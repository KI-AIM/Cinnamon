package de.kiaim.anon.controller;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.model.AnonymizationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AnonymizationControllerTest extends AbstractAnonymizationTests {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();  // Appeler la méthode setUp d'AbstractAnonymizationTests pour initialiser les variables partagées
    }

    @Test
    public void testObject() throws Exception {
        String jsonRequest = objectMapper.writeValueAsString(request);
        System.out.println("JSON Request");
        System.out.println(request.getSession_key());

    }

    @Test
    public void testCreateAnonymizationTaskWithCallbackResult() throws Exception {
        byte[] dataSetBytes = objectMapper.writeValueAsBytes(request.getData());
        byte[] frontendConfigBytes = objectMapper.writeValueAsBytes(frontendAnonConfig);

        // Create multipart file with MockMultipartFile
        MockMultipartFile dataSetFile = new MockMultipartFile(
                "data", // nom du paramètre multipart
                "data.json", // nom du fichier
                "application/json", // type de contenu
                dataSetBytes // contenu du fichier
        );

        MockMultipartFile frontendConfigFile = new MockMultipartFile(
                "anonymizationConfig", // nom du paramètre multipart
                "anonymizationConfig.json", // nom du fichier
                "application/json", // type de contenu
                frontendConfigBytes // contenu du fichier
        );

        MvcResult result = mockMvc.perform(multipart("/api/anonymization/")
                        .file(dataSetFile)
                        .file(frontendConfigFile)
                        .param("session_key", request.getSession_key())
                        .param("callback", request.getCallback()))
                .andExpect(status().isAccepted())  // Vérifier que le statut est ACCEPTED (202)
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Response content: " + responseContent);

        assertNotNull(responseContent);
        assertTrue(responseContent.contains(request.getSession_key()));
    }

//    @Test
//    public void testGetTaskStatus() throws Exception {;
//        byte[] dataSetBytes = objectMapper.writeValueAsBytes(request.getData());
//        byte[] frontendConfigBytes = objectMapper.writeValueAsBytes(request.getAnonymizationConfig());
//
//        // Create multipart file with MockMultipartFile
//        MockMultipartFile dataSetFile = new MockMultipartFile(
//                "data", // nom du paramètre multipart
//                "data.json", // nom du fichier
//                "application/json", // type de contenu
//                dataSetBytes // contenu du fichier
//        );
//
//        MockMultipartFile frontendConfigFile = new MockMultipartFile(
//                "anonymizationConfig", // nom du paramètre multipart
//                "anonymizationConfig.json", // nom du fichier
//                "application/json", // type de contenu
//                frontendConfigBytes // contenu du fichier
//        );
//
//        MvcResult result = mockMvc.perform(multipart("/api/anonymization/")
//                        .file(dataSetFile)
//                        .file(frontendConfigFile)
//                        .param("session_key", request.getSession_key())
//                        .param("callback", request.getCallback()))
//                .andExpect(status().isAccepted())  // Vérifier que le statut est ACCEPTED (202)
//                .andReturn();
//
//        MvcResult statusResult = mockMvc.perform(get("/api/anonymization/process/" + processId + "/status"))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        String status = statusResult.getResponse().getContentAsString();
//        System.out.println("Task Status: " + status);
//
//        assertNotNull(status);
//    }
//
//    @Test
//    public void testGetTaskResult() throws Exception {
//        String jsonRequest = objectMapper.writeValueAsString(request);
//
//        MvcResult creationResult = mockMvc.perform(post("/api/anonymization/process/callback/result")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(jsonRequest))
//                .andExpect(status().isAccepted())
//                .andReturn();
//
//        Thread.sleep(10000);
//
//        MvcResult result = mockMvc.perform(get("/api/anonymization/process/" + processId + "/result"))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        String responseContent = result.getResponse().getContentAsString();
////        System.out.println("Task Result: " + responseContent);
//
//        assertNotNull(responseContent);
//    }

    @Test
    public void testGetTabularAnonConfig() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/anonymization/anon-tabular-privacy-model-config"))
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
        System.out.println("Config File Content: " + fileContentAsString.substring(0, 200));
    }
}
