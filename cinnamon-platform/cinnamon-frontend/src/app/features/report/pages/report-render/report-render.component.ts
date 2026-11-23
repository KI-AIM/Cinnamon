import { AsyncPipe, NgIf } from "@angular/common";
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { StateManagementService } from "@core/services/state-management.service";
import { ReportComponent } from "@features/report/pages/report/report.component";
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
        NgIf,
        ReportComponent,
    ],
    template: `<app-report *ngIf="projectLoaded$ | async" [renderMode]="true"></app-report>`,
    changeDetection: ChangeDetectionStrategy.Eager,
})
export class ReportRenderComponent implements OnInit, OnDestroy {

    protected projectLoaded$: Observable<boolean>;

    constructor(
        private readonly projectService: ProjectService,
        private readonly route: ActivatedRoute,
        private readonly stateManagementService: StateManagementService,
        private readonly statusService: StatusService,
    ) {
    }

    public ngOnInit(): void {
        const projectId = this.route.snapshot.params['projectId'];

        // Same initialization as the ProjectShellComponent, but without routing to the current step
        this.projectLoaded$ = this.projectService.openProjectId(projectId).pipe(
            switchMap(() => this.statusService.updateStatus()),
            tap(() => this.stateManagementService.initPipeline()),
            map(() => true),
            catchError(error => {
                console.error("Failed to open the project for rendering the report", error);
                (window as any).cinnamonReportStatus = 'error';
                return of(false);
            }),
        );
    }

    public ngOnDestroy(): void {
        this.projectService.closeProject();
    }
}
