package de.kiaim.anon.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class AnonymizationErrorResponse {
    private final String errorMessage;
    private final String errorDetails;
}
