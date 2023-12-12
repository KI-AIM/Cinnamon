import { Injectable } from "@angular/core";
import {
	HttpClient,
	HttpErrorResponse,
	HttpHeaders,
} from "@angular/common/http";
import { Observable, finalize, retry } from "rxjs";
import { Router } from "@angular/router";
import { User } from "../model/user";

@Injectable({
	providedIn: "root",
})
export class UserService {
	private readonly USER_KEY = "user";
	private user: User;

	constructor(
		private readonly http: HttpClient,
		private readonly router: Router
	) {
		const storedUser = sessionStorage.getItem(this.USER_KEY);
		if (storedUser !== null) {
			this.user = JSON.parse(storedUser);
		} else {
			this.user = new User(false, "", "");
		}
	}

	getUser(): User {
		return this.user;
	}

	isAuthenticated(): boolean {
		return this.user.authenticated;
	}

	login(
		credentials: { email: string; password: string },
		callback: (error: string) => void
	) {
		this.user = new User(false, "", "");

		const token = btoa(credentials.email + ":" + credentials.password);
		const headers = new HttpHeaders(
			credentials ? { authorization: "Basic " + token } : {}
		);

		this.http
			.get<any>("api/user", { headers: headers })
			.subscribe({
				next: (data: any) => {
					console.log(data);
					if (data["name"]) {
						this.user = new User(true, credentials.email, token);
						sessionStorage.setItem(
							this.USER_KEY,
							JSON.stringify(this.user)
						);
					}
					return callback && callback("");
				},
				error: (e: HttpErrorResponse) => {
					return callback && callback(e.error);
				},
			});
	}

	logout() {
		sessionStorage.removeItem(this.USER_KEY)
		this.router.navigate(['login', { mode: 'logout' }]).then(
			() => window.location.reload()
		);
	}

	register(request: {
		email: string;
		password: string;
		passwordRepeated: string;
	}): Observable<any> {
		return this.http.post("api/user/register", request);
	}
}
