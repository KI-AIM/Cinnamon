import { Injectable } from "@angular/core";
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpXsrfTokenExtractor } from "@angular/common/http";
import { Observable } from "rxjs";

/**
 * Header Angular's CSRF cookie value is echoed back as, matching the backend's
 * `CookieCsrfTokenRepository` default (see `SecurityConfig`).
 */
const XSRF_HEADER_NAME = "X-XSRF-TOKEN";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

@Injectable()
export class XhrInterceptor implements HttpInterceptor {
	constructor(
		private readonly xsrfTokenExtractor: HttpXsrfTokenExtractor,
	) {}

	intercept(
		request: HttpRequest<unknown>,
		next: HttpHandler
	): Observable<HttpEvent<unknown>> {
		// The user is authenticated via a session (JSESSIONID cookie, sent automatically below),
		let header = request.headers.set("X-Requested-With", "XMLHttpRequest");

		// Angular's built-in XSRF interceptor skips absolute URLs (used in dev mode, where the API is
		// served from a different origin than the Angular dev server), so the header is attached here
		// instead, unconditionally, for every state-changing request.
		if (!SAFE_METHODS.has(request.method) && !header.has(XSRF_HEADER_NAME)) {
			const xsrfToken = this.xsrfTokenExtractor.getToken();
			if (xsrfToken !== null) {
				header = header.set(XSRF_HEADER_NAME, xsrfToken);
			}
		}

		// Required so the browser sends/accepts the XSRF-TOKEN (and session) cookie cross-origin, i.e.
		// when running the Angular dev server against a separately hosted backend.
		const xhr = request.clone({ headers: header, withCredentials: true });
		return next.handle(xhr);
	}
}
