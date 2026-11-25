package de.kiaim.cinnamon.platform.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Flattened table of FHIR resources.
 * Each row has exactly one value per column; missing values are null.
 *
 * @param columnNames The column names, containing the FHIR paths of the values.
 * @param rows        The rows of the table.
 * @author Daniel Preciado-Marquez
 */
public record FhirTable(List<String> columnNames, List<List<@Nullable String>> rows) {
}
