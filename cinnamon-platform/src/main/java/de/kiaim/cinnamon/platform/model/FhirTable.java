package de.kiaim.cinnamon.platform.model;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Flattened table of FHIR resources.
 * Each row has exactly one value per column; missing values are null.
 *
 * @param columnNames The column names, containing the FHIR paths of the values.
 * @param fhirTypes   The FHIR datatype of each column (e.g. {@code date}, {@code decimal}, {@code code}),
 *                    aligned with the column names. Null if the type is unknown.
 * @param rows        The rows of the table.
 * @author Daniel Preciado-Marquez
 */
public record FhirTable(List<String> columnNames, List<@Nullable String> fhirTypes, List<List<@Nullable String>> rows) {

	/**
	 * Creates a table without type information.
	 *
	 * @param columnNames The column names, containing the FHIR paths of the values.
	 * @param rows        The rows of the table.
	 */
	public FhirTable(final List<String> columnNames, final List<List<@Nullable String>> rows) {
		this(columnNames, Collections.nCopies(columnNames.size(), null), rows);
	}
}
