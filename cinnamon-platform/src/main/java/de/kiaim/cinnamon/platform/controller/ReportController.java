package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.dto.ModuleReportContent;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.exception.InternalMissingHandlingException;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.ReportService;
import de.kiaim.cinnamon.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for functionalities regarding the report.
 *
 * @author Daniel Preciado-Marquez
 */
@RestController()
@RequestMapping("/api/report")
@Tag(name = "/api/report", description = "API for creating the report.")
public class ReportController {

	private final ProjectService projectService;
	private final ReportService reportService;
	private final UserService userService;

	public ReportController(final ProjectService projectService, final ReportService reportService,
	                        final UserService userService) {
		this.projectService = projectService;
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
	public Map<String, ModuleReportContent> getReportData(
			@AuthenticationPrincipal final UserEntity requestUser
			) throws InternalIOException, InternalMissingHandlingException,InternalRequestException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		return reportService.fetchReportData(project);
	}
}
