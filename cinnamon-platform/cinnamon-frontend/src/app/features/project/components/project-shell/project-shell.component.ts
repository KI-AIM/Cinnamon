import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { StepConfiguration } from "@core/enums/steps";
import { StateManagementService } from "@core/services/state-management.service";
import { TitleService } from "@core/services/title-service.service";
import { ProjectService } from "@shared/services/project.service";
import { StatusService } from "@shared/services/status.service";
import { switchMap } from "rxjs";

@Component({
    selector: 'app-project-shell',
    standalone: false,
    templateUrl: './project-shell.component.html',
})
export class ProjectShellComponent implements OnInit, OnDestroy {

    constructor(
        private readonly route: ActivatedRoute,
        private readonly projectService: ProjectService,
        private readonly stateManagementService: StateManagementService,
        private readonly statusService: StatusService,
        private readonly titleService: TitleService,
    ) {
    }

    public ngOnInit(): void {
        const projectId = this.route.snapshot.params['projectId'];
        if (projectId) {
            this.projectService.openProjectId(projectId).pipe(
                switchMap((project) => {
                    return this.statusService.updateStatus();
                }),
                switchMap(status => {
                    const title = StepConfiguration[status.currentStep].text;
                    this.titleService.setPageTitle(title);
                    return this.stateManagementService.routeToCurrentStep(projectId, status);
                }),
            ).subscribe({});
        }
    }

    public ngOnDestroy(): void {
        this.projectService.closeProject();
    }

}
