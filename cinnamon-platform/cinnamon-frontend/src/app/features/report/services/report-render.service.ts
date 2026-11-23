import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Injectable, OnDestroy } from '@angular/core';
import { NotificationService } from "@core/services/notification.service";
import { catchError, filter, firstValueFrom, Observable, Subscription, tap, throwError } from "rxjs";
import { environments } from "src/environments/environment";

/**
 * Status of the report page that Gotenberg waits for, see ReportPdfService in the backend.
 */
export type ReportRenderStatus = 'ready' | 'error';

declare global {
    interface Window {
        cinnamonReportStatus?: ReportRenderStatus;
    }
}

/**
 * Coordinates the rendering of the report by the headless browser (Gotenberg).
 * <p>
 * The page must always end in one of two states, otherwise Gotenberg waits until its timeout:
 * <ul>
 *     <li>`ready`: The report is printable.</li>
 *     <li>`error`: Rendering failed. The error has been sent to the platform beforehand,
 *     so the platform can return it instead of the PDF.</li>
 * </ul>
 * To turn a page that hangs into an error, a watchdog fails the rendering after a timeout
 * and reports which data sources have not emitted yet and which errors occurred.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class ReportRenderService implements OnDestroy {

    private active = false;
    private finished = false;
    private projectId: string | null = null;

    /**
     * Names of the data sources that have not emitted yet.
     */
    private readonly pendingSources = new Set<string>();

    /**
     * Problems that occurred during rendering, e.g. failed requests or uncaught errors.
     * They do not necessarily cause the rendering to fail, but explain why it got stuck.
     */
    private readonly problems: string[] = [];

    private watchdog: ReturnType<typeof setTimeout> | null = null;
    private readonly originalConsoleError = console.error.bind(console);
    private notificationSubscription: Subscription | null = null;

    private readonly onWindowError = (event: ErrorEvent) =>
        this.recordProblem(`Uncaught error: ${event.message}`);
    private readonly onUnhandledRejection = (event: PromiseRejectionEvent) =>
        this.recordProblem(`Unhandled promise rejection: ${this.describe(event.reason)}`);

    constructor(
        private readonly http: HttpClient,
        private readonly notificationService: NotificationService,
    ) {
    }

    public ngOnDestroy(): void {
        this.stop();
    }

    /**
     * If the report is currently rendered for the headless browser.
     */
    public get isActive(): boolean {
        return this.active;
    }

    /**
     * Starts the rendering mode.
     *
     * @param projectId The project the report is rendered for.
     * @param timeoutMs Time after which the rendering fails if the report is not ready.
     */
    public start(projectId: string, timeoutMs: number): void {
        this.active = true;
        this.projectId = projectId;

        window.addEventListener('error', this.onWindowError);
        window.addEventListener('unhandledrejection', this.onUnhandledRejection);

        // Angular's ErrorHandler only logs errors, e.g. errors in templates, so capture the log as well
        console.error = (...args: unknown[]) => {
            this.recordProblem(`Logged error: ${args.map(arg => this.describe(arg)).join(" ")}`);
            this.originalConsoleError(...args);
        };

        // Many services report failed requests only as a notification and never emit,
        // which would otherwise leave the page waiting without any hint
        this.notificationSubscription = this.notificationService.latestNotification$().pipe(
            filter(notification => notification != null && notification.type === 'failure'),
        ).subscribe(notification => this.recordProblem(`Notification: ${notification!.message}`));

        this.watchdog = setTimeout(
            () => this.fail(`The report was not ready after ${Math.round(timeoutMs / 1000)}s.`),
            timeoutMs,
        );
    }

    /**
     * Tracks a data source of the report.
     * If the rendering gets stuck, the sources that have not emitted are part of the error message.
     * Has no effect outside the rendering mode.
     *
     * @param name Name of the source used in the error message.
     * @param source The source.
     */
    public track<T>(name: string, source: Observable<T>): Observable<T> {
        if (!this.active) {
            return source;
        }

        this.pendingSources.add(name);
        return source.pipe(
            tap(() => this.pendingSources.delete(name)),
            catchError(error => {
                this.recordProblem(`Loading '${name}' failed: ${this.describe(error)}`);
                return throwError(() => error);
            }),
        );
    }

    /**
     * Runs an asynchronous step of the rendering and tracks it like a data source.
     *
     * @param name Name of the step used in the error message.
     * @param step The step.
     */
    public async step<T>(name: string, step: () => Promise<T>): Promise<T> {
        this.pendingSources.add(name);
        try {
            return await step();
        } finally {
            this.pendingSources.delete(name);
        }
    }

    /**
     * Records a problem that is included in the error message if the rendering fails.
     * @param problem Description of the problem.
     */
    public recordProblem(problem: string): void {
        if (!this.active || this.finished) {
            return;
        }

        this.originalConsoleError("[report rendering]", problem);
        this.problems.push(problem);
    }

    /**
     * Marks the report as printable.
     */
    public markReady(): void {
        if (!this.active || this.finished) {
            return;
        }

        this.finished = true;
        this.stop();
        window.cinnamonReportStatus = 'ready';
    }

    /**
     * Fails the rendering.
     * Sends the error to the platform first and only then signals the failure to the headless browser,
     * so the platform knows the reason as soon as Gotenberg returns.
     *
     * @param reason Description of the failure.
     * @param error The causing error, if any.
     */
    public async fail(reason: string, error?: unknown): Promise<void> {
        if (!this.active || this.finished) {
            return;
        }

        this.finished = true;
        this.stop();

        const message = this.buildMessage(reason, error);
        this.originalConsoleError("[report rendering]", message);

        try {
            await firstValueFrom(this.http.post(
                `${environments.apiUrl}/api/project/${this.projectId}/report/render-error`, message,
                {headers: {'Content-Type': 'text/plain'}},
            ));
        } catch (postError) {
            // Without the message, the platform would treat the printed page as the report.
            // Not signaling the failure lets Gotenberg time out instead, which the platform reports as an error.
            this.originalConsoleError("[report rendering] Failed to report the error to the platform", postError);
            return;
        }

        window.cinnamonReportStatus = 'error';
    }

    private stop(): void {
        if (this.watchdog != null) {
            clearTimeout(this.watchdog);
            this.watchdog = null;
        }

        this.notificationSubscription?.unsubscribe();
        this.notificationSubscription = null;

        window.removeEventListener('error', this.onWindowError);
        window.removeEventListener('unhandledrejection', this.onUnhandledRejection);
        console.error = this.originalConsoleError;
    }

    private buildMessage(reason: string, error?: unknown): string {
        const lines = [reason];

        if (error != null) {
            lines.push(`Cause: ${this.describe(error)}`);
        }
        if (this.pendingSources.size > 0) {
            lines.push(`Still waiting for: ${Array.from(this.pendingSources).join(", ")}`);
        }
        if (this.problems.length > 0) {
            lines.push("Problems:");
            lines.push(...this.problems.map(problem => `- ${problem}`));
        }

        return lines.join("\n");
    }

    private describe(error: unknown): string {
        if (error instanceof HttpErrorResponse) {
            const body = typeof error.error === 'string' ? error.error : JSON.stringify(error.error);
            return `HTTP ${error.status} for ${error.url}: ${body}`;
        }
        if (error instanceof Error) {
            return error.message;
        }
        return String(error);
    }
}
