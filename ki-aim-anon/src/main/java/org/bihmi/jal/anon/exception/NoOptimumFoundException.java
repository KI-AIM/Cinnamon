package org.bihmi.jal.anon.exception;

public class NoOptimumFoundException extends RuntimeException {

    //No solution found for the anonymization with the given parameters.
    // Possible Fix : Show message to user, navigate to anonymization configuration
    private static final String ERROR_CODE = "ANON_1_1";

    public NoOptimumFoundException() {
        super(ERROR_CODE); // Laissez un message par défaut si nécessaire.
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}