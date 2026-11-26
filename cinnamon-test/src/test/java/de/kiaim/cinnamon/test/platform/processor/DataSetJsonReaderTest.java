package de.kiaim.cinnamon.test.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.Configuration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.RangeConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.StringPatternConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.IntegerData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.DataTransformationError;
import de.kiaim.cinnamon.platform.processor.DataSetJsonReader;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataSetJsonReaderTest extends ContextRequiredTest {

	@Autowired private DataSetJsonReader dataSetJsonReader;

	@Test
	public void readValidDataSet() {
		// The formats of the original file must not be used, as the JSON contains ISO dates
		var configuration = configuration(
				column(0, "date", DataType.DATE, new DateFormatConfiguration("dd.MM.yyyy")),
				column(1, "dateTime", DataType.DATE_TIME, new DateTimeFormatConfiguration("dd.MM.yyyy HH:mm")),
				column(2, "decimal", DataType.DECIMAL),
				column(3, "boolean", DataType.BOOLEAN));
		var json = """
				{"dataConfiguration":{"configurations":[]},"data":[
				  ["1990-01-31","2024-12-17T20:17:48.265",36.2,true]
				]}""";

		var result = assertDoesNotThrow(() -> dataSetJsonReader.read(stream(json), configuration));

		assertEquals(0, result.getTransformationErrors().size());
		assertSame(configuration, result.getDataSet().getDataConfiguration());
		assertEquals(Arrays.asList(LocalDate.of(1990, 1, 31), LocalDateTime.of(2024, 12, 17, 20, 17, 48, 265_000_000),
		                           36.2f, true),
		             values(result.getDataSet().getDataRows().get(0).getData()));
	}

	@Test
	public void readReportsInvalidValues() {
		var configuration = configuration(
				column(0, "gender", DataType.STRING, new StringPatternConfiguration("^(male|female)$")),
				column(1, "count", DataType.INTEGER,
				       new RangeConfiguration(new IntegerData(1), new IntegerData(Integer.MAX_VALUE))));
		// Partially masked value, value out of range, suppressed values (null)
		var json = """
				{"data":[
				  ["female",3],
				  ["femXXX",0],
				  [null,null]
				]}""";

		var result = assertDoesNotThrow(() -> dataSetJsonReader.read(stream(json), configuration));

		// Invalid values are stored as null instead of failing the whole data set
		var rows = result.getDataSet().getDataRows();
		assertEquals(3, rows.size());
		assertEquals(Arrays.asList("female", 3), values(rows.get(0).getData()));
		assertEquals(Arrays.asList(null, null), values(rows.get(1).getData()));

		var errors = result.getTransformationErrors();
		assertEquals(2, errors.size());
		assertEquals(1, errors.get(0).getIndex());
		assertEquals(List.of(TransformationErrorType.FORMAT_ERROR, TransformationErrorType.VALUE_NOT_IN_RANGE),
		             errors.get(0).getDataTransformationErrors().stream()
		                   .map(DataTransformationError::getErrorType).toList());
		// Suppressed values are missing values, like empty cells of other file types
		assertEquals(2, errors.get(1).getIndex());
		assertEquals(List.of(TransformationErrorType.MISSING_VALUE, TransformationErrorType.MISSING_VALUE),
		             errors.get(1).getDataTransformationErrors().stream()
		                   .map(DataTransformationError::getErrorType).toList());
	}

	@Test
	public void readInvalidJson() {
		var configuration = configuration(column(0, "a", DataType.STRING));

		var exception = assertThrows(InternalIOException.class,
		                             () -> dataSetJsonReader.read(stream("{\"rows\":[]}"), configuration));
		assertTrue(exception.getErrorCode().endsWith("_" + InternalIOException.DATA_SET_READING));
	}

	private static DataConfiguration configuration(final ColumnConfiguration... columns) {
		var configuration = new DataConfiguration();
		for (var column : columns) {
			configuration.addColumnConfiguration(column);
		}
		return configuration;
	}

	private static ColumnConfiguration column(final int index, final String name, final DataType type,
	                                          final Configuration... configurations) {
		return new ColumnConfiguration(index, name, type, type.getDefaultScale(),
		                               new ArrayList<>(List.of(configurations)));
	}

	private static List<Object> values(final List<Data> data) {
		return data.stream().map(Data::getValue).toList();
	}

	private static ByteArrayInputStream stream(final String json) {
		return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
	}
}
