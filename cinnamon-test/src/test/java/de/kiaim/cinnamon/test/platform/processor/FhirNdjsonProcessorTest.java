package de.kiaim.cinnamon.test.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.platform.exception.BadDataConfigurationException;
import de.kiaim.cinnamon.platform.model.entity.FhirNdjsonFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileCompatibilityEntity;
import de.kiaim.cinnamon.platform.model.entity.FileEntity;
import de.kiaim.cinnamon.platform.model.entity.LobWrapperEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.processor.FhirNdjsonProcessor;
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

public class FhirNdjsonProcessorTest extends ContextRequiredTest {

	@Autowired private FhirNdjsonProcessor fhirNdjsonProcessor;

	@Test
	public void checkFileCompatibility() throws IOException {
		var ndjson = ResourceHelper.loadFileAsString("observations.ndjson");
		var lobWrapper = new LobWrapperEntity(ndjson);

		var fileCompatibility = new FileCompatibilityEntity();
		assertDoesNotThrow(() -> fhirNdjsonProcessor.checkFileCompatibility(lobWrapper, fileCompatibility));

		assertTrue(fileCompatibility.getCompatibleFileTypes().contains(FileType.FHIR_NDJSON));
		assertEquals(Set.of("Observation"), fileCompatibility.getFhirResourceTypes());
	}

	@Test
	public void checkFileCompatibilityInvalid() {
		var lobWrapper = new LobWrapperEntity("a,b,c\n1,2,3\n");

		var fileCompatibility = new FileCompatibilityEntity();
		assertDoesNotThrow(() -> fhirNdjsonProcessor.checkFileCompatibility(lobWrapper, fileCompatibility));

		assertFalse(fileCompatibility.getCompatibleFileTypes().contains(FileType.FHIR_NDJSON));
	}

	@Test
	public void readAndWrite() throws IOException {
		var ndjson = ResourceHelper.loadFileAsString("observations.ndjson");
		var fileConfiguration = new FhirNdjsonFileConfigurationEntity();

		var numberColumns = assertDoesNotThrow(
				() -> fhirNdjsonProcessor.getNumberColumns(toStream(ndjson), fileConfiguration));
		assertTrue(numberColumns > 0);

		// MOST_GENERAL because valueQuantity.value contains as many integers as decimals in the sampled rows
		var estimation = assertDoesNotThrow(
				() -> fhirNdjsonProcessor.estimateDataConfiguration(toStream(ndjson), fileConfiguration,
				                                                    DatatypeEstimationAlgorithm.MOST_GENERAL));
		assertEquals(numberColumns, estimation.getDataConfiguration().getConfigurations().size());

		var data = assertDoesNotThrow(
				() -> fhirNdjsonProcessor.read(toStream(ndjson), fileConfiguration,
				                               estimation.getDataConfiguration()));
		assertEquals(20, data.getDataSet().getDataRows().size());
		assertEquals(0, data.getTransformationErrors().size());

		var output = new ByteArrayOutputStream();
		assertDoesNotThrow(() -> fhirNdjsonProcessor.write(output, data.getDataSet()));

		var lines = output.toString(StandardCharsets.UTF_8).lines().toList();
		assertEquals(20, lines.size());
		assertTrue(lines.stream().allMatch(line -> line.startsWith("{\"resourceType\":\"Observation\"")));
	}

	@Test
	public void validateFileAndConfig() throws IOException {
		var ndjson = ResourceHelper.loadFileAsString("observations.ndjson");
		var fileConfiguration = new FhirNdjsonFileConfigurationEntity();

		var estimation = assertDoesNotThrow(
				() -> fhirNdjsonProcessor.estimateDataConfiguration(toStream(ndjson), fileConfiguration,
				                                                    DatatypeEstimationAlgorithm.MOST_GENERAL));

		var file = createFileEntity(ndjson, fileConfiguration);
		assertDoesNotThrow(() -> fhirNdjsonProcessor.validateFileAndConfig(file, estimation.getDataConfiguration()));
	}

	@Test
	public void validateFileAndConfigColumnNameMismatch() throws IOException {
		var ndjson = ResourceHelper.loadFileAsString("observations.ndjson");
		var fileConfiguration = new FhirNdjsonFileConfigurationEntity();

		var estimation = assertDoesNotThrow(
				() -> fhirNdjsonProcessor.estimateDataConfiguration(toStream(ndjson), fileConfiguration,
				                                                    DatatypeEstimationAlgorithm.MOST_GENERAL));
		var dataConfiguration = estimation.getDataConfiguration();
		dataConfiguration.getConfigurations().get(0).setName("renamedColumn");

		var file = createFileEntity(ndjson, fileConfiguration);
		var exception = assertThrows(BadDataConfigurationException.class,
		                             () -> fhirNdjsonProcessor.validateFileAndConfig(file, dataConfiguration));
		assertEquals("PLATFORM_1_9_3", exception.getErrorCode(), "Unexpected error code!");
	}

	private static FileEntity createFileEntity(final String content,
	                                           final FhirNdjsonFileConfigurationEntity fileConfiguration) {
		var file = new FileEntity();
		file.setFile(new LobWrapperEntity(content));
		file.setFileConfiguration(fileConfiguration);
		return file;
	}

	private static ByteArrayInputStream toStream(final String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}
}
