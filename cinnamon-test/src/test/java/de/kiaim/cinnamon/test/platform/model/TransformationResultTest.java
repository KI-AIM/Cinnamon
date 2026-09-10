package de.kiaim.cinnamon.test.platform.model;

import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.TransformationResultTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransformationResultTest extends ContextRequiredTest {

	@Autowired
	YAMLMapper yamlMapper;

	@Test
	public void serializationTest() {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(true);
		final String json = yamlMapper.writeValueAsString(transformationResult);
		final String expected = TransformationResultTestHelper.generateTransformationResultAsYaml();
		assertEquals(expected, json);
	}
}
