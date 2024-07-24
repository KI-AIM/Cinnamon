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
import org.springframework.cache.annotation.Cacheable;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/api/anonymization")
public class AnonymizationController {

    private final AnonymizationService anonymizationService;
    private final Map<String, Future<DataSet>> tasks = new ConcurrentHashMap<>();

    public AnonymizationController(final AnonymizationService anonymizationService) {
        this.anonymizationService = anonymizationService;
    }

//    @Operation(summary = "Anonymizes the provided dataset based on the given configuration.",
//            description = "Anonymizes the provided dataset based on the given configuration and returns the anonymized data.")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Successfully anonymized the dataset.", content = @Content),
//            @ApiResponse(responseCode = "500", description = "Internal server error.", content = @Content)
//    })
//    @PostMapping(value = "/anonymization_tabular", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<DataSet> processAnonymization(
//            @io.swagger.v3.oas.annotations.parameters.RequestBody(
//                    description = "Request containing the dataset and anonymization configuration.",
//                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
//                            schema = @Schema(implementation = AnonymizationRequest.class)),
//                    required = true
//            )
//            @RequestBody(required = true) AnonymizationRequest request) {
//        try {
//            DataSet dataSet = request.getDataSet();
//            AnonymizationConfig kiaimAnonConfig = request.getKiaimAnonConfig();
//            DataSet result = anonymizationService.anonymizeData(dataSet, kiaimAnonConfig);
//            return ResponseEntity.ok(result);
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
//        }
//    }

    @Operation(summary = "Creates a new anonymization task.",
            description = "Creates a new asynchronous anonymization task and returns a unique task ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Task accepted for processing.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error.", content = @Content)
    })
    @PostMapping(value = "/task", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createAnonymizationTask(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Request containing the dataset and anonymization configuration.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AnonymizationRequest.class)),
                    required = true
            )
            @RequestBody AnonymizationRequest request) {
        try {
            String taskId = String.valueOf(System.currentTimeMillis());
            Future<DataSet> future = anonymizationService.anonymizeData(request.getDataSet(), request.getKiaimAnonConfig());
            tasks.put(taskId, future);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(taskId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(summary = "Gets the status of the anonymization task.",
            description = "Returns the current status of the anonymization task.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully.", content = @Content),
            @ApiResponse(responseCode = "404", description = "Task not found.", content = @Content)
    })
    @GetMapping("/task/{taskId}/status")
    public ResponseEntity<String> getTaskStatus(@PathVariable String taskId) {
        Future<DataSet> future = tasks.get(taskId);
        if (future == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found");
        } else if (future.isDone()) {
            return ResponseEntity.ok("Task completed");
        } else {
            return ResponseEntity.ok("Task in progress");
        }
    }

    @Operation(summary = "Gets the result of the anonymization task.",
            description = "Returns the result of the anonymization task when it has finished.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Result retrieved successfully.", content = @Content),
            @ApiResponse(responseCode = "404", description = "Task not found.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error retrieving task result.", content = @Content)
    })
    @GetMapping("/task/{taskId}/result")
    public ResponseEntity<DataSet> getTaskResult(@PathVariable String taskId) {
        Future<DataSet> future = tasks.get(taskId);
        if (future == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } else if (future.isDone()) {
            try {
                DataSet result = future.get();
                tasks.remove(taskId);
                return ResponseEntity.ok(result);
            } catch (InterruptedException | ExecutionException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } else {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        }
    }

    @GetMapping(value = "/config")
    @Cacheable("config")
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
}