package de.kiaim.cinnamon.platform.helper.fhir;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Flattened table of FHIR resources.
 * Each row has exactly one value per column; missing values are null.
 *
 * @param columns The columns containing the FHIR paths and types of the values.
 * @param rows    The rows of the table.
 * @author Daniel Preciado-Marquez
 */
public record FhirTable(List<FhirColumn> columns, List<List<@Nullable String>> rows) {

	/**
	 * @return The column names, containing the FHIR paths of the values.
	 */
	public List<String> columnNames() {
		return columns.stream().map(FhirColumn::name).toList();
	}

	/**
	 * @return The FHIR datatype of each column, aligned with the column names. Null if the type is unknown.
	 */
	public List<@Nullable String> fhirTypes() {
		return columns.stream().map(FhirColumn::fhirType).toList();
	}
}
