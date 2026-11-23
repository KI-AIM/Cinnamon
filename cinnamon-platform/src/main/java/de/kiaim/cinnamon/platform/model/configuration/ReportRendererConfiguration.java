package de.kiaim.cinnamon.platform.model.configuration;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Configuration for rendering the report as a PDF on the server side.
 * The report page of the frontend is rendered by a headless Chromium provided by a Gotenberg instance.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class ReportRendererConfiguration {

	/**
	 * Base URL of the Gotenberg instance, e.g. {@code http://cinnamon-pdf:3000}.
	 * If not set, server side report rendering is disabled.
	 */
	@Nullable
	private String url = null;

	/**
	 * Base URL under which the Chromium inside Gotenberg can reach this platform, e.g. {@code http://cinnamon-platform:8080}.
	 * Has to be reachable from the Gotenberg container, so it is usually the internal host name, not the public one.
	 */
	@Nullable
	private String platformUrl = null;

	/**
	 * Maximum time the platform waits for Gotenberg to return the PDF.
	 * <p>
	 * The timeouts are staggered so that the most specific error wins:
	 * <ol>
	 *     <li>The report page gives up after {@link #getPageTimeout()} and reports what it was still waiting for.</li>
	 *     <li>Gotenberg's {@code --api-timeout} should lie between the page timeout and this timeout.</li>
	 *     <li>The platform gives up after this timeout.</li>
	 * </ol>
	 */
	private Duration timeout = Duration.ofSeconds(120);

	/**
	 * How much earlier than {@link #timeout} the report page gives up.
	 * Leaves time for reporting the error and for Gotenberg to respond.
	 */
	private Duration pageTimeoutMargin = Duration.ofSeconds(30);

	/**
	 * Maximum size of the generated PDF in bytes.
	 */
	private int maxPdfSize = 50 * 1024 * 1024;

	/**
	 * Returns the time after which the report page itself gives up and reports an error.
	 *
	 * @return The page timeout, at least 10 seconds.
	 */
	public Duration getPageTimeout() {
		final Duration pageTimeout = timeout.minus(pageTimeoutMargin);
		final Duration minimum = Duration.ofSeconds(10);
		return pageTimeout.compareTo(minimum) < 0 ? minimum : pageTimeout;
	}

	/**
	 * Returns if server side rendering is configured.
	 *
	 * @return True if Gotenberg and the platform URL are configured.
	 */
	public boolean isEnabled() {
		return url != null && !url.isBlank() && platformUrl != null && !platformUrl.isBlank();
	}
}
