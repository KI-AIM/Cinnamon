package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.configuration.data.ImportConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.enumeration.*;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.exception.BadConfigurationFileException;
import de.kiaim.cinnamon.platform.exception.BadDataConfigurationException;
import de.kiaim.cinnamon.platform.exception.BadDataSetIdException;
import de.kiaim.cinnamon.platform.model.dto.AttributeImportOverview;
import de.kiaim.cinnamon.platform.model.dto.AttributeMatchingRequest;
import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.model.dto.ConfigurationImportSummary;
import de.kiaim.cinnamon.platform.service.ConfigurationService;
import de.kiaim.cinnamon.platform.model.dto.ProjectExportParameter;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.service.DatabaseService;
import de.kiaim.cinnamon.platform.service.ExportService;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.test.platform.DatabaseTest;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.FileConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the matching of uploaded attribute configurations against the attributes in the data.
 * The test file contains the attributes column0_boolean to column5_string.
 */
class AttributeMatchingTest extends DatabaseTest {

	private static final List<String> ALL_COLUMNS = List.of("column0_boolean", "column1_date", "column2_date_time",
	                                                        "column3_decimal", "column4_integer", "column5_string");

	@Autowired
	DatabaseService databaseService;

	@Autowired
	ProjectService projectService;

	@Autowired
	ConfigurationService configurationService;

	@Autowired
	ExportService exportService;

	@BeforeEach
	public void setUp() throws IOException, ApiException {
		projectService.setMode(testProject, Mode.EXPERT);
		databaseService.storeFileConfiguration(testProject, FileConfigurationTestHelper.generateFileConfiguration());
		databaseService.storeFile(testProject, ResourceHelper.loadCsvFile());
	}

	@Test
	void matchByName() throws ApiException {
		// Shuffled order and wrong indices must be corrected
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.forEach(c -> c.setIndex(42));
		final ColumnConfiguration first = attributes.remove(0);
		attributes.add(first);

		databaseService.storeImportConfiguration(testProject, importConfiguration(
				attributes, AttributeMatchingStrategy.NAME, HandleConfigOnlyStrategy.ERROR,
				HandleDataOnlyStrategy.ERROR));

		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(ALL_COLUMNS, names(overview.getImportConfiguration().getAttributes()));
		assertEquals(List.of(0, 1, 2, 3, 4, 5), indices(overview.getImportConfiguration().getAttributes()));
		assertTrue(overview.getConfigOnlyAttributes().isEmpty());
		assertTrue(overview.getDataOnlyAttributes().isEmpty());
	}

	@Test
	void matchByIndex() throws ApiException {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.forEach(c -> c.setName("wrong_" + c.getIndex()));

		databaseService.storeImportConfiguration(testProject, importConfiguration(
				attributes, AttributeMatchingStrategy.INDEX, HandleConfigOnlyStrategy.ERROR,
				HandleDataOnlyStrategy.ERROR));

		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(ALL_COLUMNS, names(overview.getImportConfiguration().getAttributes()));
	}

	@Test
	void ignoreConfigOnlyAndKeepDataOnly() throws ApiException {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.remove(5);
		attributes.add(new ColumnConfiguration(5, "extra", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));

		databaseService.storeImportConfiguration(testProject, importConfiguration(
				attributes, AttributeMatchingStrategy.NAME, HandleConfigOnlyStrategy.IGNORE,
				HandleDataOnlyStrategy.REMOVE_ATTRIBUTE));

		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(ALL_COLUMNS.subList(0, 5), names(overview.getImportConfiguration().getAttributes()));
		assertEquals(List.of("extra"), names(overview.getConfigOnlyAttributes()));
		assertEquals(1, overview.getDataOnlyAttributes().size());
		assertEquals(5, overview.getDataOnlyAttributes().get(0).getIndex());
		assertEquals("column5_string", overview.getDataOnlyAttributes().get(0).getName());
	}

	@Test
	void exportExcludesUnmatched() throws ApiException {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.add(new ColumnConfiguration(6, "extra", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));

		databaseService.storeImportConfiguration(testProject, importConfiguration(
				attributes, AttributeMatchingStrategy.NAME, HandleConfigOnlyStrategy.IGNORE,
				HandleDataOnlyStrategy.ERROR));

		final ProjectEntity project = getTestProject();
		assertEquals(ALL_COLUMNS, databaseService.exportOriginalDataConfiguration(project).getColumnNames());
		assertEquals(ALL_COLUMNS, names(databaseService.exportImportConfiguration(project).getAttributes()));
	}

