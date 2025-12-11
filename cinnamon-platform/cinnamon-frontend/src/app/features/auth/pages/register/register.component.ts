import { HttpErrorResponse } from "@angular/common/http";
import { Component, OnInit, TemplateRef } from "@angular/core";
import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { Observable, tap } from "rxjs";
import { TitleService } from "src/app/core/services/title-service.service";
import { AppConfig, AppConfigService, PasswordRequirements } from "src/app/shared/services/app-config.service";
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
        private readonly notificationService: NotificationService,
        private readonly router: Router,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.titleService.setPageTitle("Create new project");
    }

    public ngOnInit(): void {
        this.appConfig$ = this.appConfigService.appConfig$.pipe(
            tap(appConfig => {
                this.registerForm = new FormGroup<RegisterForm>({
                    email: new FormControl<string>("", {
                        nonNullable: true,
                        validators: [Validators.required],
                    }),
                    password: new FormControl<string>("", {
                        nonNullable: true,
                        validators: [this.passwordRequirementsValidator(appConfig.passwordRequirements)],
                    }),
                    passwordRepeated: new FormControl<string>("", {
                        nonNullable: true,
                        validators: [Validators.required],
                    }),
                });
            }),
        );
    }

    /**
     * Opens the dialog contained in the given template.
     * @param ref Reference to the template element.
     * @protected
     */
    protected openDialog(ref: TemplateRef<any>) {
        this.matDialog.open(ref);
    }

    /**
     * Creates the error message for the password field.
     * @protected
     */
    protected createPasswordErrorMessage(): string | null {
        const control = this.registerForm.controls["password"] as FormControl;
        if (control.errors == null) {
            return null;
        }

        const errors: string[] = [];

        if (control.hasError('length')) {
            errors.push(`be at least ${control.getError('length').minLength} characters long`);
        }
        if (control.hasError('digit')) {
            errors.push('contain at lest one digit')
        }
        if (control.hasError('lowercase')) {
            errors.push(`contain at least one lowercase character`);
        }
        if (control.hasError('uppercase')) {
            errors.push(`contain at least one uppercase character`);
        }
        if (control.hasError('specialChar')) {
            errors.push(`contain at least one special character`);
        }

        return "Password must " + errors.join(", ");
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
        this.router.navigate(['/open']).then(_ => {
            this.notificationService.addNotification(new AppNotification("Successfully created project", 'success'));
        });
    }

    handleRegisterFailed(error: HttpErrorResponse) {
        this.errorHandlingService.addError(error);
    }

    /**
     * Creates a password validator for the given password requirements.
     * @param passwordRequirements The password requirements.
     * @private
     */
    private passwordRequirementsValidator(passwordRequirements: PasswordRequirements): ValidatorFn {
        return (control: AbstractControl): ValidationErrors | null => {
            if (typeof control.value !== "string") {
                return null;
            }

            let hasLength = control.value.length >= passwordRequirements.minLength

            const constraints = passwordRequirements.constraints;
            let hasLowercase = !constraints.includes('LOWERCASE');
            let hasDigit = !constraints.includes('DIGIT');
            let hasSpecialChar = !constraints.includes('SPECIAL_CHAR');
            let hasUppercase = !constraints.includes('UPPERCASE');

            for (let i = 0; i < control.value.length; i++) {
                const c = control.value.charAt(i);

                if (/\p{N}/u.test(c)) {
                    hasDigit = true;
                } else if (/\p{Ll}/u.test(c)) {
                    hasLowercase = true;
                } else if (/\p{Lu}/u.test(c)) {
                    hasUppercase = true;
                } else {
                    hasSpecialChar = true;
                }
            }

            const v: Record<string, any> = {};
            if (!hasLength) {
                v['length'] = {minLength: passwordRequirements.minLength};
            }
            if (!hasDigit) {
                v['digit'] = {};
            }
            if (!hasLowercase) {
                v['lowercase'] = {};
            }
            if (!hasUppercase) {
                v['uppercase'] = {};
            }
            if (!hasSpecialChar) {
                v['specialChar'] = {};
            }

            return hasLength && hasLowercase && hasDigit && hasSpecialChar && hasUppercase ? null : v;
        }
    }

}
