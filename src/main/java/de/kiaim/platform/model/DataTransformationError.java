package de.kiaim.platform.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DataTransformationError {

	private final int index;

	private final String errorMessage;
}
