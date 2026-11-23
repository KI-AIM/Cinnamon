package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues short-lived tokens that allow the headless browser rendering the report to read a single project.
 * Tokens are kept in memory, as they only have to live for the duration of one rendering.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ReportRenderTokenService {

	/**
	 * The header used by the headless browser to transmit the token.
	 */
	public static final String HEADER_NAME = "X-Cinnamon-Report-Token";

	private static final int TOKEN_BYTES = 32;

	private final Clock clock;
	private final CinnamonConfiguration cinnamonConfiguration;
	private final SecureRandom secureRandom = new SecureRandom();
	private final Map<String, RenderGrant> grants = new ConcurrentHashMap<>();

	public ReportRenderTokenService(final Clock clock, final CinnamonConfiguration cinnamonConfiguration) {
		this.clock = clock;
		this.cinnamonConfiguration = cinnamonConfiguration;
	}

	/**
	 * Grant created for one rendering.
	 *
	 * @param username  The owner of the project.
	 * @param projectId The external ID of the project that can be read.
	 * @param expiresAt Point in time after which the token is invalid.
	 */
	public record RenderGrant(String username, String projectId, Instant expiresAt) {
	}

	/**
	 * Creates a new token granting read access to the given project.
	 * The token must be revoked with {@link #revoke(String)} once the rendering is done.
	 *
	 * @param username  The owner of the project.
	 * @param projectId The external ID of the project.
	 * @return The token.
	 */
	public String issue(final String username, final String projectId) {
		removeExpired();

		final byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		final Instant expiresAt = clock.instant().plus(cinnamonConfiguration.getReportRenderer().getTokenValidity());
		grants.put(token, new RenderGrant(username, projectId, expiresAt));
		return token;
	}

	/**
	 * Resolves the given token.
	 *
	 * @param token The token.
	 * @return The grant, or null if the token is unknown or expired.
	 */
	@Nullable
	public RenderGrant resolve(final String token) {
		final RenderGrant grant = grants.get(token);
		if (grant == null) {
			return null;
		}

		if (clock.instant().isAfter(grant.expiresAt())) {
			grants.remove(token);
			return null;
		}

		return grant;
	}

	/**
	 * Invalidates the given token.
	 *
	 * @param token The token.
	 */
	public void revoke(final String token) {
		grants.remove(token);
	}

	private void removeExpired() {
		final Instant now = clock.instant();
		grants.values().removeIf(grant -> now.isAfter(grant.expiresAt()));
	}
}
