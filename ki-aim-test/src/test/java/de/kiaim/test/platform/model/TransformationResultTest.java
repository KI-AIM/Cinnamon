package de.kiaim.test.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.test.platform.ContextRequiredTest;
import de.kiaim.test.util.TransformationResultTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransformationResultTest extends ContextRequiredTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(true);
		final String json = objectMapper.writeValueAsString(transformationResult);
		final String expected = TransformationResultTestHelper.generateTransformationResultAsYaml();
		assertEquals(expected, json);
	}
}
