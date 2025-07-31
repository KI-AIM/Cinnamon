package de.kiaim.cinnamon.test.platform.processor;

import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.processor.FhirProcessor;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FhirProcessorTest extends ContextRequiredTest {

	@Autowired private FhirProcessor fhirProcessor;

	@Test
	public void read() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();

		var estimation = assertDoesNotThrow(
				() -> fhirProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()), null,
				                                              DatatypeEstimationAlgorithm.MOST_ESTIMATED));
		var data = assertDoesNotThrow(
				() -> fhirProcessor.read(new ByteArrayInputStream(bundle.getBytes()), null,
				                         estimation.getDataConfiguration()));

		assertEquals(21, data.getTransformationErrors().size());
	}

	@Test
	public void estimateDataConfiguration() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();

		var estimation = assertDoesNotThrow(
				() -> fhirProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()), null,
				                                              DatatypeEstimationAlgorithm.MOST_ESTIMATED));

		assertEquals(26, estimation.getDataConfiguration().getConfigurations().size());
	}
}
