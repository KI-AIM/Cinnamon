package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;
import de.kiaim.cinnamon.platform.model.dto.LoadDataRequest;
import de.kiaim.cinnamon.platform.model.StoredTransformationError;
import de.kiaim.cinnamon.platform.service.DataSetService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.DataSetTestHelper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataSetServiceTest extends ContextRequiredTest {

	@Autowired private DataSetService dataSetService;

	/**
	 * Row 2 of the data set contains null values in column 2 and 4.
	 * Column 2 has a missing value error for an empty string, column 4 has no error and was initially null.
	 */
	@Test
	void encodeMissingValues() {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final var errors = List.of(error(2, TransformationErrorType.MISSING_VALUE, ""));

		final LoadDataRequest request = new LoadDataRequest();
		request.setMissingValueEncoding("MISSING");
		final List<List<@Nullable Object>> data = encode(dataSet, errors, request);

		assertEquals("MISSING", data.get(2).get(2));
		assertEquals("MISSING", data.get(2).get(4), "Null values without an error must be encoded as missing!");
	}

	@Test
	void encodeMissingValuesWithOriginalValue() {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final var errors = List.of(error(2, TransformationErrorType.MISSING_VALUE, ""));

		final LoadDataRequest request = new LoadDataRequest();
		request.setMissingValueEncoding("$value");
		final List<List<@Nullable Object>> data = encode(dataSet, errors, request);

		assertEquals("", data.get(2).get(2));
		assertNull(data.get(2).get(4), "The original value of null values without an error is null!");
	}

	@Test
	void encodeMissingValuesDoesNotOverwriteErrors() {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final var errors = List.of(error(4, TransformationErrorType.FORMAT_ERROR, "forty two"));

		final LoadDataRequest request = new LoadDataRequest();
		request.setMissingValueEncoding("MISSING");
		request.setFormatErrorEncoding("$value");
		final List<List<@Nullable Object>> data = encode(dataSet, errors, request);

		assertEquals("MISSING", data.get(2).get(2));
		assertEquals("forty two", data.get(2).get(4));
	}

	@Test
	void requiresTransformationErrors() {
		final LoadDataRequest request = new LoadDataRequest();
		assertFalse(dataSetService.requiresTransformationErrors(request),
		            "Errors are not required if all encodings are '$null'!");

		request.setFormatErrorEncoding("$value");
		assertTrue(dataSetService.requiresTransformationErrors(request));

		final LoadDataRequest defaultEncodingRequest = new LoadDataRequest();
		defaultEncodingRequest.setDefaultNullEncoding("N/A");
		assertTrue(dataSetService.requiresTransformationErrors(defaultEncodingRequest));
	}

	private List<List<@Nullable Object>> encode(final DataSet dataSet,
	                                            final Collection<StoredTransformationError> errors,
	                                            final LoadDataRequest request) {
		return dataSetService.encodeDataRows(dataSet, errors,
		                                     dataSetService.getColumnIndexMapping(dataSet.getDataConfiguration()),
		                                     request);
	}

	private static StoredTransformationError error(final int columnIndex, final TransformationErrorType type,
	                                               final String originalValue) {
		return new StoredTransformationError(2, columnIndex, type, originalValue);
	}
}
