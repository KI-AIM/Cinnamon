package de.kiaim.platform.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.ContextRequiredTest;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DataControllerTest extends ContextRequiredTest {

	@Autowired
	DataController controller;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void estimateDatatypes() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", "data.csv", null,
		                                               classLoader.getResourceAsStream("test.csv"));

		final String result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/datatypes").file(file))
		                             .andExpect(status().isOk())
		                             .andReturn().getResponse().getContentAsString();

		final DataConfiguration dataConfiguration = objectMapper.readValue(result, DataConfiguration.class);

		final DataConfiguration expectedConfiguration = new DataConfiguration();
		final List<ColumnConfiguration> columnConfigurations = List.of(
				new ColumnConfiguration(0, "", DataType.BOOLEAN, new ArrayList<>()),
				new ColumnConfiguration(1, "", DataType.DATE, new ArrayList<>()),
				new ColumnConfiguration(2, "", DataType.DATE_TIME, new ArrayList<>()),
				new ColumnConfiguration(3, "", DataType.DECIMAL, new ArrayList<>()),
				new ColumnConfiguration(4, "", DataType.INTEGER, new ArrayList<>()),
				new ColumnConfiguration(5, "", DataType.STRING, new ArrayList<>()));
		expectedConfiguration.setConfigurations(columnConfigurations);

		assertEquals(expectedConfiguration, dataConfiguration);
	}

	@Test
	void estimateDatatypesMissingFile() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/datatypes"))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing file"));
	}

	@Test
	void estimateDatatypesMissingFileName() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", null, null,
		                                               classLoader.getResourceAsStream("test.csv"));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/datatypes").file(file))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing filename"));
	}

	@Test
	void estimateDatatypesMissingFileExtension() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", "file", null,
		                                               classLoader.getResourceAsStream("test.csv"));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/datatypes").file(file))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing file extension"));
	}

}
