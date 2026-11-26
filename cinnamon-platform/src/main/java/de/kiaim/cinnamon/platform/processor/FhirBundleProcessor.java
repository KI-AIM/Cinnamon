package de.kiaim.cinnamon.platform.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
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
import org.hl7.fhir.r4.model.Resource;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Class for processing FHIR bundles.
 * Reading a FHIR bundle flattens the resources of the configured type into a {@link FhirTable} using the
 * {@link FhirFlattener}. The data configuration is derived from the FHIR datatypes using the {@link FhirTypeMapper}.
 * Writing a FHIR bundle creates one resource per row by reversing the flattening.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class FhirBundleProcessor extends CommonDataProcessor implements DataProcessor {

	private final FhirContext fhirContext;
	private final FhirFlattener fhirFlattener;

	public FhirBundleProcessor(final FhirContext fhirContext, final FhirFlattener fhirFlattener) {
		this.fhirContext = fhirContext;
		this.fhirFlattener = fhirFlattener;
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
	 * Derives the data configuration for the given table from the FHIR datatypes of the columns.
	 * No estimation based on samples is performed, as the FHIR datatypes are fixed by the specification.
	 * Columns without a known FHIR datatype are configured as {@link de.kiaim.cinnamon.model.enumeration.DataType#STRING}.
	 *
	 * @param table     The flattened FHIR resources.
	 * @param algorithm Unused, the data types are derived from the FHIR datatypes.
	 * @return The data configuration. The confidence is 1 for all columns.
	 */
	public DataConfigurationEstimation estimateDataConfiguration(final FhirTable table,
	                                                             final DatatypeEstimationAlgorithm algorithm) {
		final int numberColumns = table.columnNames().size();
		final DataConfiguration dataConfiguration = new DataConfiguration();
		final float[] confidences = new float[numberColumns];

		for (int columnIndex = 0; columnIndex < numberColumns; columnIndex++) {
			final List<String> values = new ArrayList<>();
			for (final List<@Nullable String> row : table.rows()) {
				final String value = row.get(columnIndex);
				if (value != null && !value.isEmpty()) {
					values.add(value);
				}
			}

			final ColumnConfiguration columnConfiguration =
					FhirTypeMapper.toColumnConfiguration(table.fhirTypes().get(columnIndex), values);
			columnConfiguration.setIndex(columnIndex);
			columnConfiguration.setName(table.columnNames().get(columnIndex));
			columnConfiguration.setScale(Objects.requireNonNull(columnConfiguration.getType()).getDefaultScale());
			dataConfiguration.addColumnConfiguration(columnConfiguration);

			confidences[columnIndex] = 1.0f;
		}

		return new DataConfigurationEstimation(dataConfiguration, confidences);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(OutputStream outputStream, DataSet dataset) throws InternalIOException {
		final Bundle bundle = toBundle(dataset);

		try {
			final Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
			fhirContext.newJsonParser()
			           .setPrettyPrint(true)
			           .encodeResourceToWriter(bundle, writer);
			writer.flush();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.FHIR_WRITING, "Failed to write FHIR bundle.", e);
		}
	}

	/**
	 * Converts the data set into a bundle containing one resource per row.
	 *
	 * @param dataset The data set with the FHIR paths as column names.
	 * @return The FHIR bundle.
	 * @throws InternalIOException If the column names are not FHIR paths of a single resource type.
	 */
	public Bundle toBundle(final DataSet dataset) throws InternalIOException {
		final DataConfiguration dataConfiguration = dataset.getDataConfiguration();
		final List<String> columnNames = dataConfiguration.getColumnNames();
		final List<ColumnConfiguration> columnConfigurations = dataConfiguration.getConfigurations();

		final String resourceType;
		try {
			resourceType = FhirFlattener.getResourceType(columnNames);
		} catch (final IllegalArgumentException e) {
			throw new InternalIOException(InternalIOException.FHIR_WRITING, "Failed to write FHIR bundle.", e);
		}

		final Bundle bundle = new Bundle();
		for (final DataRow dataRow : dataset.getDataRows()) {
			final List<@Nullable String> values = new ArrayList<>(columnNames.size());
			for (int i = 0; i < columnNames.size(); i++) {
				values.add(FhirTypeMapper.toFhirValue(dataRow.getData().get(i), columnConfigurations.get(i)));
			}
			bundle.addEntry().setResource(fhirFlattener.unflatten(resourceType, columnNames, values));
		}

		return bundle;
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
	 * @throws DataFormatException If the data is not a valid FHIR bundle.
	 */
	public Set<String> getRessourceTypes(final String data) {
		final Set<String> resourceTypes = new LinkedHashSet<>();
		for (final Resource resource : parseBundle(data)) {
			resourceTypes.add(resource.fhirType());
		}
		return resourceTypes;
	}

	/**
	 * Flattens the resources of the given type in the FHIR bundle into a table.
	 *
	 * @param bundle       The FHIR bundle as JSON.
	 * @param resourceType The resource type to be included in the table.
	 * @return The table.
	 * @throws DataFormatException If the data is not a valid FHIR bundle.
	 */
	public FhirTable getTable(final String bundle, final String resourceType) {
		return fhirFlattener.flatten(parseBundle(bundle), resourceType);
	}

	/**
	 * Parses the FHIR bundle and returns the contained resources.
	 *
	 * @param bundle The FHIR bundle as JSON.
	 * @return The resources of the bundle entries.
	 * @throws DataFormatException If the data is not a valid FHIR bundle.
	 */
	private List<Resource> parseBundle(final String bundle) {
		final Bundle parsedBundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundle);
		final List<Resource> resources = new ArrayList<>();
		for (final Bundle.BundleEntryComponent entry : parsedBundle.getEntry()) {
			if (entry.hasResource()) {
				resources.add(entry.getResource());
			}
		}
		return resources;
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
