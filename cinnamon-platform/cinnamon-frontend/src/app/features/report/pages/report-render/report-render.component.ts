import { AsyncPipe } from "@angular/common";
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { StateManagementService } from "@core/services/state-management.service";
import { ReportComponent } from "@features/report/pages/report/report.component";
import { ReportRenderService } from "@features/report/services/report-render.service";
import { ProjectService } from "@shared/services/project.service";
import { StatusService } from "@shared/services/status.service";
import { catchError, map, Observable, of, switchMap, tap } from "rxjs";

/**
 * Page opened by the headless browser (Gotenberg) for rendering the report as a PDF on the server side.
 * It is not protected by the auth guards, as the headless browser has no session.
 * Instead, the browser sends a short-lived token header with every request that the backend validates.
 * The page itself does not contain any data until the backend accepts the token.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-report-render',
    imports: [
        AsyncPipe,
        ReportComponent,
    ],
    template: `
        @if (projectLoaded$ | async) {
            <app-report [renderMode]="true"></app-report>
        }
    `,
    changeDetection: ChangeDetectionStrategy.Eager,
})
export class ReportRenderComponent implements OnInit, OnDestroy {

    /**
     * Used if the platform does not pass a timeout.
     */
    private static readonly DEFAULT_TIMEOUT_MS = 60_000;

    protected projectLoaded$: Observable<boolean>;

    constructor(
        private readonly projectService: ProjectService,
        private readonly reportRenderService: ReportRenderService,
        private readonly route: ActivatedRoute,
        private readonly stateManagementService: StateManagementService,
        private readonly statusService: StatusService,
    ) {
    }

    public ngOnInit(): void {
        const projectId = this.route.snapshot.params['projectId'];
        const timeout = Number(this.route.snapshot.queryParams['timeout']);
        this.reportRenderService.start(projectId, timeout > 0 ? timeout : ReportRenderComponent.DEFAULT_TIMEOUT_MS);

        // Same initialization as the ProjectShellComponent, but without routing to the current step
        this.projectLoaded$ = this.reportRenderService.track("project", this.projectService.openProjectId(projectId)).pipe(
            switchMap(() => this.reportRenderService.track("project status", this.statusService.updateStatus())),
            tap(() => this.stateManagementService.initPipeline()),
            map(() => true),
            catchError(error => {
                this.reportRenderService.fail("Failed to open the project.", error);
                return of(false);
            }),
        );
    }

    public ngOnDestroy(): void {
        this.projectService.closeProject();
    }
}
