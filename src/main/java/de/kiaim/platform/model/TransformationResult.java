package de.kiaim.platform.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TransformationResult {

	private final DataSet dataSet;

	private final List<DataRowTransformationError> transformationErrors;

	public void addError(final DataRowTransformationError dataRowTransformationError) {
		transformationErrors.add(dataRowTransformationError);
	}
}
