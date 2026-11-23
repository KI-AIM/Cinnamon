package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.controller.ApiExceptionHandler;
import de.kiaim.cinnamon.platform.model.dto.LlmProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

/**
 * Reads the shared {@code CINNAMON_LLM_PROFILE_*} configuration.
 *
 * The platform is the single source for profile choices and non-secret
 * connection details. API keys remain local to the service that executes the
 * request.
 */
@Service
public class LlmProfileService {

	private static final String PROFILE_IDS = "CINNAMON_LLM_PROFILE_IDS";
	private static final String PROFILE_PREFIX = "CINNAMON_LLM_PROFILE_";

	private final Logger LOGGER = LoggerFactory.getLogger(LlmProfileService.class);

	private final Environment environment;

	public LlmProfileService(final Environment environment) {
		this.environment = environment;
	}

	/**
	 * Returns the configured profiles in the order declared by
	 * {@code CINNAMON_LLM_PROFILE_IDS}.
	 */
	public List<LlmProfile> getProfiles() {
		Stream<String> profiles = Arrays.stream(environment.getProperty(PROFILE_IDS, "").split(","));
		return profiles
				.map(this::readProfile)
				.flatMap(Optional::stream)
				.toList();
	}

	private Optional<LlmProfile> readProfile(final String profileId) {
		final String token = profileToken(profileId);
		final String provider = value(token, "PROVIDER").toLowerCase(Locale.ROOT);
		final String modelName = value(token, "MODEL_NAME");
		final String baseUrl = value(token, "BASE_URL");
		if (!("ollama".equals(provider) || "openai_compatible".equals(provider))
				|| modelName.isEmpty() || baseUrl.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new LlmProfile(
					profileId,
					value(token, "NAME"),
					provider,
					modelName,
					baseUrl,
					value(token, "ENDPOINT_PATH"),
					value(token, "HEALTHCHECK_PATH"),
					integerValue(token, "TIMEOUT_SECONDS"),
					integerValue(token, "MAX_RETRIES"),
					booleanValue(token, "VERIFY_SSL"),
					integerValue(token, "MAX_TOKENS"),
					booleanValue(token, "ENABLE_THINKING")
			));
		} catch (Exception e) {
			LOGGER.warn("Error reading profile id {}", profileId, e);
			return Optional.empty();
		}
	}

	private String value(final String token, final String property) {
		return environment.getProperty(profileVariable(token, property)).trim()
				.replaceAll("^([\"'])|([\"'])$", "");
	}

	private int integerValue(final String token, final String property) {
		return Integer.parseInt(value(token, property));
	}

	private boolean booleanValue(final String token, final String property) {
		return Boolean.parseBoolean(value(token, property));
	}

	private String profileVariable(final String token, final String property) {
		return PROFILE_PREFIX + token + "_" + property;
	}

	private String profileToken(final String profileId) {
		return profileId.trim().replaceAll("[^A-Za-z0-9]+", "_")
				.replaceAll("^_+|_+$", "").toUpperCase(Locale.ROOT);
	}
}
