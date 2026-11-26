package de.kiaim.cinnamon.test.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.platform.model.entity.FhirBundleFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileCompatibilityEntity;
import de.kiaim.cinnamon.platform.model.entity.LobWrapperEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.model.configuration.data.file.FhirBundleFileConfiguration;
import de.kiaim.cinnamon.platform.processor.FhirBundleProcessor;
import de.kiaim.cinnamon.platform.processor.FhirTransformers;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import de.unimuenster.imi.fhir.transform.ResultTable;
import de.unimuenster.imi.fhir.transform.SubTable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class FhirBundleProcessorTest extends ContextRequiredTest {

	@Autowired private FhirBundleProcessor fhirBundleProcessor;

	@Test
	public void checkFileCompatibility() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();
		var lobWrapper = new LobWrapperEntity(bundle);

		var fileCompatibility = new FileCompatibilityEntity();
		assertDoesNotThrow(() -> fhirBundleProcessor.checkFileCompatibility(lobWrapper, fileCompatibility));

		assertTrue(fileCompatibility.getCompatibleFileTypes().contains(FileType.FHIR_BUNDLE));
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
				() -> fhirBundleProcessor.estimateFileConfiguration(lobWrapper, fileCompatibility));

		assertEquals(FileType.FHIR_BUNDLE, estimation.getEstimation().getFileType());
	}

	@Test
	public void read() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();

		var fhirFileConfiguration = new FhirBundleFileConfiguration("Patient");
		var fileConfiguration = new FhirBundleFileConfigurationEntity(fhirFileConfiguration);

		var estimation = assertDoesNotThrow(
				() -> fhirBundleProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()),
				                                                    fileConfiguration,
				                                                    DatatypeEstimationAlgorithm.MOST_ESTIMATED));
		var data = assertDoesNotThrow(
				() -> fhirBundleProcessor.read(new ByteArrayInputStream(bundle.getBytes()), fileConfiguration,
				                               estimation.getDataConfiguration()));

		assertEquals(1, data.getDataSet().getDataRows().size());
		assertEquals(0, data.getTransformationErrors().size());
	}

	@Test
	public void estimateDataConfiguration() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();

		var fhirFileConfiguration = new FhirBundleFileConfiguration("Patient");
		var fileConfiguration = new FhirBundleFileConfigurationEntity(fhirFileConfiguration);

		var estimation = assertDoesNotThrow(
				() -> fhirBundleProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()),
				                                                    fileConfiguration,
				                                                    DatatypeEstimationAlgorithm.MOST_ESTIMATED));

		var dataConfiguration = estimation.getDataConfiguration();
		assertEquals(13, dataConfiguration.getConfigurations().size());

		// The data types are derived from the FHIR datatypes
		assertEquals(DataType.STRING, dataConfiguration.getColumnConfigurationByColumnName("Patient.id").getType());
		assertEquals(DataType.DATE_TIME,
		             dataConfiguration.getColumnConfigurationByColumnName("Patient.meta.lastUpdated").getType());
		assertEquals(DataType.STRING, dataConfiguration.getColumnConfigurationByColumnName("Patient.gender").getType());
		assertEquals(DataType.DATE, dataConfiguration.getColumnConfigurationByColumnName("Patient.birthDate").getType());
		// The narrative is free text
		assertEquals(DataType.TEXT, dataConfiguration.getColumnConfigurationByColumnName("Patient.text.div").getType());
	}

	@Test
	public void readAndWriteObservations() throws IOException {
		var bundle = ResourceHelper.loadFhirBundleAsString();

		var fileConfiguration = new FhirBundleFileConfigurationEntity(new FhirBundleFileConfiguration("Observation"));
		var estimation = assertDoesNotThrow(
				() -> fhirBundleProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()),
				                                                    fileConfiguration,
				                                                    DatatypeEstimationAlgorithm.MOST_ESTIMATED));
		var dataConfiguration = estimation.getDataConfiguration();
		assertEquals(DataType.DATE_TIME,
		             dataConfiguration.getColumnConfigurationByColumnName("Observation.effectiveDateTime").getType());
		assertEquals(DataType.DECIMAL,
		             dataConfiguration.getColumnConfigurationByColumnName("Observation.valueQuantity.value").getType());

		var data = assertDoesNotThrow(
				() -> fhirBundleProcessor.read(new ByteArrayInputStream(bundle.getBytes()), fileConfiguration,
				                               dataConfiguration));
		assertEquals(20, data.getDataSet().getDataRows().size());
		assertEquals(0, data.getTransformationErrors().size());

		var stream = new ByteArrayOutputStream();
		assertDoesNotThrow(() -> fhirBundleProcessor.write(stream, data.getDataSet()));

		// Reading the written bundle results in the same data set
		var written = stream.toString(StandardCharsets.UTF_8);
		var reread = assertDoesNotThrow(
				() -> fhirBundleProcessor.read(new ByteArrayInputStream(written.getBytes(StandardCharsets.UTF_8)),
				                               fileConfiguration, dataConfiguration));
		assertEquals(0, reread.getTransformationErrors().size());
		assertEquals(data.getDataSet().getDataRows(), reread.getDataSet().getDataRows());
	}

	@Test
	public void write() throws IOException {
		var bundle = ResourceHelper.loadFileAsString("fhir-bundle-patient.json");

		var fhirFileConfiguration = new FhirBundleFileConfiguration("Patient");
		var fileConfiguration = new FhirBundleFileConfigurationEntity(fhirFileConfiguration);

		var estimation = assertDoesNotThrow(
				() -> fhirBundleProcessor.estimateDataConfiguration(new ByteArrayInputStream(bundle.getBytes()),
				                                                    fileConfiguration,
				                                                    DatatypeEstimationAlgorithm.MOST_ESTIMATED));
		var data = assertDoesNotThrow(
				() -> fhirBundleProcessor.read(new ByteArrayInputStream(bundle.getBytes()), fileConfiguration,
				                               estimation.getDataConfiguration()));
		DataSet dataset = data.getDataSet();

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		assertDoesNotThrow(() -> fhirBundleProcessor.write(stream, dataset));

		String content = stream.toString(StandardCharsets.UTF_8);
		String expectedContent = ResourceHelper.loadFileAsString("fhir-bundle-patient.json")
		                                       .replace("\r\n", "\n");
		assertEquals(expectedContent, content);
	}

	@Test
	public void toFhirTableKeysColumnsByName() {
		// Column indices are counted per resource, so "status" has index 3 in the first and index 2 in the second
		var first = new SubTable();
		first.addColumn("id", "a");
		first.addColumn("code[0]", "x");
		first.addColumn("status", "active");
		var second = new SubTable();
		second.addColumn("id", "b");
		second.addColumn("status", "inactive");
		second.getData().put(new kotlin.Pair<>(3, "note"), Collections.singletonList(null));
		var empty = new SubTable();

		var table = FhirTransformers.toFhirTable(new ResultTable(List.of(first, second, empty)));

		assertEquals(List.of("id", "code[0]", "status", "note"), table.columnNames());
		assertEquals(2, table.rows().size());
		assertEquals(Arrays.asList("a", "x", "active", null), table.rows().get(0));
		assertEquals(Arrays.asList("b", null, "inactive", null), table.rows().get(1));
	}

	@Test
	public void readResourcesWithDifferentFields() {
		// The first patient has two identifiers and a birth date, the second one has a single identifier and none
		var bundle = """
				{"resourceType":"Bundle","type":"collection","entry":[
				  {"resource":{"resourceType":"Patient","id":"p1",
				    "identifier":[{"value":"a"},{"value":"b"}],"gender":"female","birthDate":"1990-01-01"}},
				  {"resource":{"resourceType":"Patient","id":"p2",
				    "identifier":[{"value":"c"}],"gender":"male"}}
				]}""";

		var fhirFileConfiguration = new FhirBundleFileConfiguration("Patient");
		var fileConfiguration = new FhirBundleFileConfigurationEntity(fhirFileConfiguration);

		var estimation = assertDoesNotThrow(
				() -> fhirBundleProcessor.estimateDataConfiguration(
						new ByteArrayInputStream(bundle.getBytes(StandardCharsets.UTF_8)), fileConfiguration,
						DatatypeEstimationAlgorithm.MOST_ESTIMATED));
		var dataConfiguration = estimation.getDataConfiguration();
		assertEquals(List.of("Patient.id", "Patient.identifier[0].value", "Patient.identifier[1].value",
		                     "Patient.gender", "Patient.birthDate"),
		             dataConfiguration.getColumnNames());

		var result = assertDoesNotThrow(
				() -> fhirBundleProcessor.read(new ByteArrayInputStream(bundle.getBytes(StandardCharsets.UTF_8)),
				                               fileConfiguration, dataConfiguration));
		var rows = result.getDataSet().getDataRows();
		assertEquals(2, rows.size());

		assertEquals(Arrays.asList("p1", "a", "b", "female", LocalDate.of(1990, 1, 1)),
		             rows.get(0).getData().stream().map(Data::getValue).toList());
		assertEquals(Arrays.asList("p2", "c", null, "male", null),
		             rows.get(1).getData().stream().map(Data::getValue).toList());
	}

	private static FileCompatibilityEntity getFileCompatibility() {
		FileCompatibilityEntity compatibility = new FileCompatibilityEntity();
		compatibility.getCompatibleFileTypes().add(FileType.FHIR_BUNDLE);
		compatibility.setFhirResourceTypes(Set.of("Patient", "Observation"));
		return compatibility;
	}
}