	@Test
	void configOnlyError() {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.add(new ColumnConfiguration(6, "extra", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));

		final var exception = assertThrows(BadDataConfigurationException.class,
		                                   () -> databaseService.storeImportConfiguration(testProject, importConfiguration(
				                                   attributes, AttributeMatchingStrategy.NAME,
				                                   HandleConfigOnlyStrategy.ERROR, HandleDataOnlyStrategy.ERROR)));
		assertEquals(ApiException.assembleErrorCode(ApiException.BAD_REQUEST,
		                                            BadDataConfigurationException.DATA_CONFIGURATION,
		                                            BadDataConfigurationException.ATTRIBUTE_MISMATCH),
		             exception.getErrorCode());
	}

	@Test
	void dataOnlyError() {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.remove(5);

		assertThrows(BadDataConfigurationException.class,
		             () -> databaseService.storeImportConfiguration(testProject, importConfiguration(
				             attributes, AttributeMatchingStrategy.NAME, HandleConfigOnlyStrategy.IGNORE,
				             HandleDataOnlyStrategy.ERROR)));
	}

	@Test
	void estimateDataOnly() throws ApiException {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.remove(5);
		attributes.remove(2);

		databaseService.storeImportConfiguration(testProject, importConfiguration(
				attributes, AttributeMatchingStrategy.NAME, HandleConfigOnlyStrategy.ERROR,
				HandleDataOnlyStrategy.ESTIMATE_CONFIG));

		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(ALL_COLUMNS, names(overview.getImportConfiguration().getAttributes()));
		assertTrue(overview.getDataOnlyAttributes().isEmpty());
	}

	@Test
	void rematchAfterFileChange() throws ApiException, IOException {
		// Upload a file without the last attribute
		final String reducedCsv = """
				column0_boolean,column1_date,column2_date_time,column3_decimal,column4_integer
				true,2023-11-20,2023-11-20T12:50:27.123456,4.2,42
				""";
		databaseService.storeFile(testProject, new MockMultipartFile("file", "file.csv", null,
		                                                             reducedCsv.getBytes(StandardCharsets.UTF_8)));

		databaseService.storeImportConfiguration(getTestProject(), importConfiguration(
				estimatedAttributes(), AttributeMatchingStrategy.NAME, HandleConfigOnlyStrategy.IGNORE,
				HandleDataOnlyStrategy.ERROR));

		AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(ALL_COLUMNS.subList(0, 5), names(overview.getImportConfiguration().getAttributes()));
		assertEquals(List.of("column5_string"), names(overview.getConfigOnlyAttributes()));

		// Upload the full file, the previously unmatched attribute must be matched again
		databaseService.storeFile(getTestProject(), ResourceHelper.loadCsvFile());

		overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(ALL_COLUMNS, names(overview.getImportConfiguration().getAttributes()));
		assertTrue(overview.getConfigOnlyAttributes().isEmpty());
		assertTrue(overview.getDataOnlyAttributes().isEmpty());
	}

	@Test
	void nameMatchingWithoutHeaderFallsBackToIndex() throws ApiException {
		databaseService.storeFileConfiguration(testProject, FileConfigurationTestHelper.generateFileConfiguration(false));

		databaseService.storeImportConfiguration(getTestProject(), importConfiguration(
				estimatedAttributes(), AttributeMatchingStrategy.NAME, HandleConfigOnlyStrategy.ERROR,
				HandleDataOnlyStrategy.ERROR));

		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(ALL_COLUMNS, names(overview.getImportConfiguration().getAttributes()));
	}

	@Test
	void missingAttributesContainSuggestion() throws ApiException {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.remove(3);

		databaseService.storeImportConfiguration(testProject, importConfiguration(
				attributes, AttributeMatchingStrategy.NAME, HandleConfigOnlyStrategy.ERROR,
				HandleDataOnlyStrategy.REMOVE_ATTRIBUTE));

		final var missing = databaseService.getAttributeImportOverview(getTestProject()).getDataOnlyAttributes();
		assertEquals(1, missing.size());
		assertEquals(3, missing.get(0).getIndex());
		assertNotNull(missing.get(0).getSuggestedConfiguration());
		assertEquals(DataType.DECIMAL, missing.get(0).getSuggestedConfiguration().getType());
		assertNotNull(missing.get(0).getConfidence());
	}

