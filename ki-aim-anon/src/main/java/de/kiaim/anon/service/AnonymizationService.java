package de.kiaim.anon.service;

import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.converter.FrontendAnonConfigConverter;
import de.kiaim.anon.converter.KiaimAnonConfigConverter;
import de.kiaim.anon.model.AnonymizationErrorResponse;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.anon.processor.AnonymizedDatasetProcessor;
import de.kiaim.anon.processor.DataSetProcessor;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.model.data.DataSet;
import lombok.extern.slf4j.Slf4j;
import org.bihmi.jal.anon.Anonymizer;
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

import static org.apache.tomcat.util.buf.ByteChunk.convertToBytes;

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

    @Autowired
    private KiaimAnonConfigConverter datasetAnonConfigConverter;

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
    @Async
    public CompletableFuture<DataSet> anonymizeData(DataSet dataSet,
                                                    FrontendAnonConfig frontendAnonConfig,
                                                    String processId) throws Exception {
        // Validation de la compatibilité entre le DataSet et la configuration frontend
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
    @Async
    public CompletableFuture<DataSet> anonymizeDataWithCallbackResult(AnonymizationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Start anon.");
                CompatibilityAssurance.checkDataSetAndFrontendConfigCompatibility(request.getData(), request.getAnonymizationConfig());
                AnonymizationConfig anonymizationConfigConverted = FrontendAnonConfigConverter.convertToJALConfig(request.getAnonymizationConfig(), request.getData());
                String[][] jalData = dataSetProcessor.convertDatasetToStringArray(request.getData());
                log.info("Jal data generated, start anonymize.");
                Anonymizer anonymizer = new Anonymizer(jalData, anonymizationConfigConverted.toJalConfig(request.getSession_key()));
                anonymizer.anonymize();
                DataSet result = AnonymizedDatasetProcessor.convertToDataSet(anonymizer.AnonymizedData(), request.getData().getDataConfiguration());
                log.info("Anon finished.");

                // Send success callback
                sendCallbackResult(request.getCallback(), result);
                return result;
            } catch (Exception ex) {
                log.error("An error occurred during data anonymization", ex);
                sendFailureCallback(request.getCallback(), ex);
                throw new RuntimeException(ex);
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
            byte[] syntheticDataBytes = convertToBytes(result.toString());

            // Create Multipart request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("synthetic_data", new ByteArrayResource(syntheticDataBytes) {
                @Override
                public String getFilename() {
                    return "synthetic_data.bin";
                }
            });

            // Send request
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
            log.error("Error preparing multipart request for callback", e);
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

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
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

            // Envoyer la requête POST avec le corps multipart
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

    //    TODO: Old version. Delete
//    /**
//     * Checks if the given dataset and configurations are consistent.
//     * This includes checks for attributes that are always NaN (null) and unsupported data types.
//     *
//     * @param dataset The dataset to check.
//     * @param config The data configuration.
//     * @param anonConfig The anonymization configuration.
//     * @return true if the configurations are valid, false otherwise.
//     */
//    protected boolean checkDataConfiguration(DataSet dataset, DataConfiguration config, AnonymizationConfig anonConfig){
//        // check if configuration and dataset is consistent, will probably be already made when creating the dataset object
//        checkDataSetCompatibility(dataset);
//        // check if any datatype is included, that can not be supported yet
//        // Question : must be done in platform module ?
//        return false;
//    }

//    TODO : Old Version, delete.
//    /**
//     * Asynchronously anonymizes the given dataset based on the provided anonymization configuration and process ID.
//     *
//     * @param dataSet The dataset to anonymize.
//     * @param kiaimAnonConfig The KI-AIM anonymization configuration.
//     * @param processId The process ID for the anonymization process.
//     * @return A CompletableFuture containing the anonymized dataset.
//     * @throws Exception If an error occurs during the anonymization process.
//     */
//    @Async
//    public CompletableFuture<DataSet> anonymizeData(DataSet dataSet,
//                                                    de.kiaim.model.configuration.anonymization.AnonymizationConfig kiaimAnonConfig,
//                                                    String processId) throws Exception {
//        log.info("Start anon.");
//        // Convert KI-AIM DatasetAnonymizationConfig to AnonymizationConfig usable by JAL
//        AnonymizationConfig anonymizationConfigConverted = datasetAnonConfigConverter.convert(kiaimAnonConfig, dataSet.getDataConfiguration());
//
//        // Convert KI-AIM DataSet object to String[][] usable by JAL
//        String[][] jalData = dataSetProcessor.convertDatasetToStringArray(dataSet);
//
//        log.info("Jal data generated, start anonymize.");
//        // TODO : add mechanism to make anon name variable: add process ID
//
//        Anonymizer anonymizer = new Anonymizer(jalData, anonymizationConfigConverted.toJalConfig(processId));
//        anonymizer.anonymize();
//
//        DataSet result = AnonymizedDatasetProcessor.convertToDataSet(anonymizer.AnonymizedData(), dataSet.getDataConfiguration());
//        log.info("Anon finished.");
//        return CompletableFuture.completedFuture(result);
//    }

