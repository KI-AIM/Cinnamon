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
	 * How long a render token is valid.
	 * Must cover the entire rendering of the report.
	 */
	private Duration tokenValidity = Duration.ofMinutes(2);

	/**
	 * Timeout for the request to Gotenberg.
	 */
	private Duration timeout = Duration.ofSeconds(90);

	/**
	 * Maximum size of the generated PDF in bytes.
	 */
	private int maxPdfSize = 50 * 1024 * 1024;

	/**
	 * Returns if server side rendering is configured.
	 *
	 * @return True if Gotenberg and the platform URL are configured.
	 */
	public boolean isEnabled() {
		return url != null && !url.isBlank() && platformUrl != null && !platformUrl.isBlank();
	}
}
