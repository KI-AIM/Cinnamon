package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.DataSetSource;
import de.kiaim.cinnamon.platform.model.dto.StatisticsResponse;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.StatisticsService;
import de.kiaim.cinnamon.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for statistics.
 * TODO currently only for initial data set
 *
 * @author Daniel Preciado-Marquez
 */
@RestController
@RequestMapping("/api/statistics")
@Tag(name = "/api/statistics", description = "API for managing statistics.")
public class StatisticsController {

	private final ProjectService projectService;
	private final StatisticsService statisticsService;
	private final UserService userService;

	public StatisticsController(final ProjectService projectService, final StatisticsService statisticsService,
	                            final UserService userService) {
		this.projectService = projectService;
		this.statisticsService = statisticsService;
		this.userService = userService;
	}

	@Operation(summary = "Returns the statistics as YAML.",
	           description = "Returns the statistics as YAML.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Response contains the statistics.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = StatisticsResponse.class))),
	})
	@GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
	public StatisticsResponse getStatistics(
			@ParameterObject @Valid final DataSetSource dataSetSource,
			@Schema(allowableValues = {"cancel"},
			        description = "If the value is 'cancel', the process calculating the statistics will be cancelled.")
			@RequestParam(name = "action", required = false) final String action,
			@AuthenticationPrincipal final UserEntity requestUser
	)
			throws InternalIOException, InternalDataSetPersistenceException, InternalRequestException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException, BadDataSetIdException, BadStepNameException, InternalApplicationConfigurationException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity projectEntity =  projectService.getProject(user);

		if ("cancel".equals(action)) {
			return statisticsService.cancelStatistics(projectEntity, dataSetSource);
		}

		return statisticsService.getStatistics(projectEntity, dataSetSource);
	}

}
