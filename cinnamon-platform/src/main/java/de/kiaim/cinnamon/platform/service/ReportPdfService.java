package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.ReportRendererConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import de.kiaim.cinnamon.platform.model.entity.ExternalProcessEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;
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
			final MultipartBodyBuilder body = new MultipartBodyBuilder();
			body.part("url", platformUrl + RENDER_ROUTE + projectId);
			// Set by ReportComponent once all data is loaded and charts are converted into images
			body.part("waitForExpression", "window.cinnamonReportStatus === 'ready'");
			// Only send the token to the platform, never to other hosts
			body.part("extraHttpHeaders", jsonMapper.writeValueAsString(Map.of(
					ReportRenderTokenService.HEADER_NAME,
					token + ";scope=^" + Pattern.quote(platformUrl) + ".*")));
			body.part("emulatedMediaType", "print");
			body.part("preferCssPageSize", "true");
			body.part("printBackground", "true");
			body.part("failOnHttpStatusCodes", "[499,599]");
			body.part("failOnResourceHttpStatusCodes", "[499,599]");

			final WebClient webClient = WebClient.builder()
			                                     .baseUrl(gotenbergUrl)
			                                     .codecs(codecs -> codecs.defaultCodecs()
			                                                             .maxInMemorySize(config.getMaxPdfSize()))
			                                     .build();

			final byte[] pdf = webClient.post()
			                            .uri(GOTENBERG_URL_ROUTE)
			                            .body(BodyInserters.fromMultipartData(body.build()))
			                            .retrieve()
			                            .onStatus(HttpStatusCode::isError,
			                                      response -> response.bodyToMono(String.class)
			                                                          .defaultIfEmpty("")
			                                                          .flatMap(message -> Mono.error(
					                                                          new InternalRequestException(
							                                                          InternalRequestException.REPORT_RENDERING,
							                                                          "Gotenberg failed to render the report (" +
							                                                          response.statusCode().value() +
							                                                          "): " + message))))
			                            .bodyToMono(byte[].class)
			                            .block(config.getTimeout());

			if (pdf == null || pdf.length == 0) {
				throw new InternalRequestException(InternalRequestException.REPORT_RENDERING,
				                                   "Gotenberg returned an empty report!");
			}

			return pdf;
		} catch (final RuntimeException e) {
			// block() wraps checked exceptions, and throws an IllegalStateException on timeout
			final Throwable cause = Exceptions.unwrap(e);
			if (cause instanceof InternalRequestException internal) {
				throw internal;
			}
			throw new InternalRequestException(InternalRequestException.REPORT_RENDERING,
			                                   "Failed to render the report: " + cause.getMessage(), cause);
		} finally {
			reportRenderTokenService.revoke(token);
		}
	}

	private static String stripTrailingSlash(final String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
