package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.ReportRendererConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import de.kiaim.cinnamon.platform.model.entity.ExternalProcessEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Renders the report as a PDF by letting Gotenberg print the report page of the frontend.
 * <p>
 * Flow:
 * <ol>
 *     <li>A short-lived token granting read access to the project is issued.</li>
 *     <li>Gotenberg opens {@code <platformUrl>/report-render/<projectId>} in a headless Chromium,
 *     sending the token as a header with every request to the platform.</li>
 *     <li>The page loads the report, converts the charts into images and sets {@code window.cinnamonReportStatus}.</li>
 *     <li>Gotenberg prints the page and returns the PDF.</li>
 * </ol>
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ReportPdfService {

	private static final Logger log = LoggerFactory.getLogger(ReportPdfService.class);

	/**
	 * Path of the frontend route rendering the report for the headless browser.
	 */
	private static final String RENDER_ROUTE = "/report-render/";

	/**
	 * Gotenberg route for converting a URL into a PDF.
	 */
	private static final String GOTENBERG_URL_ROUTE = "/forms/chromium/convert/url";

	/**
	 * Name of the job whose results are required for the report.
	 */
	private static final String REQUIRED_JOB = "technical_evaluation";

	private final CinnamonConfiguration cinnamonConfiguration;
	private final JsonMapper jsonMapper;
	private final ReportRenderTokenService reportRenderTokenService;

	public ReportPdfService(final CinnamonConfiguration cinnamonConfiguration,
	                        final JsonMapper jsonMapper,
	                        final ReportRenderTokenService reportRenderTokenService) {
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.jsonMapper = jsonMapper;
		this.reportRenderTokenService = reportRenderTokenService;
	}

	/**
	 * Checks if the report can be rendered for the given project.
	 * Requires a configured renderer and a finished technical evaluation.
	 *
	 * @param project The project.
	 * @return True if the report can be rendered.
	 */
	public boolean isReportAvailable(final ProjectEntity project) {
		if (!cinnamonConfiguration.getReportRenderer().isEnabled() || project.getPipelines().isEmpty()) {
			return false;
		}

		for (final ExecutionStepEntity executionStep : project.getPipelines().get(0).getStages()) {
			for (final ExternalProcessEntity process : executionStep.getProcesses()) {
				if (process.getJob().getName().equals(REQUIRED_JOB)) {
					return process.getExternalProcessStatus() == ProcessStatus.FINISHED;
				}
			}
		}

		return false;
	}

	/**
	 * Renders the report of the given project as a PDF.
	 * Blocks until the PDF has been generated.
	 *
	 * @param project The project.
	 * @param user    The user requesting the report. The report is rendered with their permissions.
	 * @return The PDF.
	 * @throws InternalRequestException If the renderer is not configured or rendering failed.
	 */
	public byte[] renderReport(final ProjectEntity project, final UserEntity user) throws InternalRequestException {
		final ReportRendererConfiguration config = cinnamonConfiguration.getReportRenderer();
		if (!config.isEnabled()) {
			throw new InternalRequestException(InternalRequestException.REPORT_RENDERING,
			                                   "Rendering the report is not configured!");
		}

		final String platformUrl = stripTrailingSlash(Objects.requireNonNull(config.getPlatformUrl()));
		final String gotenbergUrl = stripTrailingSlash(Objects.requireNonNull(config.getUrl()));
		final String projectId = project.getExternalId().toString();
		final String token = reportRenderTokenService.issue(user.getUsername(), projectId);

		try {
			final byte[] pdf;
			try {
				pdf = requestPdf(config, gotenbergUrl, platformUrl, projectId, token);
			} catch (final InternalRequestException e) {
				// An error reported by the page is more specific than the failure of Gotenberg it caused
				throw pageErrorOr(token, projectId, e);
			}

			// The page finished with an error, so the PDF does not contain the report
			final InternalRequestException pageError = pageErrorOr(token, projectId, null);
			if (pageError != null) {
				throw pageError;
			}

			if (pdf == null || pdf.length == 0) {
				throw new InternalRequestException(InternalRequestException.REPORT_RENDERING,
				                                   "Gotenberg returned an empty report!");
			}

			return pdf;
		} finally {
			reportRenderTokenService.revoke(token);
		}
	}

	/**
	 * Requests Gotenberg to print the report page.
	 *
	 * @param config       The renderer configuration.
	 * @param gotenbergUrl Base URL of Gotenberg.
	 * @param platformUrl  Base URL of the platform as seen from Gotenberg.
	 * @param projectId    The external ID of the project.
	 * @param token        The render token.
	 * @return The PDF as returned by Gotenberg.
	 * @throws InternalRequestException If Gotenberg failed or did not respond in time.
	 */
	@Nullable
	private byte[] requestPdf(final ReportRendererConfiguration config, final String gotenbergUrl,
	                          final String platformUrl, final String projectId, final String token)
			throws InternalRequestException {
		final MultipartBodyBuilder body = new MultipartBodyBuilder();
		body.part("url", platformUrl + RENDER_ROUTE + projectId + "?timeout=" + config.getPageTimeout().toMillis());
		// Set by the report page once it is printable or once it has reported an error,
		// so Gotenberg never waits until its own timeout
		body.part("waitForExpression",
		          "window.cinnamonReportStatus === 'ready' || window.cinnamonReportStatus === 'error'");
		// Only send the token to the platform, never to other hosts
		body.part("extraHttpHeaders", jsonMapper.writeValueAsString(Map.of(
				ReportRenderTokenService.HEADER_NAME,
				token + ";scope=^" + Pattern.quote(platformUrl) + ".*")));
		body.part("emulatedMediaType", "print");
		body.part("preferCssPageSize", "true");
		body.part("printBackground", "true");
		// Fail fast if the page itself or one of its resources (scripts, API requests) cannot be loaded
		body.part("failOnHttpStatusCodes", "[499,599]");
		body.part("failOnResourceHttpStatusCodes", "[499,599]");

		final WebClient webClient = WebClient.builder()
		                                     .baseUrl(gotenbergUrl)
		                                     .codecs(codecs -> codecs.defaultCodecs()
		                                                             .maxInMemorySize(config.getMaxPdfSize()))
		                                     .build();

		try {
			return webClient.post()
			                .uri(GOTENBERG_URL_ROUTE)
			                .body(BodyInserters.fromMultipartData(body.build()))
			                .retrieve()
			                .onStatus(HttpStatusCode::isError,
			                          response -> response.bodyToMono(String.class)
			                                              .defaultIfEmpty("")
			                                              .flatMap(message -> Mono.error(
					                                              new InternalRequestException(
							                                              InternalRequestException.REPORT_RENDERING,
							                                              "Gotenberg failed to render the report (HTTP " +
							                                              response.statusCode().value() + "): " +
							                                              message.strip()))))
			                .bodyToMono(byte[].class)
			                .timeout(config.getTimeout())
			                .block();
		} catch (final RuntimeException e) {
			// block() wraps checked exceptions
			final Throwable cause = Exceptions.unwrap(e);
			if (cause instanceof InternalRequestException internal) {
				throw internal;
			}
			if (cause instanceof TimeoutException) {
				throw new InternalRequestException(
						InternalRequestException.REPORT_RENDERING,
						"Gotenberg did not respond within " + config.getTimeout().toSeconds() + "s. " +
						"Gotenberg's --api-timeout must be lower than cinnamon.report-renderer.timeout.", cause);
			}
			if (cause instanceof WebClientRequestException) {
				throw new InternalRequestException(InternalRequestException.REPORT_RENDERING,
				                                   "Gotenberg is not reachable at " + gotenbergUrl + ": " +
				                                   cause.getMessage(), cause);
			}
			throw new InternalRequestException(InternalRequestException.REPORT_RENDERING,
			                                   "Failed to render the report: " + cause.getMessage(), cause);
		}
	}

	/**
	 * Returns an exception for the error reported by the report page, if there is one.
	 *
	 * @param token     The render token.
	 * @param projectId The project ID used for logging.
	 * @param fallback  Returned if the page did not report an error.
	 * @return The exception for the page error, or the fallback.
	 */
	@Nullable
	private InternalRequestException pageErrorOr(final String token, final String projectId,
	                                             @Nullable final InternalRequestException fallback) {
		final String pageError = reportRenderTokenService.getError(token);
		if (pageError == null) {
			if (fallback != null) {
				log.warn("Rendering the report of project {} failed: {}", projectId, fallback.getMessage());
			}
			return fallback;
		}

		log.warn("The report page of project {} reported an error: {}", projectId, pageError);
		return new InternalRequestException(InternalRequestException.REPORT_RENDERING,
		                                    "The report page failed to load: " + pageError, fallback);
	}

	private static String stripTrailingSlash(final String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
