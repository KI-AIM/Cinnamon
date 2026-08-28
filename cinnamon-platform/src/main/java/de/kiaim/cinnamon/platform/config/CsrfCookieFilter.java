package de.kiaim.cinnamon.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the (by default lazily rendered) {@link CsrfToken} to be resolved on every request, so that the
 * {@code XSRF-TOKEN} cookie is always (re-)written to the response. Without this, the cookie would only be
 * written once something downstream actually reads {@link CsrfToken#getToken()}, so a client that never
 * triggers that read would never receive the cookie in the first place.
 *
 * @author Daniel Preciado-Marquez
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
	                                 final FilterChain filterChain) throws ServletException, IOException {
		final CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			// Renders the token, causing the CsrfTokenRepository (the cookie) to be populated.
			csrfToken.getToken();
		}

		filterChain.doFilter(request, response);
	}
}
