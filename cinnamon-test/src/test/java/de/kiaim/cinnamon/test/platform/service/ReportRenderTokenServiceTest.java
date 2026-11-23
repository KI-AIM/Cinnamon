package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.service.ReportRenderTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link ReportRenderTokenService}.
 * Does not require a Spring context.
 *
 * @author Daniel Preciado-Marquez
 */
public class ReportRenderTokenServiceTest {

	private MutableClock clock;
	private ReportRenderTokenService service;

	@BeforeEach
	public void setUp() {
		clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
		final CinnamonConfiguration configuration = new CinnamonConfiguration();
		configuration.getReportRenderer().setTimeout(Duration.ofSeconds(60));
		service = new ReportRenderTokenService(clock, configuration);
	}

	@Test
	public void resolvesIssuedToken() {
		final String token = service.issue("user", "project");

		final var grant = service.resolve(token);
		assertNotNull(grant, "Issued token should be valid!");
		assertEquals("user", grant.username());
		assertEquals("project", grant.projectId());
	}

	@Test
	public void issuesUniqueTokens() {
		assertNotEquals(service.issue("user", "project"), service.issue("user", "project"));
	}

	@Test
	public void rejectsUnknownToken() {
		assertNull(service.resolve("unknown"));
	}

	@Test
	public void tokenOutlivesTimeoutButExpiresAfterwards() {
		final String token = service.issue("user", "project");

		clock.advance(Duration.ofSeconds(61));
		assertNotNull(service.resolve(token), "Token must be valid for the entire rendering timeout!");

		clock.advance(Duration.ofMinutes(5));
		assertNull(service.resolve(token), "Token should expire after the timeout and grace period!");
	}

	@Test
	public void rejectsRevokedToken() {
		final String token = service.issue("user", "project");
		service.revoke(token);
		assertNull(service.resolve(token));
	}

	@Test
	public void keepsFirstReportedError() {
		final String token = service.issue("user", "project");

		service.recordError(token, "first");
		service.recordError(token, "second");

		assertEquals("first", service.getError(token));
	}

	@Test
	public void ignoresErrorsOfInvalidTokens() {
		service.recordError("unknown", "error");
		assertNull(service.getError("unknown"));
	}

	@Test
	public void truncatesLongErrors() {
		final String token = service.issue("user", "project");
		service.recordError(token, "x".repeat(10_000));

		final String error = service.getError(token);
		assertNotNull(error);
		assertTrue(error.length() < 5_000, "Error should be truncated!");
	}

	@Test
	public void revokeRemovesError() {
		final String token = service.issue("user", "project");
		service.recordError(token, "error");
		service.revoke(token);
		assertNull(service.getError(token));
	}

	/**
	 * Clock that can be advanced manually.
	 */
	private static class MutableClock extends Clock {
		private Instant instant;

		MutableClock(final Instant instant) {
			this.instant = instant;
		}

		void advance(final Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(final ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
