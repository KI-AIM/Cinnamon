package de.kiaim.platform.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class DataRowTransformationError {

	private final int index;

	private final List<String> rawValues;

	private final List<DataTransformationError> dataTransformationErrors = new ArrayList<>();

	public void addError(final DataTransformationError dataTransformationError) {
		dataTransformationErrors.add(dataTransformationError);
	}
}
