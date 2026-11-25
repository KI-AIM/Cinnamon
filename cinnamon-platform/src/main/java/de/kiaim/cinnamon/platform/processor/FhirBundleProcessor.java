package de.kiaim.cinnamon.platform.processor;

import ca.uhn.fhir.context.FhirContext;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.BadDataConfigurationException;
import de.kiaim.cinnamon.platform.exception.BadDatasetException;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.FhirTable;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.dto.FileConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.model.configuration.data.file.FhirBundleFileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Class for processing FHIR bundles.
 * Reading a FHIR bundle flattens the resources of the configured type into a {@link FhirTable}.
 * Writing a FHIR bundle converts the data set into a CSV string using the {@link CsvProcessor},
 * because the library only supports converting CSV into FHIR.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class FhirBundleProcessor extends CommonDataProcessor implements DataProcessor {

	private final CsvProcessor csvProcessor;
	private final FhirContext fhirContext;
	private final FhirTransformers fhirTransformers;

	public FhirBundleProcessor(final CsvProcessor csvProcessor, final FhirContext fhirContext,
	                           final FhirTransformers fhirTransformers) {
		this.csvProcessor = csvProcessor;
		this.fhirContext = fhirContext;
		this.fhirTransformers = fhirTransformers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FileType getSupportedDataType() {
		return FileType.FHIR_BUNDLE;
	}

	@Override
	public void checkFileCompatibility(final LobWrapperEntity data, final FileCompatibilityEntity fileCompatibility) {
		final Set<String> resourceTypes;
		try {
			resourceTypes = getRessourceTypes(data.getLobString());
		} catch (final Exception ignored) {
			return;
		}

		fileCompatibility.getCompatibleFileTypes().add(FileType.FHIR_BUNDLE);
		fileCompatibility.setFhirResourceTypes(resourceTypes);
	}

	@Override
	public FileConfigurationEstimation estimateFileConfiguration(final LobWrapperEntity data,
	                                                             final FileCompatibilityEntity fileCompatibility) {
		final var fhirFileConfiguration = new FhirBundleFileConfiguration();
		if (fileCompatibility.getFhirResourceTypes() != null && fileCompatibility.getFhirResourceTypes().size() == 1) {
			fhirFileConfiguration.setResourceType(fileCompatibility.getFhirResourceTypes().iterator().next());
		}

		final var fileConfiguration = new FileConfiguration();

		fileConfiguration.setFileType(FileType.FHIR_BUNDLE);
		fileConfiguration.setFhirBundleFileConfiguration(fhirFileConfiguration);

		return new FileConfigurationEstimation(fileConfiguration);
	}

	/**
	 * {@inheritDoc}
	 * Validates that column names match the paths of the FHIR bundle.
	 */
	@Override
	public void validateFileAndConfig(FileEntity file, DataConfiguration dataConfiguration)
			throws BadDataConfigurationException, InternalIOException {
		final var fileData = Objects.requireNonNull(file.getFile());
		final var fileConfiguration = Objects.requireNonNull(file.getFileConfiguration());

		final List<String> expectedColumns = getAttributeNames(fileData.getLobStream(), fileConfiguration);

		compareColumnNames(dataConfiguration, expectedColumns);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberColumns(final InputStream data, final FileConfigurationEntity fileConfiguration
	) throws InternalIOException {
		final FhirBundleFileConfigurationEntity fhirFileConfiguration = (FhirBundleFileConfigurationEntity) fileConfiguration;
		if (fhirFileConfiguration.getResourceType() == null) {
			// The resource type can be null after the estimation
			return 0;
		}

		return getTable(data, fhirFileConfiguration).columnNames().size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransformationResult read(final InputStream data,
	                                 final FileConfigurationEntity fileConfiguration,
	                                 final DataConfiguration configuration
	) throws BadDatasetException, InternalIOException {
		return read(getTable(data, (FhirBundleFileConfigurationEntity) fileConfiguration), configuration);
	}

	/**
	 * Transforms the rows of the given table into a data set.
	 *
	 * @param table         The flattened FHIR resources.
	 * @param configuration The data configuration.
	 * @return The transformation result.
	 * @throws BadDatasetException If the number of values in a row does not match the configuration.
	 */
	public TransformationResult read(final FhirTable table, final DataConfiguration configuration)
			throws BadDatasetException {
		final List<DataRow> dataRows = new ArrayList<>();
		final List<DataRowTransformationError> errors = new ArrayList<>();
		for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
			transformRow(table.rows().get(rowIndex), rowIndex, configuration, dataRows, errors);
		}

		return new TransformationResult(new DataSet(dataRows, configuration), errors);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DataConfigurationEstimation estimateDataConfiguration(
			final InputStream data,
			final FileConfigurationEntity fileConfiguration,
			final DatatypeEstimationAlgorithm algorithm
	) throws InternalIOException {
		return estimateDataConfiguration(getTable(data, (FhirBundleFileConfigurationEntity) fileConfiguration),
		                                 algorithm);
	}

	/**
	 * Estimates the data configuration for the given table.
	 *
	 * @param table     The flattened FHIR resources.
	 * @param algorithm Algorithm how to select the datatype of a column.
	 * @return The estimated data configuration.
	 */
	public DataConfigurationEstimation estimateDataConfiguration(final FhirTable table,
	                                                             final DatatypeEstimationAlgorithm algorithm) {
		final int numberColumns = table.columnNames().size();
		if (numberColumns == 0) {
			return new DataConfigurationEstimation(new DataConfiguration(), new float[0]);
		}

		final List<List<String>> samples = getAttributeSamples(table.rows().iterator(), numberColumns);
		return estimateDataConfiguration(samples, algorithm, numberColumns, table.columnNames());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(OutputStream outputStream, DataSet dataset) throws InternalIOException {
		// First, convert the dataset into a CSV string
		final OutputStream csvOutputStream = new ByteArrayOutputStream();
		// Keep time zones, as FHIR requires them for instants and date-times with a time
		csvProcessor.write(csvOutputStream, dataset, true);
		String csvString = csvOutputStream.toString();

		final Bundle bundle = fhirTransformers.parseCsvToBundle(csvString);

		try {
			fhirContext.newJsonParser()
			           .setPrettyPrint(true)
			           .encodeResourceToWriter(bundle, new OutputStreamWriter(outputStream));
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.FHIR_WRITING, "Failed to write FHIR bundle.", e);
		}
	}

	/**
	 * Returns the column names containing the FHIR paths from the bundle of the specified resource.
	 *
	 * @param data              The FHIR bundle.
	 * @param fileConfiguration The file configuration.
	 * @return List of column names.
	 * @throws InternalIOException If reading the FHIR bundle failed.
	 */
	public List<String> getAttributeNames(final InputStream data,
	                                      final FileConfigurationEntity fileConfiguration) throws InternalIOException {
		return getTable(data, (FhirBundleFileConfigurationEntity) fileConfiguration).columnNames();
	}

	/**
	 * Compares the column names of the given DataConfiguration with the given expected ones.
	 * Throws an exception if they do not match.
	 *
	 * @param dataConfiguration   The DataConfiguration to be compared.
	 * @param expectedColumnNames The column names to compare against.
	 * @throws BadDataConfigurationException If the column names do not match.
	 */
	public void compareColumnNames(final DataConfiguration dataConfiguration, final List<String> expectedColumnNames)
			throws BadDataConfigurationException {
		for (int i = 0; i < dataConfiguration.getConfigurations().size(); i++) {
			final String columnName = dataConfiguration.getConfigurations().get(i).getName();
			final String fhirColumnName = expectedColumnNames.get(i);
			if (!Objects.equals(columnName, fhirColumnName)) {
				throw new BadDataConfigurationException(BadDataConfigurationException.FHIR_ATTRIBUTE_MISMATCH,
				                                        "Attribute number " + (i + 1) + " with name '" + columnName +
				                                        "' does not match the column name of the FHIR bundle '" +
				                                        fhirColumnName + "'");
			}
		}
	}

	/**
	 * Returns the resource types contained in the FHIR bundle.
	 * If the FHIR bundle is invalid, an exception is thrown.
	 *
	 * @param data The FHIR bundle.
	 * @return Set of resource types.
	 */
	public Set<String> getRessourceTypes(final String data) {
		return fhirTransformers.getResourceTypesInBundle(data);
	}

	/**
	 * Flattens the resources of the given type in the FHIR bundle into a table.
	 *
	 * @param bundle       The FHIR bundle as JSON.
	 * @param resourceType The resource type to be included in the table.
	 * @return The table.
	 */
	public FhirTable getTable(final String bundle, final String resourceType) {
		return fhirTransformers.processBundle(bundle, resourceType);
	}

	/**
	 * Reads the given FHIR bundle and flattens the resources of the configured type into a table.
	 *
	 * @param fhirBundle        The FHIR bundle.
	 * @param fileConfiguration The FHIR configuration for importing the FHIR bundle.
	 * @return The table.
	 * @throws InternalIOException If reading the FHIR bundle failed.
	 */
	private FhirTable getTable(final InputStream fhirBundle, final FhirBundleFileConfigurationEntity fileConfiguration)
			throws InternalIOException {
		final String content;
		try {
			content = new String(fhirBundle.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new InternalIOException(InternalIOException.FHIR_BUNDLE_READING, "Failed to read the FHIR bundle.", e);
		}

		return getTable(content, fileConfiguration.getResourceType());
	}
}
