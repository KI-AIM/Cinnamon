package de.kiaim.cinnamon.test.model.serialization.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.serialization.mapper.JsonMapper;
import de.kiaim.cinnamon.model.status.synthetization.SynthetizationStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SynthetizationStatusDeserializationTest {

	private static ObjectMapper jsonMapper;

	@BeforeAll
	static void beforeAll() {
		jsonMapper = JsonMapper.jsonMapper();
	}

	@Test
	public void deserializeExtendedSynthetizationStatus() throws JsonProcessingException {
		final String json = """
				{
				  "session_key": "session-1",
				  "synthesizer_name": "ctgan",
				  "components": {
				    "structured_synthesis": {
				      "completed": "False",
				      "duration": "12.5",
				      "fitting_duration": "10.0",
				      "fitting_remaining_time": "2",
				      "initialization_duration": "1.0",
				      "remaining_time": "1",
				      "sampling_duration": "1.5",
				      "sampling_remaining_time": "1",
				      "synthesizer_name": "ctgan",
				      "future_metric": "ignored"
				    },
				    "total_synthesis": {
				      "step": "Total",
				      "completed": "False",
				      "duration": "12.5",
				      "remaining_time": "1"
				    }
				  },
				  "status": [
				    {
				      "step": "callback",
				      "completed": "False",
				      "unexpected_field": "ignored"
				    }
				  ],
				  "unknown_root_field": "ignored"
				}
				""";

		final SynthetizationStatus status = jsonMapper.readValue(json, SynthetizationStatus.class);

		assertEquals("session-1", status.getSessionKey());
		assertEquals("ctgan", status.getSynthesizerName());
		assertEquals("2", status.getComponents().get("structured_synthesis").getFittingRemainingTime());
		assertEquals("1", status.getComponents().get("structured_synthesis").getSamplingRemainingTime());
		assertEquals("Total", status.getComponents().get("total_synthesis").getStep());
		assertEquals("1", status.getComponents().get("total_synthesis").getRemainingTime());
		assertEquals(1, status.getStatus().size());
		assertEquals("callback", status.getStatus().get(0).getStep());
	}

	@Test
	public void deserializeSynthetizationStatusWithNullCollections() throws JsonProcessingException {
		final String json = """
				{
				  "components": null,
				  "status": null
				}
				""";

		final SynthetizationStatus status = jsonMapper.readValue(json, SynthetizationStatus.class);

		assertNotNull(status.getComponents());
		assertNotNull(status.getStatus());
		assertTrue(status.getComponents().isEmpty());
		assertTrue(status.getStatus().isEmpty());
	}
}
