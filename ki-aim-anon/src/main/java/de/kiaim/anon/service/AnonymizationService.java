package de.kiaim.anon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.converter.FrontendAnonConfigConverter;
import de.kiaim.anon.exception.AnonymizationException;
import de.kiaim.anon.exception.UnexpectedAnonymizationException;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.anon.processor.AnonymizedDatasetProcessor;
import de.kiaim.anon.processor.DataSetProcessor;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.serialization.mapper.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.bihmi.jal.anon.Anonymizer;
import org.bihmi.jal.anon.exception.NoOptimumFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class AnonymizationService {
//    Interface to the platform that receive data and proceed anonymization
//    TODO :
//     - check dataset and configuration
//     - transform the configs into JAL configs object
//     - transform the dataset (if necessary) including the dataset configuration
//     - start the anonymization process with JAL
//     within JAL :
//          - check dataset and config again
//          - create hierarchies
//          - transform dates
//          - perform anonymization using ARX
//          - give back anonymized dataset and performance metrics
//     - analyze results (QualityAssurance)
//     - give back anonymized dataset and performance metrics

    @Autowired
    private DataSetProcessor dataSetProcessor;

    private final WebClient webClient;

    @Autowired
    public AnonymizationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Asynchronously anonymizes the given dataset based on the provided anonymization configuration and process ID.
     *
     * @param dataSet The dataset to anonymize.
     * @param frontendAnonConfig The KI-AIM anonymization configuration.
     * @param processId The process ID for the anonymization process.
     * @return A CompletableFuture containing the anonymized dataset.
     * @throws Exception If an error occurs during the anonymization process.
     */
    @Async("taskExecutor")
    public CompletableFuture<DataSet> anonymizeData(DataSet dataSet,
                                                    FrontendAnonConfig frontendAnonConfig,
                                                    String processId) throws Exception {

        // Check that at least one attribute configuration as been defined by the user.
        FrontendAnonConfigValidation.validateAttributeConfiguration(frontendAnonConfig);

        // Check compatibility between DataSet and FrontendAnonConfig
        CompatibilityAssurance.checkDataSetAndFrontendConfigCompatibility(dataSet, frontendAnonConfig);

        log.info("Start anon.");
        // Convert FrontendAnonymizationConfig to AnonymizationConfig usable by JAL
        AnonymizationConfig anonymizationConfigConverted = FrontendAnonConfigConverter.convertToJALConfig(frontendAnonConfig, dataSet);

        // Convert KI-AIM DataSet object to String[][] usable by JAL
        String[][] jalData = dataSetProcessor.convertDatasetToStringArray(dataSet);

        log.info("Jal data generated, start anonymize.");

        Anonymizer anonymizer = new Anonymizer(jalData, anonymizationConfigConverted.toJalConfig(processId));
        anonymizer.anonymize();

        DataSet result = AnonymizedDatasetProcessor.convertToDataSet(anonymizer.AnonymizedData(), dataSet.getDataConfiguration());
        log.info(result.toString());
        log.info("Anon finished.");
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Asynchronously anonymizes the given dataset based on the provided anonymization configuration and process ID.
     * Once the anonymization is complete, it sends the result to the specified callback URL.
     * The result of the anonymization is also stored for future retrieval.
     *
     * @param request The anonymization request containing the dataset, configuration, and callback URL.
     * @return A CompletableFuture containing the anonymized dataset.
     */
    @Async("taskExecutor")
    public CompletableFuture<DataSet> anonymizeDataWithCallbackResult(AnonymizationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Start anon.");
                FrontendAnonConfigValidation.validateAttributeConfiguration(request.getAnonymizationConfig());
                FrontendAnonConfigValidation.validateOneAttributeIsGeneralized(request.getAnonymizationConfig());
                CompatibilityAssurance.checkDataSetAndFrontendConfigCompatibility(request.getData(), request.getAnonymizationConfig());
                AnonymizationConfig anonymizationConfigConverted = FrontendAnonConfigConverter.convertToJALConfig(request.getAnonymizationConfig(), request.getData());
                String[][] jalData = dataSetProcessor.convertDatasetToStringArray(request.getData());
                log.info("Session key:");
                log.info(request.getSession_key());

                Anonymizer anonymizer = new Anonymizer(jalData, anonymizationConfigConverted.toJalConfig(request.getSession_key()));
                log.info("Instance created.");
                anonymizer.anonymize();
                log.info("Anon executed.");
                DataSet result = AnonymizedDatasetProcessor.convertToDataSet(anonymizer.AnonymizedData(), request.getData().getDataConfiguration());
                log.info("Anon finished.");

                // Send success callback
                sendCallbackResult(request.getCallback(), result);
                return result;
            } catch (NoOptimumFoundException e) {
                log.error("No optimum found during anonymization", e);
                sendFailureCallback(request.getCallback(), new de.kiaim.anon.exception.NoOptimumFoundException());
                return null;
            } catch (AnonymizationException ex) {
                log.error("An error occurred during data anonymization", ex);
                sendFailureCallback(request.getCallback(), ex);
                return null;
            } catch (Exception e) {
                log.error("Unexpected error during anonymization", e);
                sendFailureCallback(request.getCallback(), new UnexpectedAnonymizationException(e));
                return null;
            }

        });
    }

    /**
     * Sends the anonymized dataset to the specified callback URL.
     *
     * @param callbackUrl The URL to send the callback to.
     * @param result The anonymized dataset.
     */
    public void sendCallbackResult(String callbackUrl, DataSet result) {
        log.info("Sending callback to URL: {}", callbackUrl);
        long startTime = System.currentTimeMillis();

        try {
            // Convert DataSet object to JSON
            ObjectMapper jsonMapper = JsonMapper.jsonMapper();
            String anonymizedDatasetJson = jsonMapper.writeValueAsString(result);

            // Create Multipart request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("anonymized_dataset", new ByteArrayResource(anonymizedDatasetJson.getBytes()) {
                @Override
                public String getFilename() {
                    return "anonymized_dataset.bin";
                }
            });

            // Send JSON request
            webClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .doOnError(e -> log.error("Failed to send callback to URL: {}", callbackUrl, e))
                    .doFinally(signal -> log.info("Callback sent to URL: {} in {} ms", callbackUrl, System.currentTimeMillis() - startTime))
                    .subscribe();
        } catch (Exception e) {
            log.error("Error preparing JSON request for callback", e);
        }
    }

    /**
     * Sends a failure callback with the error message to the specified callback URL.
     *
     * @param callbackUrl The URL to send the failure callback to.
     * @param ex The exception that occurred.
     */
    public void sendFailureCallback(String callbackUrl, Throwable ex) {
        log.info("Sending failure callback to URL: {}", callbackUrl);
        long startTime = System.currentTimeMillis();

        try {
            String errorMessage = "Anonymization failed";
            String exceptionMessage = ex.getMessage() != null ? ex.getMessage() : "No additional information";
            String errorCode = "ANON_UNKNOWN";

            // If the exception is an instance of AnonymizationException, retrieve errorCode
            if (ex instanceof AnonymizationException anonymizationException) {
                errorCode = anonymizationException.getErrorCode();
                errorMessage = anonymizationException.getMessage();
            }

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("error_code", new ByteArrayResource(errorCode.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() { return "error_code.txt"; }
            });
            body.add("error_message", new ByteArrayResource(errorMessage.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return "error_message.txt";
                }
            });
            body.add("exception_message", new ByteArrayResource(exceptionMessage.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return "exception_message.txt";
                }
            });

            webClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(2)))
                    .doOnError(e -> log.error("Failed to send failure callback to URL: {}", callbackUrl, e))
                    .doFinally(signal -> log.info("Failure callback sent to URL: {} in {} ms", callbackUrl, System.currentTimeMillis() - startTime))
                    .subscribe();
        } catch (Exception e) {
            log.error("Error preparing multipart request for failure callback", e);
        }
    }
}
