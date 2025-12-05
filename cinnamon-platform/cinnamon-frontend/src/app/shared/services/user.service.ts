import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Router } from "@angular/router";
import { Observable, tap } from "rxjs";
import { environments } from "src/environments/environment";
import { User } from "../model/user";

@Injectable({
	providedIn: "root",
})
export class UserService {
    private readonly baseURL = environments.apiUrl + "/api/user";
	private readonly USER_KEY = "user";
	private user: User;

	constructor(
		private readonly http: HttpClient,
		private readonly router: Router,
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
		credentials: { email: string; password: string }
	): Observable<any> {
		this.user = new User(false, "", "");

		const token = btoa(credentials.email + ":" + credentials.password);
		const headers = new HttpHeaders(
			credentials ? { authorization: "Basic " + token } : {}
		);

        return this.http.get<any>(this.baseURL + "/login", {headers: headers}).pipe(
            tap(data => {
                if (typeof data === "boolean" && data) {
                    this.user = new User(true, credentials.email, token);
                    sessionStorage.setItem(this.USER_KEY, JSON.stringify(this.user));
                }
            }),
        );
	}


	register(request: {
		email: string;
		password: string;
		passwordRepeated: string;
	}): Observable<any> {
		return this.http.post(this.baseURL + "/register", request);
	}

    /**
     * Deletes the currently authenticated user.
     * @param email The email of the user.
     * @param password The password of the user.
     */
    public delete(email: string, password: string): Observable<void> {
        const formData = new FormData();
        formData.append("email", email);
        formData.append("password", password);

        return this.http.delete<void>(this.baseURL + "/delete", {body: formData});
    }

    /**
     * Logs out the user, redirects to the login page and displays a message based on the given mode.
     * @param mode The mode defining the displayed message.
     */
    public logout(mode: LogoutMode) {
        sessionStorage.removeItem(this.USER_KEY)
        this.user = new User(false, "", "");
        this.router.navigate(['open', { mode: mode }]).then(
            () => window.location.reload()
        );
    }
}

export type LogoutMode = "close" | "create" | "delete" | "expired" | "fail" | "noPermission";
