package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.WithMockWebServer;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockWebServer
@WithUserDetails("test_user")
public class StatisticsControllerTest extends ControllerTest {

	private MockWebServer mockBackEnd;

	@Test
	public void getStatisticsNoData() throws Exception {
		mockMvc.perform(get("/api/statistics")
				                .param("selector", "original"))
		       .andExpect(status().isBadRequest());
	}

	@Test
	public void getStatistics() throws Exception {
		// Preparation
		postData(false);

		// First request fetching statistics from endpoint
		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("123");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		mockMvc.perform(get("/api/statistics")
				                .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{status: 'RUNNING', statistics: null}"));

		assertEquals(1, mockBackEnd.getRequestCount());
		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("POST", recordedRequest.getMethod());
		assertEquals("/calculate_descriptive_statistics", recordedRequest.getPath());

		// Finish
		var updateTestProject = getTestProject();
		var process = updateTestProject.getOriginalData().getDataSet().getStatisticsProcess();
		String id = process.getUuid().toString();

		final MockMultipartFile resultAdditional = new MockMultipartFile("metrics.json", "metrics.json",
		                                                                 MediaType.TEXT_PLAIN_VALUE,
		                                                                 "statistics".getBytes());

		mockMvc.perform(multipart("/api/process/" + id + "/callback")
				                .file(resultAdditional))
		       .andExpect(status().isOk());

		// Second request fetching statistics from database
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(404)
				                    .body("error")
				                    .build());

		mockMvc.perform(get("/api/statistics")
				                .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{status: 'FINISHED', statistics: 'statistics'}"));

		assertEquals(1, mockBackEnd.getRequestCount(), "No request should have been made!");
	}
}