	@Test
	void storeAttributeMatching() throws ApiException {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		final ColumnConfiguration removed = attributes.remove(4);
		final ColumnConfiguration ignored = new ColumnConfiguration(9, "ignored", DataType.STRING, DataScale.NOMINAL,
		                                                            new ArrayList<>());
		// Order must not matter
		attributes.add(0, attributes.remove(2));

		final AttributeMatchingRequest request = new AttributeMatchingRequest();
		request.setAttributes(attributes);
		request.setUnmatchedAttributes(List.of(ignored));

		final AttributeImportOverview overview = databaseService.storeAttributeMatching(testProject, request);
		assertEquals(List.of(0, 1, 2, 3, 5), indices(overview.getImportConfiguration().getAttributes()));
		assertEquals(List.of("ignored"), names(overview.getConfigOnlyAttributes()));
		assertEquals(1, overview.getDataOnlyAttributes().size());
		assertEquals(removed.getIndex(), overview.getDataOnlyAttributes().get(0).getIndex());

		// Persisted
		final AttributeImportOverview reloaded = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(List.of(0, 1, 2, 3, 5), indices(reloaded.getImportConfiguration().getAttributes()));
		assertEquals(List.of("ignored"), names(reloaded.getConfigOnlyAttributes()));
	}

	@Test
	void storeAttributeMatchingIndexOutOfRange() {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.add(new ColumnConfiguration(6, "extra", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));

		final AttributeMatchingRequest request = new AttributeMatchingRequest();
		request.setAttributes(attributes);

		final var exception = assertThrows(BadDataConfigurationException.class,
		                                   () -> databaseService.storeAttributeMatching(testProject, request));
		assertEquals(ApiException.assembleErrorCode(ApiException.BAD_REQUEST,
		                                            BadDataConfigurationException.DATA_CONFIGURATION,
		                                            BadDataConfigurationException.ATTRIBUTE_MISMATCH),
		             exception.getErrorCode());
	}

	@Test
	void storeAttributeMatchingConfirmed() throws ApiException {
		final ProjectEntity project = getTestProject();
		final var dataSet = new DataSetEntity(project.getOriginalData());
		dataSet.setConfirmedData(true);

		final AttributeMatchingRequest request = new AttributeMatchingRequest();
		request.setAttributes(estimatedAttributes());

		assertThrows(BadDataSetIdException.class, () -> databaseService.storeAttributeMatching(project, request));
	}

	@Test
	void importDataConfigurationFromFileMatchesAttributes() throws ApiException {
		// Same as the upload page: only the data configuration of the file is imported
		final String configuration = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				- index: 1
				  name: "unknown_a"
				  type: "STRING"
				  scale: "NOMINAL"
				  configurations: []
				- index: 2
				  name: "unknown_b"
				  type: "INTEGER"
				  scale: "INTERVAL"
				  configurations: []
				""";
		final ConfigurationImportParameters parameters = new ConfigurationImportParameters();
		parameters.setConfigurationsToImport(Set.of("configurations"));

		configurationService.importConfigurations(getTestProject(), new MockMultipartFile(
				"configuration", "config.yaml", "text/yaml", configuration.getBytes(StandardCharsets.UTF_8)), parameters);

		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(List.of("column0_boolean"), names(overview.getImportConfiguration().getAttributes()));
		assertEquals(List.of("unknown_a", "unknown_b"), names(overview.getConfigOnlyAttributes()));
		assertEquals(5, overview.getDataOnlyAttributes().size());
	}

	@Test
	void importImportConfigurationFromFile() throws ApiException {
		final String configuration = """
				import:
				  attributeMatching: "NAME"
				  handleConfigOnly: "IGNORE"
				  handleDataOnly: "REMOVE_ATTRIBUTE"
				  attributes:
				  - index: 0
				    name: "column0_boolean"
				    type: "BOOLEAN"
				    scale: "NOMINAL"
				    configurations: []
				  - index: 1
				    name: "unknown_a"
				    type: "STRING"
				    scale: "NOMINAL"
				    configurations: []
				""";

		importConfigurationFile(configuration, "import");

		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(List.of("column0_boolean"), names(overview.getImportConfiguration().getAttributes()));
		assertEquals(List.of("unknown_a"), names(overview.getConfigOnlyAttributes()));
		assertEquals(5, overview.getDataOnlyAttributes().size());
	}

	@Test
	void importLegacyDataConfigurationWhenSelectingImport() throws ApiException {
		final String configuration = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				""";

		final var summary = importConfigurationFile(configuration, "import");

		assertEquals(ConfigurationImportSummary.ConfigurationImportStatus.SUCCESS, summary.getStatus());
		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(List.of("column0_boolean"), names(overview.getImportConfiguration().getAttributes()));
	}

