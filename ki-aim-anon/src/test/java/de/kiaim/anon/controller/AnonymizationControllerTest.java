package de.kiaim.anon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.anon.service.AnonymizationService;
import de.kiaim.model.configuration.anonymization.DatasetAnonymizationConfig;
import de.kiaim.model.data.DataSet;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureMockMvc
public class AnonymizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnonymizationService anonymizationService;

    private DataSet dataSet;
    private DatasetAnonymizationConfig datasetAnonymizationConfig;

    @BeforeEach
    public void setUp() throws Exception {
        // Charger le DataSet depuis le fichier JSON
        String dataSetJson = new String(Files.readAllBytes(Paths.get("path/to/your/dataset.json")));
        dataSet = objectMapper.readValue(dataSetJson, DataSet.class);

        // Charger la configuration d'anonymisation depuis le fichier JSON
        String anonConfigJson = new String(Files.readAllBytes(Paths.get("path/to/your/anonymizationConfig.json")));
        datasetAnonymizationConfig = objectMapper.readValue(anonConfigJson, DatasetAnonymizationConfig.class);
    }

    @Test
    public void testProcessAnonymization() throws Exception {
        assertNotNull(dataSet);
        assertNotNull(datasetAnonymizationConfig);

//        AnonymizationRequest request = new AnonymizationRequest(dataSet, datasetAnonymizationConfig);
//        String jsonRequest = objectMapper.writeValueAsString(request);
//
//        MvcResult result = mockMvc.perform(post("/api/anonymization/anonymization")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(jsonRequest))
//                .andExpect(status().isOk())
//                .andDo(print()) // Display result in console
//                .andReturn();
//
//        String responseContent = result.getResponse().getContentAsString();
//        System.out.println("Response content: " + responseContent);
//
//        assertNotNull(responseContent);
//        assertTrue(responseContent.contains("anonymizedData"));
    }
}
