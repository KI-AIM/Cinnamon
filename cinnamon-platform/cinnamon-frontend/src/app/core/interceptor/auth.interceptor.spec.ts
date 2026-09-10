import { HttpErrorResponse, HttpHandler, HttpRequest } from "@angular/common/http";
import { throwError, of } from "rxjs";

import { AuthInterceptor } from "./auth.interceptor";
import { UserService } from "src/app/shared/services/user.service";

describe("AuthInterceptor", () => {
	let userService: jasmine.SpyObj<UserService>;
	let interceptor: AuthInterceptor;

	beforeEach(() => {
		userService = jasmine.createSpyObj<UserService>("UserService", ["isAuthenticated", "logout"]);
		interceptor = new AuthInterceptor(userService);
	});

	function next(error: unknown): HttpHandler {
		return { handle: () => throwError(() => error) } as unknown as HttpHandler;
	}

	it("logs out and redirects when an authenticated request comes back with a 401", (done) => {
		userService.isAuthenticated.and.returnValue(true);
		const request = new HttpRequest("GET", "/api/project/1");
		const error = new HttpErrorResponse({ status: 401 });

		interceptor.intercept(request, next(error)).subscribe({
			error: (err) => {
				expect(err).toBe(error);
				expect(userService.logout).toHaveBeenCalledWith("expired");
				done();
			},
		});
	});

	it("does not log out on a 401 while not authenticated (e.g. a failed login attempt)", (done) => {
		userService.isAuthenticated.and.returnValue(false);
		const request = new HttpRequest("GET", "/api/user/login");
		const error = new HttpErrorResponse({ status: 401 });

		interceptor.intercept(request, next(error)).subscribe({
			error: (err) => {
				expect(err).toBe(error);
				expect(userService.logout).not.toHaveBeenCalled();
				done();
			},
		});
	});

	it("passes through non-401 errors without logging out", (done) => {
		userService.isAuthenticated.and.returnValue(true);
		const request = new HttpRequest("GET", "/api/project/1");
		const error = new HttpErrorResponse({ status: 500 });

		interceptor.intercept(request, next(error)).subscribe({
			error: (err) => {
				expect(err).toBe(error);
				expect(userService.logout).not.toHaveBeenCalled();
				done();
			},
		});
	});

	it("passes through successful responses unchanged", (done) => {
		const request = new HttpRequest("GET", "/api/project/1");
		const successHandler = { handle: () => of("ok" as any) } as unknown as HttpHandler;

		interceptor.intercept(request, successHandler).subscribe((value) => {
			expect(value).toBe("ok" as any);
			expect(userService.logout).not.toHaveBeenCalled();
			done();
		});
	});
});
