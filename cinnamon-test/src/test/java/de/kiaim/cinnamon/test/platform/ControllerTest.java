package de.kiaim.cinnamon.test.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.FileConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

	protected ResultMatcher validationError(final String key, final String... expectedErrors) {
		return  mvcResult -> {
			final String response = mvcResult.getResponse().getContentAsString();
			final ErrorResponse errorResponse = objectMapper.readValue(response, ErrorResponse.class);

			assertNotNull(errorResponse.getErrorDetails(), "No errors details present!");

			final Map<String, Set<String>> errors = errorResponse.getErrorDetails().getValidationErrors();

			assertNotNull(errors, "No validation errors present!");
			assertEquals(1, errors.size(), "Number of errors not correct!");
			assertTrue(errors.containsKey(key), "No error for '" + key + "' present!");
			assertEquals(Set.of(expectedErrors), errors.get(key), "Unexpected message!");
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

	protected Long postData() throws Exception {
		return postData(true);
	}

	protected Long postData(final boolean withErrors) throws Exception {
		return postData(withErrors, false);
	}

	protected void postDataAlternative() throws Exception {
		postData(false, true);
	}

	private Long postData(final boolean withErrors, final boolean alternative) throws Exception {
		postFile(withErrors, alternative);

		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration(alternative);
		String result = mockMvc.perform(multipart("/api/project/" + testProject.getExternalId() + "/data")
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		return assertDoesNotThrow(() -> Long.parseLong(result.trim()));
	}

	protected void postData(final boolean withErrors, final String user, final UUID projectId) throws Exception {
		postFile(withErrors, user, projectId);

		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		String result = mockMvc.perform(multipart("/api/project/" + projectId + "/data")
				                                .with(httpBasic(user, "changeme"))
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		assertDoesNotThrow(() -> Long.parseLong(result.trim()));
	}

	protected void postDataSource(DataSourceType type) throws Exception {
		var configuration = FileConfigurationTestHelper.generateDataSourceConfiguration(type);
		mockMvc.perform(multipart("/api/project/" + testProject.getExternalId() + "/data/file/source")
				                .param("dataSourceConfiguration", objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isOk());
	}

	protected void postFile(final boolean withErrors, final boolean alternative) throws Exception {
		MockMultipartFile file;
		if (withErrors) {
			file = ResourceHelper.loadCsvFileWithErrors();
		} else {
			if (alternative) {
				file = ResourceHelper.loadCsvFileAlternative();
			} else {
				file = ResourceHelper.loadCsvFile();
			}
		}

		mockMvc.perform(multipart("/api/project/" + testProject.getExternalId() + "/data/file")
				                .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.csv', type: null, numberOfAttributes: 0}"));

		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		mockMvc.perform(multipart("/api/project/" + testProject.getExternalId() + "/data/file/configuration")
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.csv', type: 'CSV', numberOfAttributes: 6}"));
	}

	protected void postFile(final boolean withErrors, final String user, final UUID projectId) throws Exception {
		MockMultipartFile file;
		if (withErrors) {
			file = ResourceHelper.loadCsvFileWithErrors();
		} else {
			file = ResourceHelper.loadCsvFile();
		}

		mockMvc.perform(multipart("/api/project/" + projectId + "/data/file")
				                .file(file)
				                .with(httpBasic(user, "changeme")))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.csv', type: null, fhirResourceTypes: null, numberOfAttributes: 0}"));

		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		mockMvc.perform(multipart("/api/project/" + projectId + "/data/file/configuration")
				                .with(httpBasic(user, "changeme"))
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.csv', type: 'CSV', fhirResourceTypes: null, numberOfAttributes: 6}"));
	}

	protected void postFhirFile() throws Exception {
		final MockMultipartFile file = ResourceHelper.loadFhirBundle();
		final FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.FHIR);

		mockMvc.perform(multipart("/api/project/" + testProject.getExternalId() + "/data/file")
				                .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.json', type: null, fhirResourceTypes: ['Patient', 'Observation'], numberOfAttributes: 0}"));

		mockMvc.perform(multipart("/api/project/" + testProject.getExternalId() + "/data/file/configuration")
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.json', type: 'FHIR', fhirResourceTypes: ['Patient', 'Observation'], numberOfAttributes: 13}"));
	}

	protected void estimateDataConfiguration() throws Exception {
		mockMvc.perform(get("/api/project/" + testProject.getExternalId() + "/data/estimation"))
		       .andExpect(status().isOk());
	}

	protected void createHoldOut(final float holdOutPercentage) throws Exception {
		mockMvc.perform(post("/api/project/" + testProject.getExternalId() + "/data/hold-out")
				                .param("holdOutPercentage", String.valueOf(holdOutPercentage)))
		       .andExpect(status().isOk());
	}

	protected void storeData() throws Exception {
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		mockMvc.perform(multipart("/api/project/" + testProject.getExternalId() + "/data")
				                .param("configuration",
				                       objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isOk());
	}

	protected void confirmData() throws Exception {
		mockMvc.perform(post("/api/project/" + testProject.getExternalId() + "/data/confirm"))
		       .andExpect(status().isOk());
	}

}
