package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.ReportRenderTokenService;
import de.kiaim.cinnamon.platform.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Authenticates requests of the headless browser that renders the report.
 * Requests carrying the {@link ReportRenderTokenService#HEADER_NAME} header are authenticated as the owner of the
 * project the token was issued for, but are restricted to read-only requests of the endpoints the report page uses,
 * and to reporting rendering errors.
 * The authentication is only stored for the current request and never persisted into a session.
 * <p>
 * Intentionally not a {@code @Component}, so it is not registered as a servlet filter by Spring Boot,
 * but only inside the security filter chain.
 *
 * @author Daniel Preciado-Marquez
 */
public class ReportRenderTokenFilter extends OncePerRequestFilter {

	private static final Set<String> ALLOWED_METHODS = Set.of("GET", "HEAD");

	/**
	 * Path relative to the project for reporting errors of the report page.
	 */
	private static final String RENDER_ERROR_PATH = "/report/render-error";

	private final ReportRenderTokenService reportRenderTokenService;
	private final UserService userService;

	public ReportRenderTokenFilter(final ReportRenderTokenService reportRenderTokenService,
	                               final UserService userService) {
		this.reportRenderTokenService = reportRenderTokenService;
		this.userService = userService;
	}

	@Override
	protected void doFilterInternal(final HttpServletRequest request,
	                                final HttpServletResponse response,
	                                final FilterChain chain) throws IOException, ServletException {
		final String token = request.getHeader(ReportRenderTokenService.HEADER_NAME);
		final String path = request.getRequestURI().substring(request.getContextPath().length());

		// Static resources of the frontend do not require authentication
		if (token == null || !path.startsWith("/api/")) {
			chain.doFilter(request, response);
			return;
		}

		final ReportRenderTokenService.RenderGrant grant = reportRenderTokenService.resolve(token);
		if (grant == null) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		if (!isAllowedRequest(request.getMethod(), path, grant.projectId())) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		final UserEntity user = userService.getUserByUsername(grant.username());
		if (user == null) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		// Only grant ROLE_USER, independent of the actual roles of the user (e.g. admin or API-only users)
		final var authentication = new UsernamePasswordAuthenticationToken(
				user, null, List.of(new SimpleGrantedAuthority(UserRole.ROLE_USER.name())));
		final SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		try {
			chain.doFilter(request, response);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	/**
	 * Checks if the report page is allowed to make the given request.
	 * Allows reading the project and the step definitions, and reporting rendering errors.
	 *
	 * @param method    The HTTP method.
	 * @param path      The request path without the context path.
	 * @param projectId The project the token was issued for.
	 * @return True if the request is allowed.
	 */
	public static boolean isAllowedRequest(final String method, final String path, final String projectId) {
		final String projectPath = "/api/project/" + projectId;

		if (method.equals("POST")) {
			return path.equals(projectPath + RENDER_ERROR_PATH);
		}

		return ALLOWED_METHODS.contains(method) &&
		       (path.equals(projectPath) || path.startsWith(projectPath + "/") || path.startsWith("/api/step/"));
	}
}
