package de.kiaim.cinnamon.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.TextExtractionService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api")
public class TextExtractionController {

	private final CinnamonConfiguration cinnamonConfiguration;
	private final ProjectService projectService;
	private final TextExtractionService textExtractionService;
	private final UserService userService;

	public TextExtractionController(final CinnamonConfiguration cinnamonConfiguration,
	                                final ProjectService projectService,
	                                final TextExtractionService textExtractionService,
	                                final UserService userService) {
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.projectService = projectService;
		this.textExtractionService = textExtractionService;
		this.userService = userService;
	}

	@GetMapping("/text-extraction/profiles")
	public JsonNode getProfiles() {
		return client().get()
		               .uri("/profiles")
		               .retrieve()
		               .bodyToMono(JsonNode.class)
		               .block();
	}

	@PostMapping("/text-extraction/configuration")
	public JsonNode configure(@RequestBody final JsonNode configuration) {
		return client().post()
		               .uri("/configuration")
		               .bodyValue(configuration)
		               .retrieve()
		               .bodyToMono(JsonNode.class)
		               .block();
	}

	@PostMapping("/project/{projectId}/text-extraction/extract")
	public JsonNode extract(
			@PathVariable final String projectId,
			@RequestBody final JsonNode configuration,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByUsername(requestUser.getUsername());
		final ProjectEntity project = projectService.getProject(user, projectId);
		return textExtractionService.start(project, configuration);
	}

	@GetMapping("/project/{projectId}/text-extraction")
	public JsonNode state(
			@PathVariable final String projectId,
			@RequestParam(defaultValue = "true") final boolean wide,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByUsername(requestUser.getUsername());
		return textExtractionService.state(projectService.getProject(user, projectId), wide);
	}

	@PostMapping("/project/{projectId}/text-extraction/apply")
	public DataConfiguration apply(
			@PathVariable final String projectId,
			@RequestBody(required = false) final JsonNode options,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByUsername(requestUser.getUsername());
		final boolean wide = options == null || options.path("wide").asBoolean(true);
		return textExtractionService.apply(projectService.getProject(user, projectId), wide);
	}

	private WebClient client() {
		final var host = cinnamonConfiguration.getExternalHost().get("text-extraction-host");
		if (host == null || host.getUrl() == null) {
			throw new IllegalStateException("Text extraction host is not configured.");
		}
		return WebClient.create(host.getUrl());
	}
}
