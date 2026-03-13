import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { StateManagementService } from "@core/services/state-management.service";
import { TitleService } from "@core/services/title-service.service";
import { UserService } from "@shared/services/user.service";

interface LoginForm {
	email: FormControl<string>;
	password: FormControl<string>;
}

@Component({
    selector: "app-login",
    templateUrl: "./login.component.html",
    styleUrls: ["./login.component.less"],
    standalone: false
})
export class LoginComponent implements OnInit {
	loginForm: FormGroup<LoginForm>;

    /**
     * If the password should be hidden by dots.
     */
    protected hidePassword: boolean = true;

	constructor(
        private readonly notificationService: NotificationService,
        private readonly router: Router,
		private readonly titleService: TitleService,
		private readonly userService: UserService,
        private readonly stateManagementService: StateManagementService,
	) {
		this.titleService.setPageTitle("Open project");
	}

	ngOnInit() {
        this.loginForm = new FormGroup<LoginForm>({
            email: new FormControl<string>(this.userService.cachedEmailInput ?? "", {
                nonNullable: true,
                validators: [Validators.required],
            }),
            password: new FormControl<string>(this.userService.cachedPasswordInput ?? "", {
                nonNullable: true,
                validators: [Validators.required],
            }),
        });

        // Reset the cached login inputs
        this.userService.cachedEmailInput = null;
        this.userService.cachedPasswordInput = null;


        if (this.userService.isAuthenticated()) {
            this.stateManagementService.fetchAndRouteToCurrentStep();
        }
	}

	onSubmit() {
        const loginData = this.loginForm.value as { email: string; password: string };
        this.userService.login(loginData).subscribe({
            next: () => {
                this.stateManagementService.fetchAndRouteToCurrentStep();
            },
            error: () => {
                this.notificationService.addNotification(
                    new AppNotification("Project name or password wrong", 'failure')
                );
            },
        });
    }

    /**
     * Navigates to the register page.
     * Caches the current email and password inputs.
     */
    protected navigateToRegister() {
        this.userService.cachedEmailInput = this.loginForm.value.email ?? null;
        this.userService.cachedPasswordInput = this.loginForm.value.password ?? null;
        this.router.navigate(["/create"]);
    }
}
