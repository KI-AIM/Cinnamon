import { HttpErrorResponse } from "@angular/common/http";
import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { Observable, tap } from "rxjs";
import { TitleService } from "src/app/core/services/title-service.service";
import { AppConfig, AppConfigService } from "src/app/shared/services/app-config.service";
import { ErrorHandlingService } from "src/app/shared/services/error-handling.service";
import { UserService } from "src/app/shared/services/user.service";

interface RegisterForm {
    username: FormControl<string>;
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
        private readonly notificationService: NotificationService,
        private readonly router: Router,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.titleService.setPageTitle("Register new account");
    }

    public ngOnInit(): void {
        this.appConfig$ = this.appConfigService.appConfig$.pipe(
            tap(appConfig => {
                this.registerForm = new FormGroup<RegisterForm>({
                    username: new FormControl<string>(this.userService.cachedUsernameInput ?? "", {
                        nonNullable: true,
                        validators: [Validators.required],
                    }),
                    password: new FormControl<string>(this.userService.cachedPasswordInput ?? "", {
                        nonNullable: true,
                        validators: [Validators.required, this.userService.passwordRequirementsValidator(appConfig.passwordRequirements)],
                    }),
                    passwordRepeated: new FormControl<string>("", {
                        nonNullable: true,
                        validators: [Validators.required],
                    }),
                }, {validators: [this.userService.passwordMatchesValidator("password", "passwordRepeated")]});

                // Reset the cached login inputs
                this.userService.cachedUsernameInput = null;
                this.userService.cachedPasswordInput = null;
            }),
        );
    }

    onSubmit(): void {
        const project = this.registerForm.controls["username"].value;

        const registerData = this.registerForm.value as { username: string; password: string; passwordRepeated: string };
        this.userService.register(registerData).subscribe({
            next: () => this.handleRegisterSuccess(project),
            error: (e) => this.handleRegisterFailed(e),
        });
    }

    handleRegisterSuccess(projectName: string) {
        const loginData = {username: this.registerForm.value.username!, password: this.registerForm.value.password!};
        this.userService.login(loginData).subscribe({
            next: () => {
                const notification = new AppNotification("Successfully registered account", 'success');
                notification.project = projectName;
                this.notificationService.addNotification(notification);

                this.userService.routeToUser$().subscribe();
            },
            error: (e) => this.handleRegisterFailed(e),
        });
    }

    handleRegisterFailed(error: HttpErrorResponse) {
        this.errorHandlingService.addError(error);
    }

    /**
     * Navigates to the login page.
     * Caches the current username and password inputs.
     */
    protected navigateToLogin() {
        this.userService.cachedUsernameInput = this.registerForm.value.username ?? null;
        this.userService.cachedPasswordInput = this.registerForm.value.password ?? null;
        this.router.navigate(["/login"]);
    }

}
