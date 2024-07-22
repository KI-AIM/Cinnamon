package de.kiaim.test.platform.controller;

import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.test.platform.ControllerTest;
import de.kiaim.test.util.DataConfigurationTestHelper;
import de.kiaim.test.util.ResourceHelper;
import jakarta.servlet.ServletContext;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileItemFactory;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithUserDetails("test_user")
public class ProcessControllerTest extends ControllerTest {

	@Autowired
	ServletContext servletContext;

	public static MockWebServer mockBackEnd;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("ki-aim.steps.synthetization.url",
		             () -> String.format("http://localhost:%s", mockBackEnd.getPort()));
	}

	@BeforeAll
	static void setUp() throws IOException {
		mockBackEnd = new MockWebServer();
		mockBackEnd.start();
	}

	@AfterAll
	static void tearDown() throws IOException {
		mockBackEnd.shutdown();
	}

	@Test
	public void startAndFinishProcess() throws Exception {
		postData(false);

		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("ok")
				                    .build());

		mockMvc.perform(post("/api/process/start")
				                .param("stepName", "synthetization")
				                .param("algorithm", "ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());

		// Test state changes
		var updateTestProject = getTestProject();
		var process = updateTestProject.getExternalProcess();
		assertNotNull(process, "No external process has been created!");
		assertEquals(ProcessStatus.RUNNING, updateTestProject.getStatus().getExternalProcessStatus(),
		             "External process status has not been updated!");

		// Test request
		RecordedRequest recordedRequest = mockBackEnd.takeRequest();
		assertEquals("POST", recordedRequest.getMethod());
		assertEquals("/start_synthetization_process/ctgan", recordedRequest.getPath());

		// Transform request into ServletRequest so FileUpload can parse it
		final byte[] body = recordedRequest.getBody().readByteArray();
		final var request = new MockHttpServletRequest();
		request.setContent(body);
		request.setContentType(recordedRequest.getHeaders().get("Content-Type"));

		// Parse request
		final FileItemFactory<DiskFileItem> factory = DiskFileItemFactory.builder().get();
		final var upload = new JakartaServletFileUpload(factory);
		final List<DiskFileItem> fileItems = upload.parseRequest(request);

		final Long id = process.getId();
		final String callbackUrl = servletContext.getContextPath() + "/process/" + id.toString() + "/callback";

		// Test request content
		for (final FileItem fileItem : fileItems) {
			if (fileItem.getFieldName().equals("data")) {

				try (final var zipIn = new ZipInputStream(new ByteArrayInputStream(fileItem.get()))) {
					final String content = readNextZipEntry(zipIn);
					assertEquals(DataConfigurationTestHelper.generateDataConfigurationAsYaml(), content, "Unexpected content of first file!");

					final String contentSecond = readNextZipEntry(zipIn);
					assertEquals("configuration", contentSecond, "Unexpected content of second file!");

					final String contentThird = readNextZipEntry(zipIn);
					assertEquals(ResourceHelper.loadCsvFileAsString(), contentThird, "Unexpected content of third file!");

					final ZipEntry forth = zipIn.getNextEntry();
					assertNull(forth, "There should be only three files in the zip!");
				}

			} else if (fileItem.getFieldName().equals("session_key")) {
				// TODO fix
				// assertEquals(id.toString(), fileItem.getString(), "Unexpected session key!");
			} else if (fileItem.getFieldName().equals("callback")) {
				// TODO fix
				// assertEquals(callbackUrl, fileItem.getString(), "Unexpected callback URL!");
			} else {
				fail("Unexpected field: " + fileItem.getFieldName());
			}
		}

		mockMvc.perform(post("/api/process/" + id.toString() + "/callback"))
		       .andExpect(status().isOk());

		// Test state changes
		process = updateTestProject.getExternalProcess();
		assertNull(process, "External process has not been deleted!");
		assertEquals(ProcessStatus.FINISHED, updateTestProject.getStatus().getExternalProcessStatus(),
		             "External process status has not been updated!");
	}

	@Test
	public void startInvalidStepName() throws Exception {
		postData(false);

		mockMvc.perform(post("/api/process/start")
				                .param("stepName", "invalid")
				                .param("algorithm", "ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "The step 'invalid' is not defined!"));
	}

	@Test
	public void startNoData() throws Exception {
		mockMvc.perform(post("/api/process/start")
				                .param("stepName", "synthetization")
				                .param("algorithm", "ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "No configuration for the project with the given ID '" + getTestProject().getId() + "' found!"));
	}

	@Test
	public void startModuleUnavailable() throws Exception {
		postData(false);

		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(404)
				                    .body("ok")
				                    .build());

		mockMvc.perform(post("/api/process/start")
				                .param("stepName", "synthetization")
				                .param("algorithm", "ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isInternalServerError())
		       .andExpect(errorMessage("Failed to start the process! Got status of 404 NOT_FOUND"));
	}

	@Test
	public void finishInvalidProcess() throws Exception {
		mockMvc.perform(post("/api/process/0/callback"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("No process with the given ID '0' exists! Maybe it was canceled!"));
	}

	private String readNextZipEntry(final ZipInputStream zipIn) throws IOException {
		final StringBuilder sb = new StringBuilder();
		final byte[] buffer = new byte[1024];
		int read;

		final ZipEntry zipEntry =  zipIn.getNextEntry();
		assertNotNull(zipEntry, "Next ZIP entry does not exist!");
		while((read = zipIn.read(buffer, 0, 1024)) >= 0) {
			sb.append(new String(buffer, 0, read));
		}

		return sb.toString();
	}

}
