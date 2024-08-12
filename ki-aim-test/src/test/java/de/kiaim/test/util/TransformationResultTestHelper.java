package de.kiaim.test.util;

import de.kiaim.model.enumeration.TransformationErrorType;
import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataTransformationError;
import de.kiaim.platform.model.TransformationResult;

import java.util.ArrayList;
import java.util.List;

import static de.kiaim.test.util.YamlUtil.indentYaml;

public class TransformationResultTestHelper {
	public static TransformationResult generateTransformationResult(final boolean withErrors) {

		if (withErrors) {
			final List<String> rawValues = List.of("true", "2023-11-20", "", "4.2", "forty two", "Hello World!");
			final DataRowTransformationError dataRowTransformationError = new DataRowTransformationError(2, rawValues);

			final DataTransformationError missingValueError = new DataTransformationError(2,
			                                                                              TransformationErrorType.MISSING_VALUE);
			dataRowTransformationError.addError(missingValueError);

			final DataTransformationError formatError = new DataTransformationError(4,
			                                                                        TransformationErrorType.FORMAT_ERROR);
			dataRowTransformationError.addError(formatError);

			final List<DataRowTransformationError> dataRowTransformationErrors = List.of(dataRowTransformationError);
			return new TransformationResult(DataSetTestHelper.generateDataSet(true), dataRowTransformationErrors);
		} else {
			return new TransformationResult(DataSetTestHelper.generateDataSet(), new ArrayList<>());
		}
	}

	public static String generateTransformationResultAsJsonA() {
		return
				"""
						{"dataSet":""" + DataSetTestHelper.generateDataSetAsJson() +
				"""
						,"transformationErrors":[{"index":2,"rawValues":["true","2023-11-20","","4.2","forty two","Hello World!"],"dataTransformationErrors":[{"index":2,"errorType":"MISSING_VALUE"},{"index":4,"errorType":"FORMAT_ERROR"}]}]}""";
	}

	public static String generateTransformationResultAsJsonB() {
		return
				"""
						{"dataSet":""" + DataSetTestHelper.generateDataSetAsJson() +
				"""
						,"transformationErrors":[{"index":2,"rawValues":["true","2023-11-20","","4.2","forty two","Hello World!"],"dataTransformationErrors":[{"index":4,"errorType":"FORMAT_ERROR"},{"index":2,"errorType":"MISSING_VALUE"}]}]}""";
	}

	public static String generateTransformationResultAsYaml() {
		return
				"""
						dataSet:
						""" + indentYaml(DataSetTestHelper.generateDataSetAsYaml()) +
				"""
						transformationErrors:
						- index: 2
						  rawValues:
						  - "true"
						  - "2023-11-20"
						  - ""
						  - "4.2"
						  - "forty two"
						  - "Hello World!"
						  dataTransformationErrors:
						  - index: 2
						    errorType: "MISSING_VALUE"
						  - index: 4
						    errorType: "FORMAT_ERROR"
						""";
	}
}
