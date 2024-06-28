package de.kiaim.anon.controller;

import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.model.configuration.anonymization.DatasetAnonymizationConfig;
import de.kiaim.anon.service.AnonymizationService;
import de.kiaim.model.data.DataSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @PostMapping(value = "/anonymization", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String[][]> processAnonymization(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Request containing the dataset and anonymization configuration.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AnonymizationRequest.class)),
                    required = true
            )
            @RequestBody(required = true) AnonymizationRequest request) {
        try {
            DataSet dataSet = request.getDataSet();
            DatasetAnonymizationConfig datasetAnonymizationConfig = request.getDatasetAnonymizationConfig();
            String[][] results = anonymizationService.anonymizeData(dataSet, datasetAnonymizationConfig);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}