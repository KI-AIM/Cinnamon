package de.kiaim.anon.controller;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.model.AnonymizationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    public void testProcessAnonymization() throws Exception {
        assertNotNull(dataSet);
        assertNotNull(datasetAnonymizationConfig);

        AnonymizationRequest request = new AnonymizationRequest(dataSet, datasetAnonymizationConfig);
        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/anonymization/anonymization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())// Display result in console
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Response content: " + responseContent.substring(0, 3000) + "...");

        assertNotNull(responseContent);
    }
}
