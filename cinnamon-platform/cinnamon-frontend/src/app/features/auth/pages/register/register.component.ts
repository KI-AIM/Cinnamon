import {Component} from '@angular/core';
import {FormControl, FormGroup, Validators} from "@angular/forms";
import {UserService} from "../../../../shared/services/user.service";
import { HttpErrorResponse } from "@angular/common/http";
import {Router} from "@angular/router";
import {TitleService} from "../../../../core/services/title-service.service";

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
export class RegisterComponent {
    registerError: string[];
    registerForm: FormGroup<RegisterForm>;

    constructor(
        private readonly router: Router,
        private readonly titleService: TitleService,
        private readonly userService: UserService,
    ) {
        this.registerError = [];
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

        this.titleService.setPageTitle("Register");
    }

    onSubmit(): void {
        const result = this.userService.register(
            this.registerForm.value as {
                email: string; password: string; passwordRepeated: string;
            }
        );
        result.subscribe({
            next: (d) => this.handleRegisterSuccess(d),
            error: (e) => this.handleRegisterFailed(e),
        });
    }

    handleRegisterSuccess(data: any) {
        this.router.navigate(['/login', { mode: 'register' }]).then(r => {
        });
    }

    handleRegisterFailed(error: HttpErrorResponse) {
        this.registerError = [];
        for (const field in error.error.errorDetails) {
            for (const fieldError of error.error.errorDetails[field]) {
                this.registerError.push(fieldError);
            }
        }
    }
}
