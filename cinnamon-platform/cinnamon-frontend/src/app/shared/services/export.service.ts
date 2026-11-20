import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { HoldOutSelector } from "@core/enums/hold-out-selector";
import { FileType } from "@shared/model/file-configuration";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { ProjectService } from "@shared/services/project.service";
import { UserService } from "@shared/services/user.service";
import { BehaviorSubject, Observable, Subscription, switchMap, tap } from "rxjs";
import { environments } from "src/environments/environment";

/**
 * Service for exporting projects.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class ExportService {

    private exportSubscription: Subscription | null = null;
    private exportStatusSubject: BehaviorSubject<boolean> = new BehaviorSubject(false);

    public constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly http: HttpClient,
        private readonly projectService: ProjectService,
        private readonly userService: UserService,
    ) {
    }

    /**
     * Updates the current export status.
     * Emits true, if an export is started and false if finished or aborted.
     */
    public exportStatus$(): Observable<boolean> {
        return this.exportStatusSubject.asObservable();
    }

    /**
     * Downloads all files related to the project in a ZIP file.
     * @protected
     */
    public exportProject(bundleConfigurations: boolean, datasetFileType: FileType, holdOutSelector: HoldOutSelector, resources: string[]): void {
        this.exportStatusSubject.next(true);

        this.exportSubscription = this.projectService.projectIdRequiredOnce$.pipe(
            switchMap(projectId => this.http.get(`${environments.apiUrl}/api/project/${projectId}/zip`, {
                observe: 'response',
                params: {
                    bundleConfigurations: bundleConfigurations,
                    datasetFileType: datasetFileType,
                    holdOutSelector: holdOutSelector,
                    resources: resources,
                },
                responseType: 'arraybuffer'
            })),
            tap(response => {
                const contentType = response.headers.get("Content-Type");
                const contentDisposition = response.headers.get("Content-Disposition");
                let fileName = this.extractFileNameFromContentDisposition(contentDisposition);

                const blob = new Blob([response.body!], {
                    type: contentType!,
                });

                if (!fileName) {
                    const now = new Date();
                    const year = now.getFullYear().toString().padStart(4, '0');
                    const month = (now.getMonth() + 1).toString().padStart(2, '0');
                    const day = now.getDate().toString().padStart(2, '0');
                    const hours = now.getHours().toString().padStart(2, '0');
                    const minutes = now.getMinutes().toString().padStart(2, '0');
                    const seconds = now.getSeconds().toString().padStart(2, '0');
                    const localDateTime = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
                    fileName = this.userService.getUser().userInfo.username + "_" + localDateTime + "_Cinnamon.zip";
                }

                const element = document.createElement('a');
                element.href = URL.createObjectURL(blob);
                element.download = fileName;
                document.body.appendChild(element);
                element.click();
            }),
        ).subscribe({
            next: () => {
                this.exportStatusSubject.next(false);
            },
            error: error => {
                this.exportStatusSubject.next(false);
                this.errorHandlingService.addError(error, "Project export failed!");
            }
        });
    }

    /**
     * Aborts the current project export.
     */
    public abortExport(): void {
        if (this.exportSubscription == null || this.exportSubscription.closed)
            return;
        this.exportSubscription.unsubscribe();
        this.exportStatusSubject.next(false);
    }

    /**
     * Extracts the file name from the Content-Disposition header.
     * @param contentDisposition The Content-Disposition header value.
     * @returns The extracted file name, or null if extraction fails.
     */
    private extractFileNameFromContentDisposition(contentDisposition: string | null): string | null {
        if (!contentDisposition) {
            return null;
        }

        // Try to match filename*= (RFC 5987) first
        let match = contentDisposition.match(/filename\*=(?:UTF-8'')?(?:"([^"]+)"|([^;,\s]+))/i);
        if (match) {
            return decodeURIComponent(match[1] || match[2]);
        }

        // Fall back to filename= (RFC 2183)
        match = contentDisposition.match(/filename=(?:"([^"]+)"|([^;,\s]+))/i);
        if (match) {
            return match[1] || match[2];
        }

        return null;
    }

}
