package de.kiaim.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.model.dto.ErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AutoConfigureMockMvc
public class ControllerTest extends DatabaseTest {

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected MockMvc mockMvc;

	protected void testValidationError(String response, String key, String expectedError)
			throws JsonProcessingException {
		final ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
		final LinkedHashMap<String, String> errors = (LinkedHashMap) errorResponse.getErrors();
		assertEquals(1, errors.size(), "Number of errors not correct!");
		assertTrue(errors.containsKey(key), "No error for '" + key + "' present!");
		assertEquals(expectedError, errors.get(key), "Unexpected message!");
	}

	protected void testErrorMessage(String response, String expectedError) throws JsonProcessingException {
		final ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
		final String errors = (String) errorResponse.getErrors();
		assertEquals(expectedError, errors, "Unexpected message!");
	}
}
