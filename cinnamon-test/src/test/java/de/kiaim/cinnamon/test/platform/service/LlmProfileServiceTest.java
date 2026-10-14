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
				.withProperty("CINNAMON_LLM_PROFILE_IDS", "qwen3,qwen3_copy")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_NAME", "BIH GPU")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_PROVIDER", "openai_compatible")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_MODEL_NAME", "Qwen/Qwen3-8B")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_BASE_URL", "http://llm:8000")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_NAME", "Local Ollama")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_PROVIDER", "ollama")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_MODEL_NAME", "qwen3:8b")
				.withProperty("CINNAMON_LLM_PROFILE_QWEN3_COPY_BASE_URL", "http://ollama:11434");

		final LlmProfileService service = new LlmProfileService(environment);

		assertEquals(
				List.of(
						new LlmProfile("BIH GPU"),
						new LlmProfile("Local Ollama")
				),
				service.getProfiles(),
				"Incomplete profiles must not be offered by the frontend."
		);
	}
}
