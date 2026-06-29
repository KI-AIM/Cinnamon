import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { StateManagementService } from "@core/services/state-management.service";
import { ProjectService } from "@shared/services/project.service";
import { StatusService } from "@shared/services/status.service";

@Component({
    selector: 'app-project-shell',
    standalone: false,
    templateUrl: './project-shell.component.html',
    styleUrl: './project-shell.component.less'
})
export class ProjectShellComponent implements OnInit {

    constructor(
        private readonly route: ActivatedRoute,
        private readonly projectService: ProjectService,
        private readonly stateManagementService: StateManagementService,
        private readonly statusService: StatusService,
    ) {
    }

    public ngOnInit(): void {
        const projectId = this.route.snapshot.params['projectId'];
        console.log("Project ID: " + projectId);
        if (projectId) {
            this.projectService.openProjectId(projectId).subscribe({
                    next: (project) => {
                    },
                }
            );
        }

        this.stateManagementService.fetchAndRouteToCurrentStep();
    }

}
