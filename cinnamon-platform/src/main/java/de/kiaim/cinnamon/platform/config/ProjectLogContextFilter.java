package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.BackgroundProcessEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.repository.BackgroundProcessRepository;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter for setting the project log context.
 * Uses the authenticated user or the process ID for callback requests.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class ProjectLogContextFilter extends OncePerRequestFilter {

	private static final String PROJECT_ID_KEY = "projectId";
	private static final String PROJECT_NAME_KEY = "projectName";
	private static final String CALLBACK_PATH_PREFIX = "/api/process/";
	private static final String CALLBACK_PATH_SUFFIX = "/callback";

	private final UserRepository userRepository;
	private final BackgroundProcessRepository backgroundProcessRepository;

	public ProjectLogContextFilter(final UserRepository userRepository,
	                              final BackgroundProcessRepository backgroundProcessRepository) {
		this.userRepository = userRepository;
		this.backgroundProcessRepository = backgroundProcessRepository;
	}

	@Override
	protected void doFilterInternal(final HttpServletRequest request,
	                                final HttpServletResponse response,
	                                final FilterChain chain) throws IOException, ServletException {
		setProjectLogContext(request);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(PROJECT_ID_KEY);
			MDC.remove(PROJECT_NAME_KEY);
		}
	}

	private void setProjectLogContext(final HttpServletRequest request) {
		if (setProjectLogContextFromProcessId(request)) {
			return;
		}

		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return;
		}

		final String email = extractEmail(authentication);
		if (email == null || email.isBlank()) {
			return;
		}

		final UserEntity user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			return;
		}

		populateProjectContext(user.getProject());
	}

	private boolean setProjectLogContextFromProcessId(final HttpServletRequest request) {
		final UUID processId = extractProcessIdFromCallbackPath(request.getRequestURI());
		if (processId == null) {
			return false;
		}

		final ProjectEntity project = backgroundProcessRepository.findByUuid(processId)
				.map(BackgroundProcessEntity::getProject)
				.orElse(null);
		populateProjectContext(project);
		return project != null;
	}

	private UUID extractProcessIdFromCallbackPath(@Nullable final String requestUri) {
		if (requestUri == null || !requestUri.startsWith(CALLBACK_PATH_PREFIX) || !requestUri.endsWith(CALLBACK_PATH_SUFFIX)) {
			return null;
		}

		final int startIndex = CALLBACK_PATH_PREFIX.length();
		final int endIndex = requestUri.length() - CALLBACK_PATH_SUFFIX.length();
		if (endIndex <= startIndex) {
			return null;
		}

		try {
			return UUID.fromString(requestUri.substring(startIndex, endIndex));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private String extractEmail(final Authentication authentication) {
		if (authentication.getPrincipal() instanceof UserEntity user) {
			return user.getEmail();
		}

		return authentication.getName();
	}

	private void populateProjectContext(@Nullable final ProjectEntity project) {
		if (project == null) {
			return;
		}

		if (project.getId() != null) {
			MDC.put(PROJECT_ID_KEY, String.valueOf(project.getId()));
		}

		final var projectConfiguration = project.getProjectConfiguration();
		if (projectConfiguration != null && projectConfiguration.getProjectName() != null) {
			MDC.put(PROJECT_NAME_KEY, projectConfiguration.getProjectName());
		}
	}
}
