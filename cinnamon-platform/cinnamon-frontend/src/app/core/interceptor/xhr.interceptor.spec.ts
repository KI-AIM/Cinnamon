import { TestBed } from "@angular/core/testing";
import { HttpHandler, HttpRequest, HttpXsrfTokenExtractor } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { RouterTestingModule } from "@angular/router/testing";
import { of } from "rxjs";

import { XhrInterceptor } from "./xhr.interceptor";

describe("XhrInterceptor", () => {
	let xsrfTokenExtractor: jasmine.SpyObj<HttpXsrfTokenExtractor>;

	beforeEach(() => {
		xsrfTokenExtractor = jasmine.createSpyObj<HttpXsrfTokenExtractor>("HttpXsrfTokenExtractor", ["getToken"]);

		TestBed.configureTestingModule({
			imports: [HttpClientTestingModule, RouterTestingModule],
			providers: [
				XhrInterceptor,
				{ provide: HttpXsrfTokenExtractor, useValue: xsrfTokenExtractor },
			],
		});
	});

	it("should be created", () => {
		const interceptor: XhrInterceptor = TestBed.inject(XhrInterceptor);
		expect(interceptor).toBeTruthy();
	});

	it("should attach the X-XSRF-TOKEN header on state-changing requests when a token is available", () => {
		xsrfTokenExtractor.getToken.and.returnValue("the-token");
		const interceptor: XhrInterceptor = TestBed.inject(XhrInterceptor);
		const request = new HttpRequest("POST", "/api/user/register", {});
		const next: HttpHandler = { handle: (req: HttpRequest<unknown>) => of(req) } as unknown as HttpHandler;

		spyOn(next, "handle").and.callThrough();
		interceptor.intercept(request, next).subscribe();

		const forwarded = (next.handle as jasmine.Spy).calls.mostRecent().args[0] as HttpRequest<unknown>;
		expect(forwarded.headers.get("X-XSRF-TOKEN")).toBe("the-token");
		expect(forwarded.withCredentials).toBeTrue();
	});

	it("should not attach the X-XSRF-TOKEN header on safe requests", () => {
		xsrfTokenExtractor.getToken.and.returnValue("the-token");
		const interceptor: XhrInterceptor = TestBed.inject(XhrInterceptor);
		const request = new HttpRequest("GET", "/api/user/login");
		const next: HttpHandler = { handle: (req: HttpRequest<unknown>) => of(req) } as unknown as HttpHandler;

		spyOn(next, "handle").and.callThrough();
		interceptor.intercept(request, next).subscribe();

		const forwarded = (next.handle as jasmine.Spy).calls.mostRecent().args[0] as HttpRequest<unknown>;
		expect(forwarded.headers.has("X-XSRF-TOKEN")).toBeFalse();
	});

	afterEach(() => {
		TestBed.inject(HttpTestingController).verify();
	});
});
