package de.kiaim.cinnamon.test.platform.processor;

import ca.uhn.fhir.context.FhirContext;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.data.DateTimeData;
import de.kiaim.cinnamon.model.data.DecimalData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.DataBuildingException;
import de.kiaim.cinnamon.model.helper.DataTransformationHelper;
import de.kiaim.cinnamon.platform.model.FhirColumn;
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
	private static final DataTransformationHelper DATA_TRANSFORMATION_HELPER = new DataTransformationHelper();

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
	public void flattenAndUnflattenPrimitiveExtensions() {
		// Birth time on the birth date, an extension on the second given name,
		// and a data absent reason on the gender, which has no value
		final String json = """
				{"resourceType":"Patient",\
				"name":[{"given":["Jane","Mary"],"_given":[null,{"extension":[{"url":"http://example.org/nick","valueBoolean":true}]}]}],\
				"_gender":{"extension":[{"url":"http://hl7.org/fhir/StructureDefinition/data-absent-reason","valueCode":"unknown"}]},\
				"birthDate":"1990-01-01",\
				"_birthDate":{"extension":[{"url":"http://hl7.org/fhir/StructureDefinition/patient-birthTime","valueDateTime":"1990-01-01T10:15:00+01:00"}]}}""";
		final Resource patient = (Resource) FHIR_CONTEXT.newJsonParser().parseResource(json);

		final FhirTable table = flattener.flatten(List.of(patient), "Patient");

		assertEquals(List.of("Patient.name[0].given[0]", "Patient.name[0].given[1]",
		                     "Patient.name[0].given[1].extension[0].url",
		                     "Patient.name[0].given[1].extension[0].valueBoolean",
		                     "Patient.gender.extension[0].url", "Patient.gender.extension[0].valueCode",
		                     "Patient.birthDate",
		                     "Patient.birthDate.extension[0].url", "Patient.birthDate.extension[0].valueDateTime"),
		             table.columnNames());
		assertEquals(List.of("string", "string", "uri", "boolean", "uri", "code", "date", "uri", "dateTime"),
		             table.fhirTypes());

		final Resource rebuilt = flattener.unflatten("Patient", table.columnNames(), table.rows().get(0));
		assertEquals(json, FHIR_CONTEXT.newJsonParser().encodeResourceToString(rebuilt));
	}

	@Test
	public void flattenNarrativeAsPlainText() throws IOException {
		final Bundle bundle = parse(ResourceHelper.loadFhirBundleAsString());

		final FhirTable table = flattener.flatten(resources(bundle), "Patient");

		final int index = table.columnNames().indexOf("Patient.text.div");
		// The div follows the status, like in the specification
		assertEquals("Patient.text.status", table.columnNames().get(index - 1));
		assertEquals("xhtml", table.fhirTypes().get(index));
		assertEquals("JOHN STEVE SMITH\nIdentifier urn:id:999991\nDate of birth 01 January 1950",
		             table.rows().get(0).get(index));
	}

	@Test
	public void unflattenNarrative() {
		final Resource patient = flattener.unflatten("Patient", List.of("Patient.text.status", "Patient.text.div"),
		                                             List.of("generated", "Smith & <Sons>\nSecond line"));

		assertEquals("{\"resourceType\":\"Patient\",\"text\":{\"status\":\"generated\"," +
		             "\"div\":\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">Smith &amp; &lt;Sons&gt;<br/>Second line</div>\"}}",
		             FHIR_CONTEXT.newJsonParser().encodeResourceToString(patient));

		// Flattening the result returns the original text
		final FhirTable table = flattener.flatten(List.of(patient), "Patient");
		assertEquals(List.of("generated", "Smith & <Sons>\nSecond line"), table.rows().get(0));
	}

	@Test
	public void unflattenRemovesNarrativeWithoutText() {
		// E.g. the text was removed by the anonymization, FHIR requires a div for narratives
		final Resource patient = flattener.unflatten("Patient", List.of("Patient.text.status", "Patient.text.div",
		                                                                "Patient.gender"),
		                                             java.util.Arrays.asList("generated", null, "female"));

		assertEquals("{\"resourceType\":\"Patient\",\"gender\":\"female\"}",
		             FHIR_CONTEXT.newJsonParser().encodeResourceToString(patient));
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
		assertEquals(DataType.STRING, FhirTypeMapper.toColumnConfiguration((String) null, List.of("x")).getType());
	}

	@Test
	public void flattenCollectsAllowedCodesOfRequiredBindings() throws IOException {
		final Bundle bundle = parse(ResourceHelper.loadFhirBundleAsString());

		final FhirTable patients = flattener.flatten(resources(bundle), "Patient");
		assertEquals(List.of("male", "female", "other", "unknown"), column(patients, "Patient.gender").allowedCodes());

		// Codes without a required binding are not restricted
		final FhirTable observations = flattener.flatten(resources(bundle), "Observation");
		assertNull(column(observations, "Observation.code.coding[0].code").allowedCodes());
		assertEquals("code", column(observations, "Observation.code.coding[0].code").fhirType());
	}

	@Test
	public void mapConstraints() {
		final var gender = FhirTypeMapper.toColumnConfiguration(
				new FhirColumn("Patient.gender", "code", List.of("male", "female", "other", "unknown")), List.of());
		assertAccepted(gender, "female");
		// E.g. a partially masked value of the anonymization
		assertRejected(gender, "femXXX");

		// Special characters in codes are escaped
		final var comparator = FhirTypeMapper.toColumnConfiguration(
				new FhirColumn("Observation.valueQuantity.comparator", "code", List.of("<", "<=", ">=", ">")),
				List.of());
		assertAccepted(comparator, "<=");
		assertRejected(comparator, "<<");

		final var positiveInt = FhirTypeMapper.toColumnConfiguration("positiveInt", List.of("1"));
		assertAccepted(positiveInt, "1");
		assertRejected(positiveInt, "0");
		final var unsignedInt = FhirTypeMapper.toColumnConfiguration("unsignedInt", List.of("0"));
		assertAccepted(unsignedInt, "0");
		assertRejected(unsignedInt, "-1");

		// Dates with reduced precision are stored as strings, but must still be FHIR dates
		final var partialDate = FhirTypeMapper.toColumnConfiguration("date", List.of("1990"));
		assertEquals(DataType.STRING, partialDate.getType());
		assertAccepted(partialDate, "1990");
		assertAccepted(partialDate, "1990-05");
		assertRejected(partialDate, "1990-13");
		assertRejected(partialDate, "199X");

		final var partialDateTime = FhirTypeMapper.toColumnConfiguration(
				"dateTime", List.of("2020-05-01", "2020-05-01T10:00:00+02:00"));
		assertEquals(DataType.STRING, partialDateTime.getType());
		assertAccepted(partialDateTime, "2020");
		assertAccepted(partialDateTime, "2020-05-01T10:00:00.123Z");
		// FHIR requires an offset for date-times with a time
		assertRejected(partialDateTime, "2020-05-01T10:00:00");

		final var time = FhirTypeMapper.toColumnConfiguration("time", List.of("10:00:00"));
		assertAccepted(time, "23:59:59.5");
		assertRejected(time, "24:00:00");

		final var id = FhirTypeMapper.toColumnConfiguration("id", List.of("p1"));
		assertAccepted(id, "patient-1.a");
		assertRejected(id, "patient 1");
		assertRejected(id, "x".repeat(65));
	}

	@Test
	public void mapFreeText() {
		assertEquals(DataType.TEXT, FhirTypeMapper.toColumnConfiguration("xhtml", List.of("short")).getType());
		assertEquals(DataType.TEXT, FhirTypeMapper.toColumnConfiguration("markdown", List.of("short")).getType());

		// Strings are free text if the majority of the values is longer than the threshold of the estimation
		final String longText = "x".repeat(300);
		assertEquals(DataType.STRING,
		             FhirTypeMapper.toColumnConfiguration("string", List.of("Body Temperature")).getType());
		assertEquals(DataType.TEXT,
		             FhirTypeMapper.toColumnConfiguration("string", List.of(longText, longText, "short")).getType());
		assertEquals(DataType.STRING,
		             FhirTypeMapper.toColumnConfiguration("string", List.of(longText, "short", "short")).getType());
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

	private static FhirColumn column(final FhirTable table, final String name) {
		return table.columns().stream().filter(column -> column.name().equals(name)).findFirst().orElseThrow();
	}

	private static void assertAccepted(final ColumnConfiguration columnConfiguration, final String value) {
		assertDoesNotThrow(() -> DATA_TRANSFORMATION_HELPER.transformData(value, columnConfiguration),
		                   "Expected '" + value + "' to be accepted");
	}

	private static void assertRejected(final ColumnConfiguration columnConfiguration, final String value) {
		assertThrows(DataBuildingException.class,
		             () -> DATA_TRANSFORMATION_HELPER.transformData(value, columnConfiguration),
		             "Expected '" + value + "' to be rejected");
	}

	private static Bundle parse(final String bundle) {
		return FHIR_CONTEXT.newJsonParser().parseResource(Bundle.class, bundle);
	}

	private static List<Resource> resources(final Bundle bundle) {
		return bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList();
	}
}
