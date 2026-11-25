package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.platform.model.dto.LlmProfile;
import de.kiaim.cinnamon.platform.service.LlmProfileService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Platform endpoint shared by frontend features that select an LLM profile.
 */
@RestController
@RequestMapping("/api/llm-profiles")
public class LlmProfileController {

	private final LlmProfileService llmProfileService;

	public LlmProfileController(final LlmProfileService llmProfileService) {
		this.llmProfileService = llmProfileService;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public LlmProfilesResponse getProfiles() {
		return new LlmProfilesResponse(llmProfileService.getProfiles());
	}

	public record LlmProfilesResponse(List<LlmProfile> profiles) {
	}
}
