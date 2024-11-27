package de.kiaim.platform.controller;

import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.config.StageConfiguration;
import de.kiaim.platform.config.StepConfiguration;
import de.kiaim.platform.exception.BadStepNameException;
import de.kiaim.platform.service.StepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/step")
@Tag(name = "/api/step", description = "API for receiving information about steps.")
public class StepController {

	private final StepService stepService;

	public StepController(final StepService stepService) {
		this.stepService = stepService;
	}

	@Operation(summary = "Returns the step configuration of the step with the given name.",
	           description = "Returns the step configuration of the step with the given name.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully return the step configuration.",
			             content = @Content(schema = @Schema(implementation = StepConfiguration.class))),
			@ApiResponse(responseCode = "400",
			             description = "No step with the given name could be found.",
			             content = @Content(schema = @Schema(implementation = StepConfiguration.class))),
	})
	@GetMapping(value = "/{stepName}",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<StepConfiguration> getStep(@PathVariable final String stepName) throws BadStepNameException {
		return ResponseEntity.ok(stepService.getStepConfiguration(stepName));
	}

	@GetMapping(value = "/stage/{stageName}",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<StageConfiguration> getStage(@PathVariable final String stageName) throws BadStepNameException {
		return ResponseEntity.ok(stepService.getStageConfiguration(stageName));
	}
}
