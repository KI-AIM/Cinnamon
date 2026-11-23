package de.kiaim.cinnamon.platform.model.dto;

/**
 * Public, non-secret description of an LLM profile.
 *
 * The API key is intentionally not part of this DTO. These non-secret
 * connection details may be stored in the selected text-anonymization
 * configuration, while the service keeps the corresponding API key in its
 * own secret configuration.
 */
public record LlmProfile(
		String id,
		String name,
		String provider,
		String modelName,
		String baseUrl,
		String endpointPath,
		String healthcheckPath,
		int timeoutSeconds,
		int maxRetries,
		boolean verifySsl,
		int maxTokens,
		boolean enableThinking
) {
}
