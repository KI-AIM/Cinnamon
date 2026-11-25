package de.kiaim.cinnamon.platform.processor;

import ca.uhn.fhir.context.FhirContext;
import de.unimuenster.imi.fhir.columns_parser.Column;
import de.unimuenster.imi.fhir.transform.BundleTransformer;
import de.unimuenster.imi.fhir.transform.CsvToFhirTransformer;
import de.unimuenster.imi.fhir.transform.ResourceExtractor;
import de.unimuenster.imi.fhir.transform.ResultTable;
import de.unimuenster.imi.fhir.transform.TransformationParameters;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.hl7.fhir.r4.model.Bundle;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Provides shared instances of the transformers from the transform-fhir library.
 * <p>
 * Creating the transformers is expensive:
 * a {@link ResourceExtractor} creates its own {@link FhirContext} and parses the FHIR structure definitions (~38 MB of JSON),
 * and the other transformers create FHIRPath engines or FHIR contexts.
 * Therefore, each transformer is created once in the background on startup (or on first use if that is earlier)
 * and reused afterward.
 * <p>
 * The transformers are not thread-safe, as they hold HAPI parsers, FHIRPath engines and mutable state.
 * Every access to a transformer is synchronized on a lock dedicated to that transformer.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
@Slf4j
public class FhirTransformers {

	private final FhirContext fhirContext;

	/**
	 * If the transformers should be created in the background on startup.
	 */
	@Value("${cinnamon.fhir.preload-transformers:true}")
	private boolean preloadTransformers;

	private final Object resourceExtractorLock = new Object();
	private final Object bundleTransformerLock = new Object();
	private final Object csvToFhirTransformerLock = new Object();

	@Nullable private ResourceExtractor resourceExtractor = null;
	@Nullable private BundleTransformer bundleTransformer = null;
	@Nullable private CsvToFhirTransformer csvToFhirTransformer = null;

	public FhirTransformers(final FhirContext fhirContext) {
		this.fhirContext = fhirContext;
	}

	/**
	 * Creates the transformers in a background thread once the application is ready,
	 * so the first FHIR request does not have to wait for the creation and the application startup is not delayed.
	 * Requests arriving during the initialization wait for the respective transformer instead of creating another one.
	 * Can be disabled with {@code cinnamon.fhir.preload-transformers}.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void initializeOnStartup() {
		if (!preloadTransformers) {
			return;
		}

		final Thread thread = new Thread(this::initialize, "fhir-transformers-init");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Creates all transformers that have not been created yet.
	 * Failures are logged and ignored, as the transformers are created on first use otherwise.
	 */
	private void initialize() {
		final long start = System.currentTimeMillis();
		try {
			synchronized (resourceExtractorLock) {
				getResourceExtractor();
			}
			synchronized (bundleTransformerLock) {
				getBundleTransformer();
			}
			synchronized (csvToFhirTransformerLock) {
				getCsvToFhirTransformer();
			}
			log.info("Initialized FHIR transformers in {} ms", System.currentTimeMillis() - start);
		} catch (final RuntimeException e) {
			log.warn("Failed to initialize FHIR transformers on startup, they will be created on first use.", e);
		}
	}

	/**
	 * Returns the resource types contained in the given FHIR bundle.
	 *
	 * @param bundle The FHIR bundle as JSON.
	 * @return Set of resource types.
	 */
	public Set<String> getResourceTypesInBundle(final String bundle) {
		synchronized (bundleTransformerLock) {
			return getBundleTransformer().getResourceTypesInBundle(bundle);
		}
	}

	/**
	 * Flattens the resources of the given type in the FHIR bundle into a table.
	 * The columns are determined by the fields present in the bundle's resources.
	 *
	 * @param bundle       The FHIR bundle as JSON.
	 * @param resourceType The resource type to be included in the table.
	 * @param csvFormat    The CSV format used when serializing the result.
	 * @return The flattened table.
	 */
	public ResultTable processBundle(final String bundle, final String resourceType, final CSVFormat csvFormat) {
		final List<Column> columns;
		synchronized (resourceExtractorLock) {
			columns = getResourceExtractor().getResourceFieldsForEntriesInBundle(bundle);
		}

		final TransformationParameters transformationParameters = new TransformationParameters(
				csvFormat, Integer.MAX_VALUE, columns, false, true, true, List.of(resourceType));

		synchronized (bundleTransformerLock) {
			return getBundleTransformer().processBundle(bundle, transformationParameters);
		}
	}

	/**
	 * Converts the given CSV into a FHIR bundle.
	 * The column names of the CSV must be FHIR paths.
	 *
	 * @param csv The CSV string.
	 * @return The FHIR bundle.
	 */
	public Bundle parseCsvToBundle(final String csv) {
		synchronized (csvToFhirTransformerLock) {
			return getCsvToFhirTransformer().parseCsvToBundle(csv);
		}
	}

	private ResourceExtractor getResourceExtractor() {
		if (resourceExtractor == null) {
			resourceExtractor = ResourceExtractor.Companion.forR4();
		}
		return resourceExtractor;
	}

	private BundleTransformer getBundleTransformer() {
		if (bundleTransformer == null) {
			bundleTransformer = new BundleTransformer(fhirContext);
		}
		return bundleTransformer;
	}

	private CsvToFhirTransformer getCsvToFhirTransformer() {
		if (csvToFhirTransformer == null) {
			csvToFhirTransformer = new CsvToFhirTransformer();
		}
		return csvToFhirTransformer;
	}
}
