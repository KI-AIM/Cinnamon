package de.kiaim.cinnamon.platform.helper.fhir;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Column of a flattened table of FHIR resources.
 *
 * @param name         The name of the column, containing the FHIR path of the values.
 * @param fhirType     The FHIR datatype of the values (e.g. {@code date}, {@code decimal}, {@code code}).
 *                     Null if the type is unknown.
 * @param allowedCodes The codes allowed by a required binding of the element (e.g. {@code male}, {@code female},
 *                     ... for {@code Patient.gender}). Null if the values are not restricted.
 * @author Daniel Preciado-Marquez
 */
public record FhirColumn(String name, @Nullable String fhirType, @Nullable List<String> allowedCodes) {

	/**
	 * Creates a column without restricted values.
	 *
	 * @param name     The name of the column.
	 * @param fhirType The FHIR datatype of the values.
	 */
	public FhirColumn(final String name, @Nullable final String fhirType) {
		this(name, fhirType, null);
	}
}
