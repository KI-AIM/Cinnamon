import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { TitleService } from "../../../../core/services/title-service.service";
import { UserService } from "src/app/shared/services/user.service";

interface LoginForm {
	email: FormControl<string>;
	password: FormControl<string>;
}

@Component({
	selector: "app-login",
	templateUrl: "./login.component.html",
	styleUrls: ["./login.component.less"],
})
export class LoginComponent implements OnInit {
	loginForm: FormGroup<LoginForm>;
	mode: string;
	infoText: string;

	constructor(
		private readonly activateRoute: ActivatedRoute,
		private readonly router: Router,
		private readonly titleService: TitleService,
		private readonly userService: UserService
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
		this.titleService.setPageTitle("Login");
	}

	ngOnInit() {
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
					this.router.navigateByUrl("/");
				} else {
					this.router.navigate(["/login", { mode: "fail" }]);
				}
			}
		);
	}
}
