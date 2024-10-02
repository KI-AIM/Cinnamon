package de.kiaim.anon.controller;

import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.anon.service.AnonymizationService;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.model.data.DataSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.NonNull;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

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

//    @Operation(summary = "Creates a new anonymization task.",
//            description = "Creates a new asynchronous anonymization task based on the processId, dataset and anon configuration." +
//                    "The anonymized dataset is return via the given callback URL.")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "202", description = "Task accepted for processing.", content = @Content),
//            @ApiResponse(responseCode = "409", description = "Task with the given process ID already exists.", content = @Content),
//            @ApiResponse(responseCode = "500", description = "Internal server error.", content = @Content)
//    })
//    @PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<String> createAnonymizationTaskWithCallbackResult(
//            @io.swagger.v3.oas.annotations.parameters.RequestBody(
//                    description = "Request containing the process ID, the dataset, the anonymization configuration and the callback URL.",
//                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
//                            schema = @Schema(implementation = AnonymizationRequest.class)),
//                    required = true
//            )
//            @ParameterObject @NonNull AnonymizationRequest request) {
//        try {
//            System.out.println("Request in controller.");
//
//            System.out.println(request.getSession_key().toString());
//
//            String processId = request.getSession_key();
//            if (tasks.containsKey(processId)) {
//                return ResponseEntity.status(HttpStatus.CONFLICT).body("Task with process ID " + processId + " already exists.");
//            }
//            Future<DataSet> future = anonymizationService.anonymizeDataWithCallbackResult(request);
////            Future<DataSet> future = null;
//            tasks.put(processId, future);
//            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
//                    "Anonymization process " + processId + " has been accepted.");
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: " + e.getMessage());
//        }
//    }

    @Operation(summary = "Creates a new anonymization task.",
            description = "Creates a new asynchronous anonymization task based on the processId, dataset, and anonymization configuration. " +
                    "The anonymized dataset is returned via the given callback URL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Task accepted for processing.", content = @Content),
            @ApiResponse(responseCode = "409", description = "Task with the given process ID already exists.", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error.", content = @Content)
    })
    @PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createAnonymizationTaskWithCallbackResult(
            @RequestParam("session_key") @Parameter(description = "The process ID for the anonymization task.", required = true) String session_key,
            @RequestPart("data") @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The dataset to be anonymized.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DataSet.class)),
                    required = true) DataSet data,
            @RequestPart("anonymizationConfig") @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The frontend anonymization configuration.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FrontendAnonConfig.class)),
                    required = true) FrontendAnonConfig anonymizationConfig,
            @RequestParam("callback") @Parameter(description = "The callback URL to return the result.", required = true) String callback) {

        try {
            System.out.println("Request in controller.");
            System.out.println("Process ID: " + session_key);

            if (tasks.containsKey(session_key)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Task with process ID " + session_key + " already exists.");
            }

            // Créer l'objet AnonymizationRequest à partir des différentes parties
            AnonymizationRequest request = new AnonymizationRequest(session_key, data, anonymizationConfig, callback);

            // Appeler le service d'anonymisation
            Future<DataSet> future = anonymizationService.anonymizeDataWithCallbackResult(request);

            tasks.put(session_key, future);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                    "Anonymization process " + session_key + " has been accepted.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: " + e.getMessage());
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

    //    TODO: Unused, delete
//    @Operation(summary = "Creates a new anonymization task.",
//            description = "Creates a new asynchronous anonymization task based on the processId, dataset and anon configuration." +
//                    "A callback with the processId is sent once the process is done.")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "202", description = "Task accepted for processing.", content = @Content),
//            @ApiResponse(responseCode = "409", description = "Task with the given process ID already exists.", content = @Content),
//            @ApiResponse(responseCode = "500", description = "Internal server error.", content = @Content)
//    })
//    @PostMapping(value = "/process/callback/processId", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<String> createAnonymizationTaskWithCallbackProcessId(
//            @io.swagger.v3.oas.annotations.parameters.RequestBody(
//                    description = "Request containing the process ID, the dataset, the anonymization configuration and the callback URL.",
//                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
//                            schema = @Schema(implementation = AnonymizationRequest.class)),
//                    required = true
//            )
//            @RequestBody @NonNull AnonymizationRequest request) {
//        try {
//            String processId = request.getSession_key();
//            if (tasks.containsKey(processId)) {
//                return ResponseEntity.status(HttpStatus.CONFLICT).body("Task with process ID " + processId + " already exists.");
//            }
//            Future<DataSet> future = anonymizationService.anonymizeDataWithCallbackProcessId(request);
//            tasks.put(processId, future);
//            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
//                    "Anonymization process " + processId + " has been accepted.");
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: " + e.getMessage());
//        }
//    }
}