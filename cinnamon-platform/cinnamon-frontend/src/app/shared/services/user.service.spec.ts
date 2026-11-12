import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UserService } from "./user.service";

describe("UserService", () => {
	let service: UserService;
	let http: jasmine.SpyObj<HttpClient>;

	beforeEach(() => {
		http = jasmine.createSpyObj<HttpClient>("HttpClient", ["post"]);
		http.post.and.returnValue(of(undefined));

		service = new UserService(
			http,
			{ addNotification: jasmine.createSpy('addNotification') } as any,
			{ navigate: jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true)) } as unknown as Router,
		);
	});

	it("should be created", () => {
		expect(service).toBeTruthy();
	});

	it("should tell the backend to end the session on logout", () => {
		service.logout("close");

		expect(http.post).toHaveBeenCalledWith(jasmine.stringMatching(/\/api\/user\/logout$/), null);
		expect(service.isAuthenticated()).toBeFalse();
	});

	it("should still log out locally if the backend logout call fails", () => {
		http.post.and.returnValue(throwError(() => new Error("network error")));

		expect(() => service.logout("close")).not.toThrow();
		expect(service.isAuthenticated()).toBeFalse();
	});
});
