import { Injectable } from "@angular/core";
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from "@angular/common/http";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { UserService } from "src/app/shared/services/user.service";

/**
 * Centrally handles session expiry: whenever any HTTP request comes back with a 401,
 * the user is logged out and redirected to the login page.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
	constructor(
		private readonly userService: UserService,
	) {}

	intercept(
		request: HttpRequest<unknown>,
		next: HttpHandler
	): Observable<HttpEvent<unknown>> {
		return next.handle(request).pipe(
			catchError((error: unknown) => {
				// Guarded by isAuthenticated() so this only fires once per session expiry (logging out
				// sets it to false synchronously) and never for a failed login attempt itself, where a
				// 401 is an expected "wrong credentials" response rather than an expired session.
				if (error instanceof HttpErrorResponse && error.status === 401 && this.userService.isAuthenticated()) {
					this.userService.logout("expired");
				}
				return throwError(() => error);
			}),
		);
	}
}