//    TODO: Old version: delete.
//    /**
//     * Asynchronously anonymizes the given dataset based on the provided anonymization configuration and process ID.
//     * Once the anonymization is complete, it sends the result to the specified callback URL.
//     * The result of the anonymization is also stored for future retrieval.
//     *
//     * @param request The anonymization request containing the dataset, configuration, and callback URL.
//     * @return A CompletableFuture containing the anonymized dataset.
//     */
//    @Async
//    public CompletableFuture<DataSet> anonymizeDataWithCallbackResult(AnonymizationRequest request) {
//        return CompletableFuture.supplyAsync(() -> {
//            try {
//                log.info("Start anon.");
//                AnonymizationConfig anonymizationConfigConverted = datasetAnonConfigConverter.convert(request.getAnonymizationConfig(), request.getData().getDataConfiguration());
//                String[][] jalData = dataSetProcessor.convertDatasetToStringArray(request.getData());
//                log.info("Jal data generated, start anonymize.");
//                Anonymizer anonymizer = new Anonymizer(jalData, anonymizationConfigConverted.toJalConfig(request.getSession_key()));
//                anonymizer.anonymize();
//                DataSet result = AnonymizedDatasetProcessor.convertToDataSet(anonymizer.AnonymizedData(), request.getData().getDataConfiguration());
//                log.info("Anon finished.");
//
//                // Send success callback
//                sendCallbackResult(request.getCallback(), result);
//                return result;
//            } catch (Exception ex) {
//                log.error("An error occurred during data anonymization", ex);
//                sendFailureCallback(request.getCallback(), ex);
//                throw new RuntimeException(ex);
//            }
//        });
//    }

    //     TODO: Unused, delete.
//    /**
//     * Asynchronously anonymizes the given dataset based on the provided anonymization configuration and process ID.
//     * Once the anonymization is complete, it sends the processID of the completed task to the specified callback URL.
//     * The result of the anonymization is also stored so it can be retrieved via the GET endpoint.
//     *
//     * @param request The anonymization request containing the dataset, configuration, and callback URL.
//     * @return A CompletableFuture containing the anonymized dataset.
//     */
//    @Async
//    public CompletableFuture<DataSet> anonymizeDataWithCallbackProcessId(AnonymizationRequest request) {
//        return CompletableFuture.supplyAsync(() -> {
//            try {
//                log.info("Start anon.");
//                AnonymizationConfig anonymizationConfigConverted = datasetAnonConfigConverter.convert(request.getAnonymizationConfig(), request.getData().getDataConfiguration());
//                String[][] jalData = dataSetProcessor.convertDatasetToStringArray(request.getData());
//                log.info("Jal data generated, start anonymize.");
//                Anonymizer anonymizer = new Anonymizer(jalData, anonymizationConfigConverted.toJalConfig(request.getSession_key()));
//                anonymizer.anonymize();
//                DataSet result = AnonymizedDatasetProcessor.convertToDataSet(anonymizer.AnonymizedData(), request.getData().getDataConfiguration());
//                log.info("Anon finished.");
//
//                // Send success callback
//                sendCallbackProcessId(request.getCallback(), request.getSession_key());
//                return result;
//            } catch (Exception ex) {
//                log.error("An error occurred during data anonymization", ex);
//                sendFailureCallback(request.getCallback(), ex);
//                throw new RuntimeException(ex);
//            }
//        });
//    }

    //     TODO: Unused, delete.
//    /**
//     * Sends the anonymized dataset to the specified callback URL.
//     *
//     * @param callbackUrl The URL to send the callback to.
//     * @param processId The processId of the completed anonymization task.
//     */
//    public void sendCallbackProcessId(String callbackUrl, String processId) {
//        log.info("Sending callback to URL: {}", callbackUrl);
//        long startTime = System.currentTimeMillis();
//
//        webClient.post()
//                .uri(callbackUrl)
//                .body(BodyInserters.fromValue(processId))
//                .retrieve()
//                .bodyToMono(Void.class)
//                .doOnError(e -> log.error("Failed to send callback to URL: {}", callbackUrl, e))
//                .doFinally(signal -> log.info("Callback sent to URL: {} in {} ms", callbackUrl, System.currentTimeMillis() - startTime))
//                .subscribe();
//    }
}
