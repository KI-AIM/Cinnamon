import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { TitleService } from "@core/services/title-service.service";
import { Project } from "@shared/model/project";
import { UserService } from "@shared/services/user.service";
import { combineLatest, Observable, switchMap } from "rxjs";

@Component({
    selector: 'app-user-home-page',
    standalone: false,
    templateUrl: './user-home-page.component.html',
    styleUrl: './user-home-page.component.less'
})
export class UserHomePageComponent implements OnInit {

    protected pageData$: Observable<{
        projects: Project[],
    }>

    protected createProjectForm: FormGroup;

    constructor(
        protected readonly dialog: MatDialog,
        private readonly formBuilder: FormBuilder,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.titleService.setPageTitle("User");
    }

    public ngOnInit(): void {
        this.pageData$ = combineLatest({
            projects: this.userService.getProjectsForCurrentUser$(),
        });

        this.createProjectForm = this.formBuilder.group({
            name: [null, Validators.required]
        });
    }

    protected onCreateProject() {
        this.userService.createProjectForCurrentUser(this.createProjectForm.value.name).pipe(
            switchMap(_ => {
                return this.userService.refreshProjectsForCurrentUser$();
            }),
        ).subscribe({
            next: _ => {
                this.createProjectForm.reset();
            },
        });
    }
}
