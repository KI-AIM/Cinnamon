package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.dto.ModuleReportContent;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.exception.BadStateException;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.ReportPdfService;
import de.kiaim.cinnamon.platform.service.ReportService;
import de.kiaim.cinnamon.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import org.jspecify.annotations.Nullable;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for functionalities regarding the report.
 *
 * @author Daniel Preciado-Marquez
 */
@RestController()
@RequestMapping("/api/project/{projectId}/report")
@Tag(name = "/api/report", description = "API for creating the report.")
public class ReportController {

	private final ProjectService projectService;
	private final ReportPdfService reportPdfService;
	private final ReportService reportService;
	private final UserService userService;

	public ReportController(final ProjectService projectService, final ReportPdfService reportPdfService,
	                        final ReportService reportService, final UserService userService) {
		this.projectService = projectService;
		this.reportPdfService = reportPdfService;
		this.reportService = reportService;
		this.userService = userService;
	}

	@Operation(summary = "Returns the report content for all jobs.",
	           description = "Returns the report content for all jobs.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Report content generated successfully.",
			             content = @Content(
					             mediaType = MediaType.APPLICATION_JSON_VALUE,
					             schema = @Schema(implementation = Map.class),
					             additionalPropertiesSchema = @Schema(implementation = ModuleReportContent.class))),
	})
	@GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, @Nullable ModuleReportContent> getReportData(
			@PathVariable final String projectId,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByUsername(requestUser.getUsername());
		final ProjectEntity project = projectService.getProject(user, projectId);

		return reportService.fetchReportData(project);
	}

	@Operation(summary = "Renders the report as a PDF.",
	           description = "Renders the report as a PDF on the server side. " +
	                         "Requires a configured report renderer and a finished technical evaluation. " +
	                         "Rendering can take several seconds.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "The PDF of the report.",
			             content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
	})
	@GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> getReportPdf(
			@PathVariable final String projectId,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByUsername(requestUser.getUsername());
		final ProjectEntity project = projectService.getProject(user, projectId);

		if (!reportPdfService.isReportAvailable(project)) {
			throw new BadStateException(BadStateException.REPORT_NOT_AVAILABLE,
			                            "The report is not available for this project!");
		}

		final byte[] pdf = reportPdfService.renderReport(project, user);
		return ResponseEntity.ok()
		                     .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
		                     .contentType(MediaType.APPLICATION_PDF)
		                     .body(pdf);
	}
}
