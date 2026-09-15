import { HttpClient } from '@angular/common/http';
import { DefaultUrlSerializer, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UserService } from "./user.service";

describe("UserService", () => {
	let service: UserService;
	let http: jasmine.SpyObj<HttpClient>;
	let router: jasmine.SpyObj<Router>;
	const urlSerializer = new DefaultUrlSerializer();

	beforeEach(() => {
		http = jasmine.createSpyObj<HttpClient>("HttpClient", ["post", "get"]);
		http.post.and.returnValue(of(undefined));

		router = jasmine.createSpyObj<Router>(
			"Router",
			["navigate", "navigateByUrl", "parseUrl", "serializeUrl"],
			{ url: "/" },
		);
		router.navigate.and.returnValue(Promise.resolve(true));
		router.navigateByUrl.and.returnValue(Promise.resolve(true));
		router.parseUrl.and.callFake(url => urlSerializer.parse(url));
		router.serializeUrl.and.callFake(tree => urlSerializer.serialize(tree));

		service = new UserService(
			http,
			{ addNotification: jasmine.createSpy('addNotification') } as any,
			router,
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

	it("should redirect to /login without a returnUrl on a manual logout", () => {
		service.logout("close");

		expect(router.navigate).toHaveBeenCalledWith(["/login"], { queryParams: {} });
	});

	it("should redirect to /login with the current url as returnUrl when the session expired", () => {
		Object.defineProperty(router, "url", { value: "/project/42", configurable: true });

		service.logout("expired");

		expect(router.navigate).toHaveBeenCalledWith(["/login"], { queryParams: { returnUrl: "/project/42" } });
	});

	describe("routeToUser$", () => {
		beforeEach(() => {
			http.get.and.returnValue(of({ username: "alice", roles: [] }));
			service.login({ username: "alice", password: "secret" }).subscribe();
		});

		it("navigates to a valid return url", () => {
			service.routeToUser$("/project/42").subscribe();

			expect(router.navigateByUrl).toHaveBeenCalledWith("/project/42");
		});

		it("falls back to home when no return url is given", () => {
			service.routeToUser$().subscribe();

			expect(router.navigate).toHaveBeenCalledWith(["/user/-/home"]);
		});

		it("falls back to home for an absolute URL (open-redirect attempt)", () => {
			service.routeToUser$("https://evil.com").subscribe();

			expect(router.navigate).toHaveBeenCalledWith(["/user/-/home"]);
		});

		it("falls back to home for a protocol-relative URL (open-redirect attempt)", () => {
			service.routeToUser$("//evil.com").subscribe();

			expect(router.navigate).toHaveBeenCalledWith(["/user/-/home"]);
		});

		it("falls back to home for '/login' to avoid a redirect loop", () => {
			service.routeToUser$("/login").subscribe();

			expect(router.navigate).toHaveBeenCalledWith(["/user/-/home"]);
		});

		it("falls back to home for '/' to avoid a pointless redirect", () => {
			service.routeToUser$("/").subscribe();

			expect(router.navigate).toHaveBeenCalledWith(["/user/-/home"]);
		});
	});
});
