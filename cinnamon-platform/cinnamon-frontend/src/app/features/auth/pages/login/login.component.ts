import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { StateManagementService } from "@core/services/state-management.service";
import { TitleService } from "@core/services/title-service.service";
import { LogoutMode, UserService } from "@shared/services/user.service";

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
	mode: LogoutMode | null;

	constructor(
		private readonly activateRoute: ActivatedRoute,
		private readonly router: Router,
		private readonly titleService: TitleService,
		private readonly userService: UserService,
        private readonly stateManagementService: StateManagementService,
	) {
		this.loginForm = new FormGroup<LoginForm>({
			email: new FormControl<string>("", {
				nonNullable: true,
				validators: [Validators.required],
			}),
			password: new FormControl<string>("", {
				nonNullable: true,
				validators: [Validators.required],
			}),
		});
		this.titleService.setPageTitle("Open project");
	}

	ngOnInit() {
        if (this.userService.isAuthenticated()) {
            this.stateManagementService.fetchAndRouteToCurrentStep();
        }

		this.activateRoute.params.subscribe((params) => {
			if (params["mode"]) {
				this.mode = params["mode"];
			} else {
				this.mode = null;
			}
		});
	}

	onSubmit() {
        const loginData = this.loginForm.value as { email: string; password: string };
        this.userService.login(loginData).subscribe({
            next: () => {
                this.stateManagementService.fetchAndRouteToCurrentStep();
            },
            error: () => {
                this.router.navigate(["/open", {mode: "fail"}]);
            },
        });
    }
}
