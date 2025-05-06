package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.exception.BadStepNameException;
import de.kiaim.cinnamon.platform.service.StepService;
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

	@GetMapping(value = "/stage/{stageName}",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Stage> getStage(@PathVariable final String stageName) throws BadStepNameException {
		return ResponseEntity.ok(stepService.getStageConfiguration(stageName));
	}
}
