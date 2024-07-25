package de.kiaim.anon.service;

import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.converter.KiaimAnonConfigConverter;
import de.kiaim.anon.model.AnonymizationErrorResponse;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.anon.processor.AnonymizedDatasetProcessor;
import de.kiaim.anon.processor.DataSetProcessor;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.DataSet;
import lombok.extern.slf4j.Slf4j;
import org.bihmi.jal.anon.Anonymizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

import static de.kiaim.anon.service.CompatibilityAssurance.checkDataSetCompatibility;

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
     * Checks if the given dataset and configurations are consistent.
     * This includes checks for attributes that are always NaN (null) and unsupported data types.
     *
     * @param dataset The dataset to check.
     * @param config The data configuration.
     * @param anonConfig The anonymization configuration.
     * @return true if the configurations are valid, false otherwise.
     */
    protected boolean checkDataConfiguration(DataSet dataset, DataConfiguration config, AnonymizationConfig anonConfig){
        // check if configuration and dataset is consistent, will probably be already made when creating the dataset object
        checkDataSetCompatibility(dataset);
        //TODO : check if any attributes are always NaN (null)
        // check if any datatype is included, that can not be supported yet
        // Question : must be done in platform module ?
        return false;
    }

    /**
     * Asynchronously anonymizes the given dataset based on the provided anonymization configuration and process ID.
     *
     * @param dataSet The dataset to anonymize.
     * @param kiaimAnonConfig The KI-AIM anonymization configuration.
     * @param processId The process ID for the anonymization process.
     * @return A CompletableFuture containing the anonymized dataset.
     * @throws Exception If an error occurs during the anonymization process.
     */
    @Async
    public CompletableFuture<DataSet> anonymizeData(DataSet dataSet,
                                                    de.kiaim.model.configuration.anonymization.AnonymizationConfig kiaimAnonConfig,
                                                    String processId) throws Exception {
        // TODO : add quality check mechanism
        log.info("Start anon.");
        // Convert KI-AIM DatasetAnonymizationConfig to AnonymizationConfig usable by JAL
        AnonymizationConfig anonymizationConfigConverted = datasetAnonConfigConverter.convert(kiaimAnonConfig, dataSet.getDataConfiguration());

        // Convert KI-AIM DataSet object to String[][] usable by JAL
        String[][] jalData = dataSetProcessor.convertDatasetToStringArray(dataSet);

        log.info("Jal data generated, start anonymize.");
        // TODO : add mechanism to make anon name variable: add process ID

        Anonymizer anonymizer = new Anonymizer(jalData, anonymizationConfigConverted.toJalConfig(processId));
        anonymizer.anonymize();

        DataSet result = AnonymizedDatasetProcessor.convertToDataSet(anonymizer.AnonymizedData(), dataSet.getDataConfiguration());
        log.info("Anon finished.");
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Asynchronously anonymizes the data and sends a callback with the result.
     *
     * @param request The anonymization request containing the dataset, configuration, and callback URL.
     * @return A CompletableFuture that completes when the callback is sent.
     * @throws Exception If an error occurs during the anonymization process.
     */
    @Async
    public CompletableFuture<Void> anonymizeDataWithCallback(AnonymizationRequest request) throws Exception {
        return anonymizeData(request.getDataSet(), request.getKiaimAnonConfig(), request.getProcessId())
                .thenAccept(result -> sendCallback(request.getCallbackURL(), result))
                .exceptionally(ex -> {
                    log.error("An error occurred during data anonymization", ex);
                    sendFailureCallback(request.getCallbackURL(), ex);
                    return null;
                });
    }

    /**
     * Sends the anonymized dataset to the specified callback URL.
     *
     * @param callbackUrl The URL to send the callback to.
     * @param result The anonymized dataset.
     */
    public void sendCallback(String callbackUrl, DataSet result) {
        log.info("Sending callback to URL: {}", callbackUrl);
        long startTime = System.currentTimeMillis();

        webClient.post()
                .uri(callbackUrl)
                .body(BodyInserters.fromValue(result))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to send callback to URL: {}", callbackUrl, e))
                .doFinally(signal -> log.info("Callback sent to URL: {} in {} ms", callbackUrl, System.currentTimeMillis() - startTime))
                .subscribe();
    }

    /**
     * Sends a failure callback with the error message to the specified callback URL.
     *
     * @param callbackUrl The URL to send the failure callback to.
     * @param ex The exception that occurred.
     */
    public void sendFailureCallback(String callbackUrl, Throwable ex) {
        AnonymizationErrorResponse errorResponse = new AnonymizationErrorResponse("Anonymization failed", ex.getMessage());
        webClient.post()
                .uri(callbackUrl)
                .body(BodyInserters.fromValue(errorResponse))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to send failure callback to URL: {}", callbackUrl, e))
                .subscribe();
    }
}