	@Test
	void importFileWithImportAndLegacyDataConfiguration() {
		final String configuration = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				import:
				  attributes: []
				""";

		final var exception = assertThrows(BadConfigurationFileException.class,
		                                   () -> importConfigurationFile(configuration, "import"));
		assertEquals(ApiException.assembleErrorCode(ApiException.BAD_REQUEST,
		                                            BadConfigurationFileException.CONFIGURATION_FILE,
		                                            BadConfigurationFileException.DUPLICATE_DATA_CONFIGURATION),
		             exception.getErrorCode());
	}

	@Test
	void exportUsesImportConfiguration() throws Exception {
		final List<ColumnConfiguration> attributes = estimatedAttributes();
		attributes.add(new ColumnConfiguration(6, "extra", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
		databaseService.storeImportConfiguration(testProject, importConfiguration(
				attributes, AttributeMatchingStrategy.INDEX, HandleConfigOnlyStrategy.IGNORE,
				HandleDataOnlyStrategy.ERROR));

		final List<String> availableResources = exportService.getAvailableExportResources(getTestProject());
		assertTrue(availableResources.contains("configuration.import"), availableResources.toString());
		assertFalse(availableResources.contains("configuration.configurations"), availableResources.toString());

		final var out = new ByteArrayOutputStream();
		exportService.createZipFile(getTestProject(), out, new ProjectExportParameter(
				false, FileType.CSV, HoldOutSelector.ALL, List.of("configuration.import")));

		final Map<String, String> entries = new HashMap<>();
		try (final var zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
			}
		}

		assertFalse(entries.containsKey("configurations.yaml"), "The legacy data configuration must not be exported!");
		assertTrue(entries.containsKey("import.yaml"), "The import configuration is missing!");
		final String exported = entries.get("import.yaml");
		assertTrue(exported.startsWith("import:"), exported);
		assertTrue(exported.contains("attributeMatching: \"INDEX\""), exported);
		assertFalse(exported.contains("extra"), "Unmatched attributes must not be exported!");

		// The exported configuration can be imported again
		importConfigurationFile(exported, "import");
		final AttributeImportOverview overview = databaseService.getAttributeImportOverview(getTestProject());
		assertEquals(ALL_COLUMNS, names(overview.getImportConfiguration().getAttributes()));
		assertTrue(overview.getConfigOnlyAttributes().isEmpty());
	}

	private ConfigurationImportSummary importConfigurationFile(final String configuration,
	                                                           final String configurationToImport)
			throws BadConfigurationFileException {
		final ConfigurationImportParameters parameters = new ConfigurationImportParameters();
		parameters.setConfigurationsToImport(Set.of(configurationToImport));
		return configurationService.importConfigurations(getTestProject(), new MockMultipartFile(
				"configuration", "config.yaml", "text/yaml", configuration.getBytes(StandardCharsets.UTF_8)), parameters);
	}

	private List<ColumnConfiguration> estimatedAttributes() {
		return new ArrayList<>(DataConfigurationTestHelper.generateEstimatedConfiguration().getConfigurations());
	}

	private ImportConfigurationDTO importConfiguration(final List<ColumnConfiguration> attributes,
	                                                  final AttributeMatchingStrategy matching,
	                                                  final HandleConfigOnlyStrategy configOnly,
	                                                  final HandleDataOnlyStrategy dataOnly) {
		final ImportConfigurationDTO dto = new ImportConfigurationDTO();
		dto.setAttributes(attributes);
		dto.setAttributeMatching(matching);
		dto.setHandleConfigOnly(configOnly);
		dto.setHandleDataOnly(dataOnly);
		return dto;
	}

	private List<String> names(final List<ColumnConfiguration> attributes) {
		return attributes.stream().map(ColumnConfiguration::getName).toList();
	}

	private List<Integer> indices(final List<ColumnConfiguration> attributes) {
		return attributes.stream().map(ColumnConfiguration::getIndex).toList();
	}
}
