package de.kiaim.cinnamon.test.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.FileConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class ControllerTest extends DatabaseTest {

	@Autowired
	protected ObjectMapper objectMapper;
	@Autowired
	protected SerializationConfig serializationConfig;

	protected ObjectMapper yamlMapper = null;
	protected ObjectMapper jsonMapper = null;

	@BeforeEach
	protected void setUpMapper() {
		if (yamlMapper == null) {
			yamlMapper = serializationConfig.yamlMapper();
		}
		if (jsonMapper == null) {
			jsonMapper = serializationConfig.jsonMapper();
		}
	}

	@Autowired
	protected MockMvc mockMvc;

	protected ResultMatcher validationError(final String key, final String expectedError) {
		final List<String> expectedErrors = new ArrayList<>();
		expectedErrors.add(expectedError);

		return  mvcResult -> {
			final String response = mvcResult.getResponse().getContentAsString();
			final ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
			final LinkedHashMap<String, List<String>> errors = (LinkedHashMap) errorResponse.getErrorDetails();
			assertEquals(1, errors.size(), "Number of errors not correct!");
			assertTrue(errors.containsKey(key), "No error for '" + key + "' present!");
			assertEquals(expectedErrors, errors.get(key), "Unexpected message!");
		};
	}

	protected ResultMatcher errorMessage(final String expectedError) {
		return  mvcResult -> {
			final String response = mvcResult.getResponse().getContentAsString();
			final ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
			assertEquals(expectedError, errorResponse.getErrorMessage() , "Unexpected message!");
		};
	}

	protected ResultMatcher errorCode(final String expectedErrorCode) {
		return  mvcResult -> {
			final String response = mvcResult.getResponse().getContentAsString();
			final ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);
			assertEquals(expectedErrorCode, errorResponse.getErrorCode() , "Unexpected error code!");
		};
	}

	protected void postData() throws Exception {
		postData(true);
	}

	protected Long postData(final boolean withErrors) throws Exception {
		postFile(withErrors);

		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		String result = mockMvc.perform(multipart("/api/data")
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		return assertDoesNotThrow(() -> Long.parseLong(result.trim()));
	}

	protected void postData(final boolean withErrors, final String user) throws Exception {
		postFile(withErrors, user);

		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		String result = mockMvc.perform(multipart("/api/data")
				                                .with(httpBasic(user, "changeme"))
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		assertDoesNotThrow(() -> Long.parseLong(result.trim()));
	}

	protected void postFile(final boolean withErrors) throws Exception {
		MockMultipartFile file;
		if (withErrors) {
			file = ResourceHelper.loadCsvFileWithErrors();
		} else {
			file = ResourceHelper.loadCsvFile();
		}

		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/file")
				                .file(file)
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.csv', type: 'CSV', numberOfAttributes: 6}"));
	}

	protected void postFile(final boolean withErrors, final String user) throws Exception {
		MockMultipartFile file;
		if (withErrors) {
			file = ResourceHelper.loadCsvFileWithErrors();
		} else {
			file = ResourceHelper.loadCsvFile();
		}

		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/file")
				                .file(file)
				                .with(httpBasic(user, "changeme"))
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.csv', type: 'CSV', numberOfAttributes: 6}"));
	}

}
