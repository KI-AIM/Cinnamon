package de.kiaim.anon.exception.processor;

import de.kiaim.anon.exception.AnonymizationException;

public class JALDataGenerationException extends AnonymizationException {
    public JALDataGenerationException(String message) {

        super("ANON_2_3_1", "Error while converting dataset to string array." + message);
    }
}
