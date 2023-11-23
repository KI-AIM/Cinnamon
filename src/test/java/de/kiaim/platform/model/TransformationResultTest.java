package de.kiaim.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.data.*;
import de.kiaim.platform.model.data.configuration.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class TransformationResultTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final TransformationResult transformationResult = TestModelHelper.generateTransformationResult();
		final String json = objectMapper.writeValueAsString(transformationResult);
		final String expected = generateTransformationResultAsJson();
		assertEquals(expected, json);
	}

	private static String generateTransformationResultAsJson() {
		return """
				{"dataSet":{"dataConfiguration":{"dataTypes":["BOOLEAN","DATE","DATE_TIME","DECIMAL","INTEGER","STRING"],"configurations":[{"index":0,"name":"column0_boolean","type":"BOOLEAN","configurations":[]},{"index":1,"name":"column1_date","type":"DATE","configurations":[{"name":"DateFormatConfiguration","dateFormatter":"yyyy-MM-dd"}]},{"index":2,"name":"column2_date_time","type":"DATE_TIME","configurations":[{"name":"DateTimeFormatConfiguration","dateTimeFormatter":"yyyy-MM-dd'T'HH:mm:ss.SSSXXX"}]},{"index":3,"name":"column3_decimal","type":"DECIMAL","configurations":[]},{"index":4,"name":"column4_integer","type":"INTEGER","configurations":[]},{"index":5,"name":"column5_string","type":"STRING","configurations":[{"name":"StringPatternConfiguration","pattern":".*"}]}]},"data":[[true,"2023-11-20","2023-11-20T12:50:27.123456789",4.2,42,"Hello World!"],[false,"2023-11-20","2023-11-20T12:50:27.123456789",2.4,24,"Bye World!"]]},"transformationErrors":[{"index":2,"rawValues":["true","2023-11-20","","4.2","42","Hello World!"],"dataTransformationErrors":[{"index":2,"errorType":"MISSING_VALUE"}]}]}""";
	}
}
