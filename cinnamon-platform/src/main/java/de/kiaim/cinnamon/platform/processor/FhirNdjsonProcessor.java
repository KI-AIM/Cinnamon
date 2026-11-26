package de.kiaim.cinnamon.platform.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.BadDataConfigurationException;
import de.kiaim.cinnamon.platform.exception.BadDatasetException;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.FhirTable;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.dto.FileConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Class for processing FHIR NDJSON files.
 * Reading a NDJSON file parses the resources line by line and flattens them using the {@link FhirFlattener}.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class FhirNdjsonProcessor implements DataProcessor {

	private final FhirBundleProcessor fhirBundleProcessor;
	private final FhirContext fhirContext;
	private final FhirFlattener fhirFlattener;

	public FhirNdjsonProcessor(final FhirBundleProcessor fhirBundleProcessor, final FhirContext fhirContext,
	                           final FhirFlattener fhirFlattener) {
		this.fhirBundleProcessor = fhirBundleProcessor;
		this.fhirContext = fhirContext;
		this.fhirFlattener = fhirFlattener;
	}

	@Override
	public FileType getSupportedDataType() {
		return FileType.FHIR_NDJSON;
	}

	@Override
	public void checkFileCompatibility(final LobWrapperEntity data, final FileCompatibilityEntity fileCompatibility) {
		final Set<String> resourceTypes;
		try {
			resourceTypes = new HashSet<>();
			for (final Resource resource : parseResources(data.getLobString())) {
				resourceTypes.add(resource.fhirType());
			}
		} catch (final Exception ignored) {
			return;
		}

		if (resourceTypes.size() != 1 )
			return;

		fileCompatibility.getCompatibleFileTypes().add(FileType.FHIR_NDJSON);
		fileCompatibility.setFhirResourceTypes(resourceTypes);
	}

	@Override
	public FileConfigurationEstimation estimateFileConfiguration(
			final LobWrapperEntity data, final FileCompatibilityEntity fileCompatibility) throws InternalIOException {
		final var fileConfiguration = new FileConfiguration();
		fileConfiguration.setFileType(FileType.FHIR_NDJSON);
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

		final List<String> expectedColumns = getTable(fileData.getLobStream()).columnNames();

		fhirBundleProcessor.compareColumnNames(dataConfiguration, expectedColumns);
	}

	@Override
	public int getNumberColumns(final InputStream data, final FileConfigurationEntity fileConfiguration
	) throws InternalIOException {
		return getTable(data).columnNames().size();
	}

	@Override
	public TransformationResult read(final InputStream data, final FileConfigurationEntity fileConfiguration,
	                                 final DataConfiguration configuration
	) throws BadDatasetException, InternalIOException {
		return fhirBundleProcessor.read(getTable(data), configuration);
	}

	@Override
	public DataConfigurationEstimation estimateDataConfiguration(final InputStream data,
	                                                             final FileConfigurationEntity fileConfiguration,
	                                                             final DatatypeEstimationAlgorithm algorithm
	) throws InternalIOException {
		return fhirBundleProcessor.estimateDataConfiguration(getTable(data), algorithm);
	}

	@Override
	public void write(final OutputStream outputStream, final DataSet dataset) throws InternalIOException {
		convertBundleToNdjson(fhirBundleProcessor.toBundle(dataset), outputStream);
	}

	/**
	 * Reads the NDJSON file and flattens the contained resources into a table.
	 *
	 * @param data The input stream containing the NDJSON file.
	 * @return The table.
	 * @throws InternalIOException If reading the file failed or it contains invalid resources.
	 */
	private FhirTable getTable(final InputStream data) throws InternalIOException {
		final List<Resource> resources = parseResources(readNdjson(data));
		if (resources.isEmpty()) {
			throw new InternalIOException(InternalIOException.FHIR_NDJSON_READING,
			                              "The FHIR NDJSON file does not contain any resources.", null);
		}

		// NDJSON files from FHIR bulk exports contain resources of a single type
		return fhirFlattener.flatten(resources, resources.get(0).fhirType());
	}

	/**
	 * Reads the entire input stream as a UTF-8 string.
	 *
	 * @param data The input stream containing the NDJSON file.
	 * @return The NDJSON content.
	 * @throws InternalIOException If reading the stream failed.
	 */
	private String readNdjson(final InputStream data) throws InternalIOException {
		try {
			return new String(data.readAllBytes(), StandardCharsets.UTF_8);
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.FHIR_NDJSON_READING, "Failed to read the FHIR NDJSON file.", e);
		}
	}

	/**
	 * Returns the non-blank lines of the NDJSON file.
	 * Each line contains exactly one FHIR resource.
	 *
	 * @param ndjson The NDJSON content.
	 * @return Stream of the lines containing a resource.
	 */
	private Stream<String> getResourceLines(final String ndjson) {
		return ndjson.lines().filter(line -> !line.isBlank());
	}

	/**
	 * Parses the resources of the NDJSON file.
	 *
	 * @param ndjson The NDJSON content, one resource per line.
	 * @return The resources.
	 * @throws InternalIOException If a line does not contain a valid FHIR resource.
	 */
	private List<Resource> parseResources(final String ndjson) throws InternalIOException {
		final IParser parser = fhirContext.newJsonParser();
		final List<Resource> resources = new ArrayList<>();

		final Iterator<String> lines = getResourceLines(ndjson).iterator();
		int resourceNumber = 0;
		while (lines.hasNext()) {
			resourceNumber++;
			try {
				resources.add((Resource) parser.parseResource(lines.next()));
			} catch (final DataFormatException | ClassCastException e) {
				throw new InternalIOException(InternalIOException.FHIR_NDJSON_READING,
				                              "Failed to parse FHIR resource number " + resourceNumber +
				                              " of the NDJSON file.", e);
			}
		}

		return resources;
	}

	/**
	 * Converts the given FHIR bundle into NDJSON by writing each contained resource on a separate line.
	 *
	 * @param bundle       The FHIR bundle.
	 * @param ndjsonStream The stream the NDJSON gets written into.
	 * @throws InternalIOException If writing the NDJSON failed.
	 */
	private void convertBundleToNdjson(final Bundle bundle, final OutputStream ndjsonStream)
			throws InternalIOException {
		final IParser parser = fhirContext.newJsonParser().setPrettyPrint(false);

		try {
			final Writer writer = new BufferedWriter(new OutputStreamWriter(ndjsonStream, StandardCharsets.UTF_8));
			for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
				if (!entry.hasResource()) {
					continue;
				}
				writer.write(parser.encodeResourceToString(entry.getResource()));
				writer.write('\n');
			}
			writer.flush();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.FHIR_NDJSON_WRITING, "Failed to write FHIR NDJSON.", e);
		}
	}
}
