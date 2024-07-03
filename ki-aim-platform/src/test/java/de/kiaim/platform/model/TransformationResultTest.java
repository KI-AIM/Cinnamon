package de.kiaim.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.ContextRequiredTest;
import de.kiaim.platform.TestModelHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransformationResultTest extends ContextRequiredTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final TransformationResult transformationResult = TestModelHelper.generateTransformationResult(true);
		final String json = objectMapper.writeValueAsString(transformationResult);
		final String expected = TestModelHelper.generateTransformationResultAsYaml();
		assertEquals(expected, json);
	}
}
