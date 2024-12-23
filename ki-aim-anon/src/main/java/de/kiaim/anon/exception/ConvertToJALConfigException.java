package de.kiaim.anon.exception;

public class ConvertToJALConfigException extends AnonymizationException {
    public ConvertToJALConfigException(String message) {
        super("ANON_2_2_2", "An error occurred while generating the JAL configuration." + message);
    }
}
