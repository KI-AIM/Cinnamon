import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from "@angular/common/http";
import { Observable, finalize, retry } from "rxjs";
import { Router } from '@angular/router';
import { User } from '../model/user';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private user: User;

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {
    const storedUser = sessionStorage.getItem("user");
    if (storedUser !== null) {
      this.user = JSON.parse(storedUser);
    } else {
      this.user = new User(false, "", "");
    }
    console.log(this.user);
  }

  getUser(): User {
    return this.user;
  }

  isAuthenticated(): boolean {
    return this.user.authenticated;
  }

  login(credentials: { email: string, password: string }, callback: (error: string) => void) {
    this.user = new User(false, "", "");

    const token = btoa(credentials.email + ':' + credentials.password);
    const headers = new HttpHeaders(credentials
      ? { authorization: 'Basic ' + token }
      : {}
    );

    this.http.get<any>('api/user', { headers: headers }).subscribe({
      next: (data: any) => {
        console.log(data);
        if (data['name']) {
          this.user = new User(true, credentials.email, token);
          sessionStorage.setItem("user", JSON.stringify(this.user));
        }
        return callback && callback("");
      },
      error: (e: HttpErrorResponse) => {
        return callback && callback(e.error);
      }
    });
  }

  logout() {
    this.http.post('logout', {})
      .pipe(finalize(() => this.router.navigateByUrl("")))
      .subscribe();
  }

  register(request: {
    email: string, password: string, passwordRepeated: string
  }): Observable<any> {
    console.log(request);
    return this.http.post('api/user/register', request);
  }
}
