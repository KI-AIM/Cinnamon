package de.kiaim.cinnamon.test.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.model.entity.FhirFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileCompatibilityEntity;
import de.kiaim.cinnamon.platform.model.entity.LobWrapperEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.model.configuration.data.file.FhirFileConfiguration;
import de.kiaim.cinnamon.platform.processor.FhirProcessor;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class FhirProcessorTest extends ContextRequiredTest {

	@Autowired private FhirProcessor fhirProcessor;

	@Test
	public void checkFileCompatibility() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();
		var lobWrapper = new LobWrapperEntity(bundle);

		var fileCompatibility = new FileCompatibilityEntity();
		assertDoesNotThrow(() -> fhirProcessor.checkFileCompatibility(lobWrapper, fileCompatibility));

		assertTrue(fileCompatibility.getCompatibleFileTypes().contains(FileType.FHIR));
		assertNotNull(fileCompatibility.getFhirResourceTypes());
		assertEquals(2, fileCompatibility.getFhirResourceTypes().size());
		assertTrue(fileCompatibility.getFhirResourceTypes().contains("Patient"));
		assertTrue(fileCompatibility.getFhirResourceTypes().contains("Observation"));
	}

	@Test
	public void estimateFileConfiguration() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();
		var lobWrapper = new LobWrapperEntity(bundle);
		var fileCompatibility = getFileCompatibility();

		var estimation = assertDoesNotThrow(
				() -> fhirProcessor.estimateFileConfiguration(lobWrapper, fileCompatibility));

		assertEquals(FileType.FHIR, estimation.getEstimation().getFileType());
	}

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

	@Test
	public void write() throws IOException {
		var bundle = ResourceHelper.loadFileAsString("fhir-bundle-patient.json");

		var fhirFileConfiguration = new FhirFileConfiguration("Patient");
		var fileConfiguration = new FhirFileConfigurationEntity(fhirFileConfiguration);

		var estimation = assertDoesNotThrow(
				() -> fhirProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()),
				                                              fileConfiguration,
				                                              DatatypeEstimationAlgorithm.MOST_ESTIMATED));
		var data = assertDoesNotThrow(
				() -> fhirProcessor.read(new ByteArrayInputStream(bundle.getBytes()), fileConfiguration,
				                         estimation.getDataConfiguration()));
		DataSet dataset = data.getDataSet();

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		assertDoesNotThrow(() -> fhirProcessor.write(stream, dataset));

		String content = stream.toString(StandardCharsets.UTF_8);
		String expectedContent = ResourceHelper.loadFileAsString("fhir-bundle-patient.json").replaceAll(",2023-11-20,", ",20.11.2023,");
		assertEquals(expectedContent, content);
	}

	private static FileCompatibilityEntity getFileCompatibility() {
		FileCompatibilityEntity compatibility = new FileCompatibilityEntity();
		compatibility.getCompatibleFileTypes().add(FileType.FHIR);
		compatibility.setFhirResourceTypes(Set.of("Patient", "Observation"));
		return compatibility;
	}
}
