package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.configuration.data.ImportConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.enumeration.*;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.exception.BadDataConfigurationException;
import de.kiaim.cinnamon.platform.model.dto.AttributeImportOverview;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.service.DatabaseService;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.test.platform.DatabaseTest;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.FileConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
