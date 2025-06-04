import { HttpErrorResponse } from "@angular/common/http";
import { Component, OnInit, TemplateRef } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { Observable } from "rxjs";
import { TitleService } from "src/app/core/services/title-service.service";
import { AppConfig, AppConfigService } from "src/app/shared/services/app-config.service";
import { ErrorHandlingService } from "src/app/shared/services/error-handling.service";
import { UserService } from "src/app/shared/services/user.service";

interface RegisterForm {
    email: FormControl<string>;
    password: FormControl<string>;
    passwordRepeated: FormControl<string>;
}

@Component({
    selector: 'app-register',
    templateUrl: './register.component.html',
    styleUrls: ['./register.component.less'],
    standalone: false
})
export class RegisterComponent implements OnInit {
    registerForm: FormGroup<RegisterForm>;

    protected appConfig$: Observable<AppConfig>;

    constructor(
        private readonly appConfigService: AppConfigService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly matDialog: MatDialog,
        private readonly router: Router,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.registerForm = new FormGroup<RegisterForm>({
            email: new FormControl<string>("", {
                nonNullable: true,
                validators: [Validators.required],
            }),
            password: new FormControl<string>("", {
                nonNullable: true,
                validators: [Validators.required],
            }),
            passwordRepeated: new FormControl<string>("", {
                nonNullable: true,
                validators: [Validators.required],
            }),
        });

        this.titleService.setPageTitle("Create new project");
    }


    public ngOnInit(): void {
        this.appConfig$ = this.appConfigService.appConfig$;
    }

    /**
     * Opens the dialog contained in the given template.
     * @param ref Reference to the template element.
     * @protected
     */
    protected openDialog(ref: TemplateRef<any>) {
        this.matDialog.open(ref);
    }

    onSubmit(): void {
        const result = this.userService.register(
            this.registerForm.value as {
                email: string; password: string; passwordRepeated: string;
            }
        );
        result.subscribe({
            next: () => this.handleRegisterSuccess(),
            error: (e) => this.handleRegisterFailed(e),
        });
    }

    handleRegisterSuccess() {
        this.router.navigate(['/open', { mode: 'create' }]).then(_ => {
        });
    }

    handleRegisterFailed(error: HttpErrorResponse) {
        this.errorHandlingService.addError(error);
    }
}
