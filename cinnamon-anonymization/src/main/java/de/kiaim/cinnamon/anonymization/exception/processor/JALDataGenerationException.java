package de.kiaim.cinnamon.anonymization.exception.processor;

import de.kiaim.cinnamon.anonymization.exception.AnonymizationException;

public class JALDataGenerationException extends AnonymizationException {
    public JALDataGenerationException(String message) {

        super("ANON_2_3_1", "Error while converting dataset to string array." + message);
    }
}
