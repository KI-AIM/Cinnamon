package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.model.dto.LlmProfile;
import de.kiaim.cinnamon.platform.service.LlmProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmProfileServiceTest {

	@Test
	void loadsOnlyCompleteProfilesInConfiguredOrder() {
		final MockEnvironment environment = new MockEnvironment()
				.withProperty("CINNAMON_LLM_PROFILE_IDS", "qwen3,incomplete,qwen3_copy")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_NAME", "BIH GPU")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_PROVIDER", "openai_compatible")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_MODEL_NAME", "Qwen/Qwen3-8B")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_BASE_URL", "http://llm:8000")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_ENDPOINT_PATH", "/v1/chat/completions")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_HEALTHCHECK_PATH", "/v1/models")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_TIMEOUT_SECONDS", "120")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_MAX_RETRIES", "3")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_VERIFY_SSL", "true")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_MAX_TOKENS", "2048")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_ENABLE_THINKING", "false")
				.withProperty("CINNAMON_LLM_PROFILE_INCOMPLETE_PROVIDER", "unsupported")
				.withProperty("CINNAMON_LLM_PROFILE_INCOMPLETE_MODEL_NAME", "unused")
				.withProperty("CINNAMON_LLM_PROFILE_INCOMPLETE_BASE_URL", "http://unused")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_NAME", "Local Ollama")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_PROVIDER", "ollama")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_MODEL_NAME", "qwen3:8b")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_BASE_URL", "http://ollama:11434")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_ENDPOINT_PATH", "/api/generate")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_HEALTHCHECK_PATH", "/api/tags")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_TIMEOUT_SECONDS", "120")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_MAX_RETRIES", "3")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_VERIFY_SSL", "true")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_MAX_TOKENS", "2048")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_ENABLE_THINKING", "false");

		final LlmProfileService service = new LlmProfileService(environment);

		assertEquals(
				List.of(
						new LlmProfile(
								"qwen3", "BIH GPU", "openai_compatible", "Qwen/Qwen3-8B",
								"http://llm:8000", "/v1/chat/completions", "/v1/models",
								120, 3, true, 2048, false
						),
						new LlmProfile(
								"qwen3_copy", "Local Ollama", "ollama", "qwen3:8b",
								"http://ollama:11434", "/api/generate", "/api/tags",
								120, 3, true, 2048, false
						)
				),
				service.getProfiles(),
				"Incomplete profiles must not be offered by the frontend."
		);
	}

}
