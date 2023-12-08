import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TitleService } from '../../services/title-service.service';
import { UserService } from 'src/app/shared/services/user.service';
import { HttpErrorResponse } from '@angular/common/http';

interface LoginForm {
  email: FormControl<string>;
  password: FormControl<string>;
}

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.less']
})
export class LoginComponent {
  loginForm: FormGroup<LoginForm>;

  constructor(
    private readonly router: Router,
    private readonly titleService: TitleService,
    private readonly userService: UserService,
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
    this.titleService.setPageTitle('Login');
  }

  onSubmit() {
    this.userService.login(this.loginForm.value as { email: string, password: string }, error => {
      if (error === '') {
        this.router.navigateByUrl("/start")
      } else {
        // TODO handle
      }
    });
  }
}
