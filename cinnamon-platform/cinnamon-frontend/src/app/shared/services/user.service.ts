import { Injectable } from "@angular/core";
import { HttpClient, HttpErrorResponse, HttpHeaders } from "@angular/common/http";
import { AppNotification, NotificationService, NotificationType } from "@core/services/notification.service";
import { Observable, Subject } from "rxjs";
import { Router } from "@angular/router";
import { User } from "../model/user";
import { environments } from "../../../environments/environment";

@Injectable({
	providedIn: "root",
})
export class UserService {
    private readonly baseURL = environments.apiUrl + "/api/user";
	private readonly USER_KEY = "user";
	private user: User;

    private logoutSubject: Subject<void> = new Subject<void>();

	constructor(
		private readonly http: HttpClient,
        private readonly notificationService: NotificationService,
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

    public logout$(): Observable<void> {
        return this.logoutSubject.asObservable();
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
			.get<any>(this.baseURL + "/login", { headers: headers })
			.subscribe({
				next: (data: any) => {
					if (typeof data === "boolean" && data) {
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

        let message = "";
        let type: NotificationType = "success";
        switch (mode) {
            case "close": message = "Successfully closed project"; break;
            case "delete": message = "Successfully deleted project"; break;
            case "expired": message = "Session expired"; type = "failure"; break;
        }

        this.logoutSubject.next();

        this.router.navigate(['open']).then(() => {
                this.notificationService.addNotification(new AppNotification(message, type));
            }
        );
    }
}

export type LogoutMode = "close" | "delete" | "expired";
