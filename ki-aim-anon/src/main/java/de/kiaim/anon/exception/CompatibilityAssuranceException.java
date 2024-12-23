package de.kiaim.anon.exception;

public class CompatibilityAssuranceException extends AnonymizationException {
    public CompatibilityAssuranceException(String message) {
        super("ANON_2_1", message);
    }
}
