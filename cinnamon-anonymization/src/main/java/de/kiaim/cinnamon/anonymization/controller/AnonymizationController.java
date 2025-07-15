package de.kiaim.cinnamon.anonymization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.anonymization.model.AnonymizationRequest;
import de.kiaim.cinnamon.anonymization.service.AnonymizationService;
import de.kiaim.cinnamon.anonymization.service.ReportService;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendAnonConfigWrapper;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendAttributeConfig;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendModelConfig;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.model.dto.ModuleReportContent;
import de.kiaim.cinnamon.model.serialization.mapper.JsonMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/anonymization")
public class AnonymizationController {

    private final AnonymizationService anonymizationService;
    private final ReportService reportService;

    private final Map<String, Future<DataSet>> tasks = new ConcurrentHashMap<>();
    private final ObjectMapper jsonMapper;

    public AnonymizationController(final AnonymizationService anonymizationService, final ReportService reportService) {
        this.anonymizationService = anonymizationService;
	    this.reportService = reportService;
	    this.jsonMapper = JsonMapper.jsonMapper();
    }

    @Operation(summary = "Creates a new anonymization task.",
            description = "Creates a new asynchronous anonymization task based on the processId, dataset, and anonymization configuration. " +
                    "The anonymized dataset is returned via the given callback URL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Task accepted for processing.", content = @Content),
            @ApiResponse(responseCode = "409", description = "Task with the given process ID already exists.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error.", content = @Content)
    })
    @PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExternalProcessResponse> createAnonymizationTaskWithCallbackResult(
            @RequestParam("session_key") @Parameter(description = "The process ID for the anonymization task.", required = true) String session_key,
            @RequestPart("data") @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The dataset to be anonymized.",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = DataSet.class)),
                    required = true) MultipartFile data,
            @RequestPart("anonymizationConfig") @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The frontend anonymization configuration.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FrontendAnonConfigWrapper.class)),
                    required = true) FrontendAnonConfigWrapper anonymizationConfig,
            @RequestParam("callback") @Parameter(description = "The callback URL to return the result.", required = true) String callback) {

        try {
            System.out.println("Request in controller.");
            System.out.println("Process ID: " + session_key);

            DataSet dataset = jsonMapper.readValue(data.getInputStream(), DataSet.class);

            if (tasks.containsKey(session_key)) {
                Future<DataSet> existingTask = tasks.get(session_key);
                existingTask.cancel(true);
                System.out.println("Replaced old task with session_key: " + session_key);
            }

            // Create AnonymizationRequest object from request
            AnonymizationRequest request = new AnonymizationRequest(session_key, dataset, anonymizationConfig.getAnonymization(), callback);

            // Run anonymization service asynchronously
            Future<DataSet> future = anonymizationService.anonymizeDataWithCallbackResult(request);

            tasks.put(session_key, future);
            ExternalProcessResponse response = new ExternalProcessResponse();
            response.setMessage("Anonymization process " + session_key + " has been accepted.");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response
                    );
        } catch (Exception e) {
            ExternalProcessResponse response = new ExternalProcessResponse();
            response.setMessage("An error occurred: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(summary = "Gets the status of the anonymization task.",
            description = "Returns the current status of the anonymization task with the given process ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully.", content = @Content),
            @ApiResponse(responseCode = "404", description = "Task not found.", content = @Content)
    })
    @GetMapping("/process/{processId}/status")
    public ResponseEntity<String> getTaskStatus(@PathVariable @NonNull String processId) {
        Future<DataSet> future = tasks.get(processId);
        if (future == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } else if (future.isDone()) {
            return ResponseEntity.status(HttpStatus.OK).body("Anonymization is finished");
        } else {
            return ResponseEntity.status(HttpStatus.OK).body("Anonymization is running...");
        }
    }

    @Operation(summary = "Gets the result of the anonymization task.",
            description = "Returns the result of the anonymization task when it has finished.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Result retrieved successfully.", content = @Content),
            @ApiResponse(responseCode = "404", description = "Task not found.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error retrieving task result.", content = @Content)
    })
    @GetMapping("/process/{processId}/result")
    public ResponseEntity<DataSet> getTaskResult(@PathVariable @NonNull String processId) {
        Future<DataSet> future = tasks.get(processId);
        if (future == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } else if (future.isDone()) {
            try {
                DataSet result = future.get();
                tasks.remove(processId);
                return ResponseEntity.ok(result);
            } catch (InterruptedException | ExecutionException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } else {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        }
    }

    @Operation(summary = "Get the privacy model tabular anon configuration file for the frontend.",
            description = "Returns the tabular anon privacy model config file needed for frontend.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File retrieved successfully.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error retrieving config file.", content = @Content)
    })
    @GetMapping(value = "/anon-tabular-privacy-model-config")
    @Cacheable("config")
    public ResponseEntity<byte[]> getTabularAnonPrivacyModelConfig() {
        try {
            Resource resource = new ClassPathResource("frontend_config/anon-tabular-privacy-model-config.yml");
            byte[] fileContent = FileCopyUtils.copyToByteArray(resource.getInputStream());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=anon-tabular-privacy-model-config.yml");

            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Get the anon tabular attributes configuration file for the frontend.",
            description = "Returns the tabular anon attribute config file needed for frontend.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File retrieved successfully.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error retrieving config file.", content = @Content)
    })
    @GetMapping(value = "/anon-tabular-attribute-config")
    @Cacheable("config")
    public ResponseEntity<byte[]> getAnonAttributeConfig() {
        try {
            Resource resource = new ClassPathResource("frontend_config/anon-tabular-attribute-config.yml");
            byte[] fileContent = FileCopyUtils.copyToByteArray(resource.getInputStream());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=anon-tabular-attribute-config.yml");

            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    @Operation(summary = "Get the anonymization algorithms available.",
            description = "Returns a YML file with available anonymization algorithms.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File retrieved successfully.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error retrieving config file.", content = @Content)
    })
    @GetMapping(value = "/algorithms")
    @Cacheable("algorithms")
    public ResponseEntity<byte[]> getAlgorithms() {
        try {
            Resource resource = new ClassPathResource("frontend_config/anon-algorithms.yml");
            byte[] fileContent = FileCopyUtils.copyToByteArray(resource.getInputStream());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=anon-algorithms.yml");

            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Cancels an ongoing anonymization task.",
            description = "Cancels an ongoing anonymization task with the given process ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Task cancelled successfully.", content = @Content),
            @ApiResponse(responseCode = "404", description = "Task not found.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error cancelling the task.", content = @Content)
    })
    @DeleteMapping("/task/{processId}/cancel")
    public ResponseEntity<String> cancelTask(@PathVariable @NonNull String processId) {
        Future<DataSet> future = tasks.get(processId);
        if (future == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task " + processId + " not found");
        } else {
            future.cancel(true);
            tasks.remove(processId);
            return ResponseEntity.ok("Task " + processId + " has been cancelled successfully");
        }
    }

    @Operation(summary = "Generates the anonymization report content for the given anonymization configuration.",
            description = "Generates the anonymization report content for the given anonymization configuration.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report content generated successfully.",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(implementation = ModuleReportContent.class))),
    })
    @PostMapping(value = "/report",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ModuleReportContent getReportContent(
            @RequestPart("configuration") final FrontendAnonConfigWrapper configuration
    ) {
        return reportService.getReportContent(configuration);
    }
}
