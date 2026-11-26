package de.kiaim.cinnamon.test.platform.processor;

import ca.uhn.fhir.context.FhirContext;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.data.DateTimeData;
import de.kiaim.cinnamon.model.data.DecimalData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.platform.model.FhirTable;
import de.kiaim.cinnamon.platform.processor.FhirFlattener;
import de.kiaim.cinnamon.platform.processor.FhirTypeMapper;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link FhirFlattener} and {@link FhirTypeMapper} without a Spring context.
 */
public class FhirFlattenerTest {

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

	private final FhirFlattener flattener = new FhirFlattener(FHIR_CONTEXT);

	@Test
	public void flattenUsesPositionalPathsAndTypes() {
		final var bundle = parse("""
				{"resourceType":"Bundle","type":"collection","entry":[
				  {"resource":{"resourceType":"Patient","id":"p1",
				    "name":[{"family":"Doe","given":["Jane","Mary"]}],"gender":"female","birthDate":"1990-01-01"}},
				  {"resource":{"resourceType":"Patient","id":"p2",
				    "name":[{"given":["Mary"]}],"gender":"male"}}
				]}""");

		final FhirTable table = flattener.flatten(resources(bundle), "Patient");

		assertEquals(List.of("Patient.id", "Patient.name[0].family", "Patient.name[0].given[0]",
		                     "Patient.name[0].given[1]", "Patient.gender", "Patient.birthDate"),
		             table.columnNames());
		assertEquals(List.of("id", "string", "string", "string", "code", "date"), table.fhirTypes());
		assertEquals(java.util.Arrays.asList("p1", "Doe", "Jane", "Mary", "female", "1990-01-01"),
		             table.rows().get(0));
		// The position is kept: "Mary" is the first given name of the second patient
		assertEquals(java.util.Arrays.asList("p2", null, "Mary", null, "male", null), table.rows().get(1));
	}

	@Test
	public void flattenKeepsIndicesOfEqualValues() {
		// Caramelizer derived the index by searching for the value, so equal values got the same index
		final var bundle = parse("""
				{"resourceType":"Bundle","entry":[{"resource":{"resourceType":"Patient",
				  "identifier":[{"system":"a","value":"same"},{"system":"b","value":"same"}]}}]}""");

		final FhirTable table = flattener.flatten(resources(bundle), "Patient");

		assertEquals(List.of("Patient.identifier[0].system", "Patient.identifier[0].value",
		                     "Patient.identifier[1].system", "Patient.identifier[1].value"),
		             table.columnNames());
		assertEquals(List.of("a", "same", "b", "same"), table.rows().get(0));
	}

	@Test
	public void flattenResolvesChoiceTypesAndExtensions() {
		final var bundle = parse("""
				{"resourceType":"Bundle","entry":[
				  {"resource":{"resourceType":"Observation","status":"final",
				    "extension":[{"url":"http://example.org/flag","valueBoolean":true}],
				    "code":{"text":"Temperature"},
				    "effectiveDateTime":"2019-09-26T16:34:42+02:00",
				    "valueQuantity":{"value":36.20,"unit":"C"}}},
				  {"resource":{"resourceType":"Observation","status":"final","code":{"text":"Smoker"},
				    "valueBoolean":false}}
				]}""");

		final FhirTable table = flattener.flatten(resources(bundle), "Observation");

		assertEquals(List.of("Observation.extension[0].url", "Observation.extension[0].valueBoolean",
		                     "Observation.status", "Observation.code.text", "Observation.effectiveDateTime",
		                     "Observation.valueBoolean", "Observation.valueQuantity.value",
		                     "Observation.valueQuantity.unit"),
		             table.columnNames());
		assertEquals(List.of("uri", "boolean", "code", "string", "dateTime", "boolean", "decimal", "string"),
		             table.fhirTypes());
		// Decimal precision is kept as written
		assertEquals("36.20", table.rows().get(0).get(6));
	}

	@Test
	public void roundTripIsLossless() throws IOException {
		final Bundle bundle = parse(ResourceHelper.loadFhirBundleAsString());

		for (final String resourceType : List.of("Patient", "Observation")) {
			final FhirTable table = flattener.flatten(resources(bundle), resourceType);

			final List<Resource> rebuilt = new ArrayList<>();
			for (final var row : table.rows()) {
				rebuilt.add(flattener.unflatten(resourceType, table.columnNames(), row));
			}

			assertEquals(table, flattener.flatten(rebuilt, resourceType), "Round trip changed " + resourceType);
		}
	}

