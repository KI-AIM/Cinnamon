import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { TitleService } from "@core/services/title-service.service";
import { UserService } from "@shared/services/user.service";
import { switchMap } from "rxjs";

interface LoginForm {
	username: FormControl<string>;
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
	) {
		this.titleService.setPageTitle("Login");
	}

	ngOnInit() {
        this.loginForm = new FormGroup<LoginForm>({
            username: new FormControl<string>(this.userService.cachedUsernameInput ?? "", {
                nonNullable: true,
                validators: [Validators.required],
            }),
            password: new FormControl<string>(this.userService.cachedPasswordInput ?? "", {
                nonNullable: true,
                validators: [Validators.required],
            }),
        });

        // Reset the cached login inputs
        this.userService.cachedUsernameInput = null;
        this.userService.cachedPasswordInput = null;

        if (this.userService.isAuthenticated()) {
            this.userService.routeToUser$().subscribe();
        }
	}

	onSubmit() {
        const loginData = this.loginForm.value as { username: string; password: string };
        this.userService.login(loginData).pipe(
            switchMap(() => this.userService.routeToUser$())
        ).subscribe({
            error: () => {
                this.notificationService.addNotification(
                    new AppNotification("Account name or password wrong", 'failure')
                );
            },
        });
    }

    /**
     * Navigates to the register page.
     * Caches the current username and password inputs.
     */
    protected navigateToRegister() {
        this.userService.cachedUsernameInput = this.loginForm.value.username ?? null;
        this.userService.cachedPasswordInput = this.loginForm.value.password ?? null;
        this.router.navigate(["/register"]);
    }
}
