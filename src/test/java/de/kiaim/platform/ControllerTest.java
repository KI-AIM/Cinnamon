package de.kiaim.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.model.dto.ErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureMockMvc
public class ControllerTest extends DatabaseTest {

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected MockMvc mockMvc;

	protected ResultMatcher validationError(final String key, final String expectedError) {
		final List<String> expectedErrors = new ArrayList<>();
		expectedErrors.add(expectedError);

		return  mvcResult -> {
			final String response = mvcResult.getResponse().getContentAsString();
			final ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
			final LinkedHashMap<String, List<String>> errors = (LinkedHashMap) errorResponse.getErrors();
			assertEquals(1, errors.size(), "Number of errors not correct!");
			assertTrue(errors.containsKey(key), "No error for '" + key + "' present!");
			assertEquals(expectedErrors, errors.get(key), "Unexpected message!");
		};
	}

	protected ResultMatcher errorMessage(final String expectedError) {
		return  mvcResult -> {
			final String response = mvcResult.getResponse().getContentAsString();
			final ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
			final String errors = (String) errorResponse.getErrors();
			assertEquals(expectedError, errors, "Unexpected message!");
		};
	}
}
