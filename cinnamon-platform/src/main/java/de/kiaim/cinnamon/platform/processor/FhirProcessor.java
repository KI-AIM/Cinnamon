package de.kiaim.cinnamon.platform.processor;

import ca.uhn.fhir.context.FhirContext;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.BadFileException;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.dto.FileConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.entity.CsvFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FhirFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.file.FhirFileConfiguration;
import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import de.kiaim.cinnamon.platform.model.file.FileType;
import de.unimuenster.imi.fhir.columns_parser.Column;
import de.unimuenster.imi.fhir.transform.BundleTransformer;
import de.unimuenster.imi.fhir.transform.ResourceExtractor;
import de.unimuenster.imi.fhir.transform.TransformationParameters;
import org.apache.commons.csv.CSVFormat;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Class for processing FHIR bundles.
 * Reading a FHIR bundle converts the bundle into a CSV string and uses the {@link CsvProcessor}.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class FhirProcessor implements DataProcessor {

	private final CsvProcessor csvProcessor;

	public FhirProcessor(final CsvProcessor csvProcessor) {
		this.csvProcessor = csvProcessor;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FileType getSupportedDataType() {
		return FileType.FHIR;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FileConfigurationEstimation estimateFileConfiguration(final InputStream data)
			throws InternalIOException, BadFileException {
		final String fhirContent;
		try {
			fhirContent = new String(data.readAllBytes(), StandardCharsets.UTF_8);
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.FHIR_READING,
			                              "Failed to read the input stream while estimating the FHIR file configuration",
			                              e);
		}

		final FhirContext fhirContext = FhirContext.forR4();
		final BundleTransformer bundleTransformer = new BundleTransformer(fhirContext);

		final Set<String> resourceTypes;
		try {
			resourceTypes = bundleTransformer.getResourceTypesInBundle(fhirContent);
		} catch (final Exception e) {
			throw new BadFileException(BadFileException.INVALID_FHIR,
			                           "Failed to read the FHIR bundle while estimating the FHIR file configuration",
			                           e);
		}

		final var fhirFileConfiguration = new FhirFileConfiguration();
		final var fileConfiguration = new FileConfiguration();

		fileConfiguration.setFileType(FileType.FHIR);
		fileConfiguration.setFhirFileConfiguration(fhirFileConfiguration);

		return new FileConfigurationEstimation(fileConfiguration, resourceTypes);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberColumns(final InputStream data, final FileConfigurationEntity fileConfiguration
	) throws InternalIOException {
		final CSVFormat csvFormat = buildCsvFormat();
		final String csvString = getCsvString(data, (FhirFileConfigurationEntity) fileConfiguration, csvFormat);
		final CsvFileConfigurationEntity csvFileConfiguration = new CsvFileConfigurationEntity(csvFormat);
		return csvProcessor.getNumberColumns(new ByteArrayInputStream(csvString.getBytes()), csvFileConfiguration);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransformationResult read(final InputStream data,
	                                 final FileConfigurationEntity fileConfiguration,
	                                 final DataConfiguration configuration
	) throws InternalIOException {
		final CSVFormat csvFormat = buildCsvFormat();
		final String csvString = getCsvString(data, (FhirFileConfigurationEntity) fileConfiguration, csvFormat);
		final CsvFileConfigurationEntity csvFileConfiguration = new CsvFileConfigurationEntity(csvFormat);
		return csvProcessor.read(new ByteArrayInputStream(csvString.getBytes()), csvFileConfiguration, configuration);
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
		final CSVFormat csvFormat = buildCsvFormat();
		final String csvString = getCsvString(data, (FhirFileConfigurationEntity) fileConfiguration, csvFormat);
		final CsvFileConfigurationEntity csvFileConfiguration = new CsvFileConfigurationEntity(csvFormat);
		return csvProcessor.estimateDataConfiguration(new ByteArrayInputStream(csvString.getBytes()),
		                                              csvFileConfiguration, algorithm);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write(OutputStream outputStream, DataSet dataset) {
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
		final CSVFormat csvFormat = buildCsvFormat();
		final String csvString = getCsvString(data, (FhirFileConfigurationEntity) fileConfiguration, csvFormat);
		final CsvFileConfigurationEntity csvFileConfiguration = new CsvFileConfigurationEntity(csvFormat);
		return csvProcessor.getFirstRow(new ByteArrayInputStream(csvString.getBytes()), csvFileConfiguration);
	}

	/**
	 * Returns the CSVFormat for converting the FHIR bundle into a CSV file.
	 *
	 * @return The CSV format.
	 */
	private CSVFormat buildCsvFormat() {
		return CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader().build();
	}

	/**
	 * Convert the given FHIR bundle string into a CSV string.
	 *
	 * @param fhirBundle        The FHIR bundle.
	 * @param fileConfiguration The FHIR configuration for importing the FHIR bundle.
	 * @param csvFormat         The CSV format used for the output.
	 * @return The CSV string.
	 * @throws InternalIOException If reading the FHIR bundle failed.
	 */
	private String getCsvString(final InputStream fhirBundle, final FhirFileConfigurationEntity fileConfiguration,
	                            final CSVFormat csvFormat) throws InternalIOException {
		final FhirContext fhirContext = FhirContext.forR4();
		final BundleTransformer bundleTransformer = new BundleTransformer(fhirContext);

		final ResourceExtractor extractor = ResourceExtractor.Companion.forR4();

		final String content;
		try {
			content = new String(fhirBundle.readAllBytes());
		} catch (IOException e) {
			throw new InternalIOException(InternalIOException.FHIR_READING, "Failed to convert FHIR bundle into a CSV.",
			                              e);
		}

		final List<Column> attributes = extractor.getResourceFieldsForEntriesInBundle(content);
		final TransformationParameters transformationParameters = new TransformationParameters(
				csvFormat, Integer.MAX_VALUE, attributes, false, true, true,
				List.of(fileConfiguration.getResourceType()));
		return bundleTransformer.processBundle(content, transformationParameters).toString();
	}
}
