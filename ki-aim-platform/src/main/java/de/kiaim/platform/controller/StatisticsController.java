package de.kiaim.platform.controller;

import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.exception.InternalIOException;
import de.kiaim.platform.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for statistics.
 * TODO currently only for initial data set
 *
 * @author Daniel Preciado-Marquez
 */
@RestController
@RequestMapping("/api/statistics")
@Tag(name = "/api/project", description = "API for managing projects.")
public class StatisticsController {

	private final StatisticsService statisticsService;

	public StatisticsController(StatisticsService statisticsService) {
		this.statisticsService = statisticsService;
	}

	@Operation(summary = "Returns the statistics as YAML.",
	           description = "Returns the statistics as YAML.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Response contains the statistics.",
			             content = @Content()),
	})
	@GetMapping(value = "", produces = CustomMediaType.APPLICATION_YAML_VALUE)
	public String getStatistics() throws InternalIOException {
		return statisticsService.getStatistics();
	}

}
