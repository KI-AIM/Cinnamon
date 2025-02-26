package de.kiaim.anon.exception;

public class ResultDatasetProcessingException extends AnonymizationException {

    public ResultDatasetProcessingException(String message) {
        super("ANON_2_4_1", "Failed to generate DataSet object with the anonymized dataset."+ message);
    }
}