	@Test
	public void unflattenSkipsInvalidValues() {
		final Resource patient = flattener.unflatten("Patient", List.of("Patient.gender", "Patient.birthDate"),
		                                             List.of("not-a-gender", "1990-01-01"));

		assertEquals("{\"resourceType\":\"Patient\",\"birthDate\":\"1990-01-01\"}",
		             FHIR_CONTEXT.newJsonParser().encodeResourceToString(patient));
	}

	@Test
	public void mapTypes() {
		assertEquals(DataType.BOOLEAN, FhirTypeMapper.toColumnConfiguration("boolean", List.of("true")).getType());
		assertEquals(DataType.INTEGER, FhirTypeMapper.toColumnConfiguration("positiveInt", List.of("1")).getType());
		assertEquals(DataType.DECIMAL, FhirTypeMapper.toColumnConfiguration("decimal", List.of("1.5")).getType());
		assertEquals(DataType.STRING, FhirTypeMapper.toColumnConfiguration("code", List.of("female")).getType());
		// The value would be estimated as an integer, but it is a FHIR string
		assertEquals(DataType.STRING, FhirTypeMapper.toColumnConfiguration("string", List.of("12345")).getType());
		assertEquals(DataType.STRING, FhirTypeMapper.toColumnConfiguration(null, List.of("x")).getType());
	}

	@Test
	public void mapDates() {
		final ColumnConfiguration date = FhirTypeMapper.toColumnConfiguration("date", List.of("1990-01-01"));
		assertEquals(DataType.DATE, date.getType());
		assertEquals(new DateFormatConfiguration("yyyy-MM-dd"), date.getConfiguration(DateFormatConfiguration.class));

		// Partial dates cannot be represented by Cinnamon dates
		assertEquals(DataType.STRING,
		             FhirTypeMapper.toColumnConfiguration("date", List.of("1990-01-01", "1990")).getType());

		final ColumnConfiguration dateTime = FhirTypeMapper.toColumnConfiguration(
				"instant", List.of("2024-12-17T20:17:48.265+00:00", "2019-09-26T14:34:43.313+02:00"));
		assertEquals(DataType.DATE_TIME, dateTime.getType());
		assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSxxx",
		             dateTime.getConfiguration(DateTimeFormatConfiguration.class).getDateTimeFormatter());

		// Date-times that only contain dates are dates
		assertEquals(DataType.DATE, FhirTypeMapper.toColumnConfiguration("dateTime", List.of("2020-05-01")).getType());
		// Mixed precision
		assertEquals(DataType.STRING, FhirTypeMapper.toColumnConfiguration(
				"dateTime", List.of("2020-05-01", "2020-05-01T10:00:00+02:00")).getType());
	}

	@Test
	public void toFhirValue() {
		final ColumnConfiguration dateTime = FhirTypeMapper.toColumnConfiguration(
				"instant", List.of("2024-12-17T20:17:48.265+00:00"));
		assertEquals("2024-12-17T20:17:48.265+00:00",
		             FhirTypeMapper.toFhirValue(new DateTimeData(LocalDateTime.of(2024, 12, 17, 20, 17, 48, 265_000_000)),
		                                        dateTime));
		// Without an offset in the format, ISO with offset is used, as FHIR requires one
		assertEquals("2024-12-17T20:17:48Z",
		             FhirTypeMapper.toFhirValue(new DateTimeData(LocalDateTime.of(2024, 12, 17, 20, 17, 48)),
		                                        new ColumnConfiguration()));
		assertEquals("36.2", FhirTypeMapper.toFhirValue(new DecimalData(36.2f), new ColumnConfiguration()));
		assertNull(FhirTypeMapper.toFhirValue(new DecimalData(null), new ColumnConfiguration()));
	}

	private static Bundle parse(final String bundle) {
		return FHIR_CONTEXT.newJsonParser().parseResource(Bundle.class, bundle);
	}

	private static List<Resource> resources(final Bundle bundle) {
		return bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList();
	}
}
