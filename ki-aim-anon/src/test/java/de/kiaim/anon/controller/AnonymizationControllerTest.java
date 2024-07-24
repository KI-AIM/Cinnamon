package de.kiaim.anon.controller;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.model.data.DataSet;
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

@SpringBootTest
@AutoConfigureMockMvc
public class AnonymizationControllerTest extends AbstractAnonymizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testCreateAnonymizationTask() throws Exception {
        assertNotNull(dataSet);
        assertNotNull(kiaimAnonConfig);

        AnonymizationRequest request = new AnonymizationRequest(dataSet, kiaimAnonConfig);
        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/anonymization/task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted()) // Verifier que le status est ACCEPTED (202)
                .andReturn();

        String taskId = result.getResponse().getContentAsString();
        System.out.println("Task ID: " + taskId);

        assertNotNull(taskId);
    }

    @Test
    public void testGetTaskStatus() throws Exception {
        AnonymizationRequest request = new AnonymizationRequest(dataSet, kiaimAnonConfig);
        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult creationResult = mockMvc.perform(post("/api/anonymization/task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted())
                .andReturn();

        String taskId = creationResult.getResponse().getContentAsString();

        MvcResult statusResult = mockMvc.perform(get("/api/anonymization/task/" + taskId + "/status"))
                .andExpect(status().isOk())
                .andReturn();

        String status = statusResult.getResponse().getContentAsString();
        System.out.println("Task Status: " + status);

        assertNotNull(status);
    }

    @Test
    public void testGetTaskResult() throws Exception {
        AnonymizationRequest request = new AnonymizationRequest(dataSet, kiaimAnonConfig);
        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult creationResult = mockMvc.perform(post("/api/anonymization/task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isAccepted())
                .andReturn();

        String taskId = creationResult.getResponse().getContentAsString();

        Thread.sleep(10000);

        MvcResult result = mockMvc.perform(get("/api/anonymization/task/" + taskId + "/result"))
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
