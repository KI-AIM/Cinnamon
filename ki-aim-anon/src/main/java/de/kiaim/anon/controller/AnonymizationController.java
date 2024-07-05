package de.kiaim.anon.controller;

import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.anon.service.AnonymizationService;
import de.kiaim.model.configuration.anonymization.AnonymizationConfig;
import de.kiaim.model.data.DataSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/anonymization")
public class AnonymizationController {

    private final AnonymizationService anonymizationService;

    public AnonymizationController(final AnonymizationService anonymizationService) {
        this.anonymizationService = anonymizationService;
    }

    @Operation(summary = "Anonymizes the provided dataset based on the given configuration.",
            description = "Anonymizes the provided dataset based on the given configuration and returns the anonymized data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully anonymized the dataset.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error.", content = @Content)
    })
    @PostMapping(value = "/anonymization_tabular", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DataSet> processAnonymization(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Request containing the dataset and anonymization configuration.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AnonymizationRequest.class)),
                    required = true
            )
            @RequestBody(required = true) AnonymizationRequest request) {
        try {
            DataSet dataSet = request.getDataSet();
            AnonymizationConfig kiaimAnonConfig = request.getKiaimAnonConfig();
            DataSet result = anonymizationService.anonymizeData(dataSet, kiaimAnonConfig);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping(value = "/anonymization_tabular_config")
    public ResponseEntity<byte[]> getTabularAnonConfig() {
        try {
            Resource resource = new ClassPathResource("frontend_config/anon-tabular-config.yml");
            byte[] fileContent = FileCopyUtils.copyToByteArray(resource.getInputStream());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=anon-tabular-config.yml");

            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }
}