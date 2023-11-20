package de.kiaim.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.model.data.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class TransformationResultTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final List<DataType> dataTypes = List.of(DataType.BOOLEAN,
		                                         DataType.DATE,
		                                         DataType.DATE_TIME,
		                                         DataType.DECIMAL,
		                                         DataType.INTEGER,
		                                         DataType.STRING);
		final DataConfiguration dataConfiguration = new DataConfiguration(dataTypes);

		final List<Data> data1 = List.of(new BooleanData(true),
		                                 new DateData(LocalDate.of(2023, 11, 20)),
		                                 new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123)),
		                                 new DecimalData(4.2f),
		                                 new IntegerData(42),
		                                 new StringData("Hello World!"));
		final List<Data> data2 = List.of(new BooleanData(false),
		                                 new DateData(LocalDate.of(2023, 11, 20)),
		                                 new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123)),
		                                 new DecimalData(2.4f),
		                                 new IntegerData(24),
		                                 new StringData("Bye World!"));
		final DataRow dataRow1 = new DataRow(data1);
		final DataRow dataRow2 = new DataRow(data2);
		final List<DataRow> dataRows = List.of(dataRow1, dataRow2);

		final DataSet dataSet = new DataSet(dataRows, dataConfiguration);

		final List<String> rawValues = List.of("true", "2023-11-20", "", "4.2", "42", "Hello World!");
		final DataRowTransformationError dataRowTransformationError = new DataRowTransformationError(2, rawValues);
		final DataTransformationError dataTransformationError = new DataTransformationError(2, TransformationErrorType.MISSING_VALUE);
		dataRowTransformationError.addError(dataTransformationError);
		final List<DataRowTransformationError> dataRowTransformationErrors = List.of(dataRowTransformationError);

		final TransformationResult transformationResult =  new TransformationResult(dataSet, dataRowTransformationErrors);

		final String json = objectMapper.writeValueAsString(transformationResult);
		final String expected = """
				{"dataSet":{"dataRows":[{"data":[{"value":true,"dataType":"BOOLEAN"},{"value":"2023-11-20","dataType":"DATE"},{"value":"2023-11-20T12:50:27.000000123","dataType":"DATE_TIME"},{"value":4.2,"dataType":"DECIMAL"},{"value":42,"dataType":"INTEGER"},{"value":"Hello World!","dataType":"STRING"}]},{"data":[{"value":false,"dataType":"BOOLEAN"},{"value":"2023-11-20","dataType":"DATE"},{"value":"2023-11-20T12:50:27.000000123","dataType":"DATE_TIME"},{"value":2.4,"dataType":"DECIMAL"},{"value":24,"dataType":"INTEGER"},{"value":"Bye World!","dataType":"STRING"}]}],"dataConfiguration":{"dataTypes":["BOOLEAN","DATE","DATE_TIME","DECIMAL","INTEGER","STRING"]}},"transformationErrors":[{"index":2,"rawValues":["true","2023-11-20","","4.2","42","Hello World!"],"dataTransformationErrors":[{"index":2,"errorType":"MISSING_VALUE"}]}]}""";

		assertEquals(expected, json);
	}

}
