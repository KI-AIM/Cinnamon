package de.kiaim.cinnamon.test.platform.processor;

import de.kiaim.cinnamon.platform.model.entity.FhirFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.file.FhirFileConfiguration;
import de.kiaim.cinnamon.platform.processor.FhirProcessor;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.ResourceHelper;
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

		var fhirFileConfiguration = new FhirFileConfiguration("Patient");
		var fileConfiguration = new FhirFileConfigurationEntity(fhirFileConfiguration);

		var estimation = assertDoesNotThrow(
				() -> fhirProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()),
				                                              fileConfiguration,
				                                              DatatypeEstimationAlgorithm.MOST_ESTIMATED));
		var data = assertDoesNotThrow(
				() -> fhirProcessor.read(new ByteArrayInputStream(bundle.getBytes()), fileConfiguration,
				                         estimation.getDataConfiguration()));

		assertEquals(1, data.getDataSet().getDataRows().size());
		assertEquals(0, data.getTransformationErrors().size());
	}

	@Test
	public void estimateDataConfiguration() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();

		var fhirFileConfiguration = new FhirFileConfiguration("Patient");
		var fileConfiguration = new FhirFileConfigurationEntity(fhirFileConfiguration);

		var estimation = assertDoesNotThrow(
				() -> fhirProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()),
				                                              fileConfiguration,
				                                              DatatypeEstimationAlgorithm.MOST_ESTIMATED));

		assertEquals(13, estimation.getDataConfiguration().getConfigurations().size());
	}
}
