package de.kiaim.anon.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnonymizationError {
    private final String errorCode;
    private final String errorMessage;
}
