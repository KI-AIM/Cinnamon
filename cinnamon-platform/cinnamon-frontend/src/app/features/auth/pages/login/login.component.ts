import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { AppNotification, NotificationService } from "@core/services/notification.service";
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

	constructor(
        private readonly notificationService: NotificationService,
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
	}

	onSubmit() {
		this.userService.login(
			this.loginForm.value as { email: string; password: string },
			(error) => {
				if (error === "") {
                    this.stateManagementService.fetchAndRouteToCurrentStep();
				} else {
                    this.notificationService.addNotification(
                        new AppNotification("Project name or password wrong", 'failure')
                    );
				}
			}
		);
	}
}
