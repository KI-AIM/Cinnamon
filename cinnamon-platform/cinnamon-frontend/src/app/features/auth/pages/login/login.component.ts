import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { TitleService } from "../../../../core/services/title-service.service";
import { UserService } from "src/app/shared/services/user.service";
import { StateManagementService } from "../../../../core/services/state-management.service";

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
	mode: string;

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
				this.mode = "";
			}
		});
	}

	onSubmit() {
		this.userService.login(
			this.loginForm.value as { email: string; password: string },
			(error) => {
				if (error === "") {
                    this.stateManagementService.fetchAndRouteToCurrentStep();
				} else {
					this.router.navigate(["/open", { mode: "fail" }]);
				}
			}
		);
	}
}
