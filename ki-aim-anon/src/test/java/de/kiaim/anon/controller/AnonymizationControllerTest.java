package de.kiaim.anon.controller;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.model.data.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest
@AutoConfigureMockMvc
public class AnonymizationControllerTest extends AbstractAnonymizationTests {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    public void setControllerTestVariables() throws Exception {

        assertNotNull(dataSet);
        assertNotNull(kiaimAnonConfig);
    }

    @Test
    public void testCreateAnonymizationTask() throws Exception {
        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/anonymization/process/callback/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted()) // Verifier que le status est ACCEPTED (202)
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Response content: " + responseContent);

        Thread.sleep(1000);

        assertNotNull(responseContent);
    }

    @Test
    public void testCreateAnonymizationTaskProcessIdCallback() throws Exception {

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/anonymization/process/callback/processId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted()) // Ensure status is 202 Accepted
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Task ID: " + responseContent);

        assertNotNull(responseContent);
    }

    @Test
    public void testCompleteProcess() throws Exception {

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/anonymization/process/callback/processId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted()) // Ensure status is 202 Accepted
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Task ID: " + responseContent);

        assertNotNull(responseContent);
    }

    @Test
    public void testCreateAnonymizationTaskConflict() throws Exception {
        AnonymizationRequest request = new AnonymizationRequest(
                processId, dataSet, kiaimAnonConfig, processId);
        String jsonRequest = objectMapper.writeValueAsString(request);

        // First request should be accepted
        mockMvc.perform(post("/api/anonymization/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted());

        // Second request with the same processId should return conflict
        MvcResult result = mockMvc.perform(post("/api/anonymization/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isConflict())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Response content: " + responseContent);

        assertNotNull(responseContent);
        assertTrue(responseContent.contains("Task with process ID " + processId + " already exists."));
    }

    @Test
    public void testGetTaskStatus() throws Exception {;
        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult creationResult = mockMvc.perform(post("/api/anonymization/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult statusResult = mockMvc.perform(get("/api/anonymization/process/" + processId + "/status"))
                .andExpect(status().isOk())
                .andReturn();

        String status = statusResult.getResponse().getContentAsString();
        System.out.println("Task Status: " + status);

        assertNotNull(status);
    }

    @Test
    public void testGetTaskResult() throws Exception {
        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult creationResult = mockMvc.perform(post("/api/anonymization/process/callback/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted())
                .andReturn();

        Thread.sleep(10000);

        MvcResult result = mockMvc.perform(get("/api/anonymization/process/" + processId + "/result"))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Task Result: " + responseContent);

        assertNotNull(responseContent);
    }

    @Test
    public void testGetTabularAnonConfig() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/anonymization/config"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] fileContent = result.getResponse().getContentAsByteArray();
        assertNotNull(fileContent);

        // Optionally, check file content
        String fileContentAsString = new String(fileContent);
        System.out.println("Config File Content: " + fileContentAsString.substring(0, 300) + "...");
    }

    @Test
    public void testGetAnonAlgorithms() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/anonymization/algorithms"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] fileContent = result.getResponse().getContentAsByteArray();
        assertNotNull(fileContent);

        // Optionally, check file content
        String fileContentAsString = new String(fileContent);
        System.out.println("Config File Content: " + fileContentAsString);
    }

//    @Test
//    public void testProcessAnonymization() throws Exception {
//        assertNotNull(dataSet);
//        assertNotNull(kiaimAnonConfig);
//
//        AnonymizationRequest request = new AnonymizationRequest(dataSet, kiaimAnonConfig);
//        String jsonRequest = objectMapper.writeValueAsString(request);
//
//        MvcResult result = mockMvc.perform(post("/api/anonymization/anonymization_tabular")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(jsonRequest))
//                .andExpect(status().isOk())// Display result in console
//                .andReturn();
//
//        String responseContent = result.getResponse().getContentAsString();
//        System.out.println("Response content: " + responseContent.substring(0, 3000) + "...");
//
//        assertNotNull(responseContent);
//    }
}
