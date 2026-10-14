package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.model.dto.LlmProfile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads the shared {@code CINNAMON_LLM_PROFILE_*} configuration.
 *
 * The platform is the single frontend-facing source for profile choices.
 * The Python services still read the same environment variables when they
 * execute a request.
 */
@Service
public class LlmProfileService {

	private static final String PROFILE_IDS = "CINNAMON_LLM_PROFILE_IDS";
	private static final String PROFILE_PREFIX = "CINNAMON_LLM_PROFILE_";

	private final Environment environment;

	public LlmProfileService(final Environment environment) {
		this.environment = environment;
	}

	/**
	 * Returns the configured profiles in the order declared by
	 * {@code CINNAMON_LLM_PROFILE_IDS}.
	 */
	public List<LlmProfile> getProfiles() {
		List<String> profiles = List.of(environment.getProperty(PROFILE_IDS, "").split(","));
		return profiles.stream()
				.map(profileId -> new LlmProfile(environment.getProperty(profileVariable(profileId.toUpperCase(), "NAME"))))
				.toList();
	}

	private String profileVariable(final String token, final String property) {
		return PROFILE_PREFIX + token + "_" + property;
	}
}
