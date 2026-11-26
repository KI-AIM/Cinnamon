package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.Configuration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataBuilder;
import de.kiaim.cinnamon.model.data.DateData;
import de.kiaim.cinnamon.model.data.DateTimeData;
import de.kiaim.cinnamon.model.data.TextData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.DataBuildingException;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Maps FHIR datatypes to Cinnamon column configurations and Cinnamon values back to FHIR values.
 * <p>
 * The FHIR datatype of an element is fixed by the specification, so the Cinnamon data type is derived from it
 * instead of estimating it from samples.
 * Values are only inspected in two cases:
 * <ul>
 *     <li>Dates, because FHIR allows reduced precision ({@code 2020}, {@code 2020-05}),
 *     which Cinnamon's date types cannot represent. Such columns fall back to {@link DataType#STRING}.</li>
 *     <li>Strings, because the FHIR type does not distinguish short strings from free text.
 *     The same length rule as in the estimation for other file types is applied.</li>
 * </ul>
 * Narratives ({@code xhtml}) and {@code markdown} are always free text.
 *
 * @author Daniel Preciado-Marquez
 */
public final class FhirTypeMapper {

	/**
	 * Format of FHIR dates with full precision.
	 */
	public static final String FHIR_DATE_FORMAT = "yyyy-MM-dd";

	/**
	 * Matches pattern letters for offsets in a date-time format, ignoring quoted literals.
	 */
	private static final Pattern OFFSET_PATTERN_LETTERS = Pattern.compile("[xXZO]");

	private FhirTypeMapper() {
	}

	/**
	 * Derives the column configuration for a column of the given FHIR datatype.
	 * Index, name and scale are not set.
	 *
	 * @param fhirType The FHIR datatype, e.g. {@code date} or {@code decimal}. Null if unknown.
	 * @param values   The non-empty values of the column. Only inspected for date columns.
	 * @return The column configuration.
	 */
	public static ColumnConfiguration toColumnConfiguration(@Nullable final String fhirType,
	                                                        final List<String> values) {
		if (fhirType == null) {
			return columnConfiguration(DataType.STRING);
		}

		return switch (fhirType) {
			case "boolean" -> columnConfiguration(DataType.BOOLEAN);
			case "integer", "positiveInt", "unsignedInt" -> columnConfiguration(DataType.INTEGER);
			case "decimal" -> columnConfiguration(DataType.DECIMAL);
			case "date" -> allFullDates(values)
			               ? columnConfiguration(DataType.DATE, new DateFormatConfiguration(FHIR_DATE_FORMAT))
			               : columnConfiguration(DataType.STRING);
			case "dateTime", "instant" -> mapDateTime(values);
			case "xhtml", "markdown" -> columnConfiguration(DataType.TEXT);
			case "string" -> isFreeText(values) ? columnConfiguration(DataType.TEXT) : columnConfiguration(DataType.STRING);
			// code, id, uri, url, canonical, oid, uuid, base64Binary, time, integer64, ...
			default -> columnConfiguration(DataType.STRING);
		};
	}

	/**
	 * Converts a Cinnamon value into the string representation of FHIR.
	 *
	 * @param data                The value.
	 * @param columnConfiguration The configuration of the column, used to format date-times.
	 * @return The FHIR value or null if the value is missing.
	 */
	@Nullable
	public static String toFhirValue(@Nullable final Data data, final ColumnConfiguration columnConfiguration) {
		if (data == null || data.getValue() == null) {
			return null;
		}

		return switch (data.getDataType()) {
			// LocalDate#toString is ISO-8601, which is the FHIR format
			case DATE -> data.asDate().toString();
			case DATE_TIME -> formatDateTime(data.asDateTime(), columnConfiguration);
			case DECIMAL -> new BigDecimal(Float.toString(data.asDecimal())).toPlainString();
			default -> data.getValue().toString();
		};
	}

	/**
	 * Maps a dateTime or instant column.
	 * Columns containing only dates are mapped to {@link DataType#DATE}.
	 * Otherwise, a date-time format that accepts all values is searched.
	 */
	private static ColumnConfiguration mapDateTime(final List<String> values) {
		if (allFullDates(values)) {
			return columnConfiguration(DataType.DATE, new DateFormatConfiguration(FHIR_DATE_FORMAT));
		}

		final DateTimeData.DateTimeDataBuilder builder = new DateTimeData.DateTimeDataBuilder();
		final Optional<String> format = builder.estimateFormatForSamples(values);
		if (format.isPresent()) {
			final Configuration formatConfiguration = new DateTimeFormatConfiguration(format.get());
			if (allAccepted(builder, values, formatConfiguration)) {
				return columnConfiguration(DataType.DATE_TIME, formatConfiguration);
			}
		}

		return columnConfiguration(DataType.STRING);
	}

	/**
	 * Checks if the majority of the values is free text,
	 * using the length rule of {@link TextData.TextDataBuilder#estimateColumnConfiguration(String)}.
	 */
	private static boolean isFreeText(final List<String> values) {
		final TextData.TextDataBuilder builder = new TextData.TextDataBuilder();
		int freeTextCount = 0;
		for (final String value : values) {
			if (builder.estimateColumnConfiguration(value).getType() == DataType.TEXT) {
				freeTextCount++;
			}
		}
		return freeTextCount * 2 > values.size();
	}

	/**
	 * Checks if all values are dates with full precision.
	 */
	private static boolean allFullDates(final List<String> values) {
		return !values.isEmpty() && allAccepted(new DateData.DateDataBuilder(), values,
		                                        new DateFormatConfiguration(FHIR_DATE_FORMAT));
	}

	/**
	 * Checks if all values are accepted by the builder with the given configuration,
	 * i.e. reading the column with the configuration will not cause transformation errors.
	 */
	private static boolean allAccepted(final DataBuilder builder, final List<String> values,
	                                   final Configuration configuration) {
		for (final String value : values) {
			try {
				builder.setValue(value, List.of(configuration));
			} catch (final DataBuildingException | RuntimeException e) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Formats a date-time for FHIR.
	 * Cinnamon normalizes date-times to UTC, so the value is written with a UTC offset.
	 * The configured format is reused if it contains an offset, so the output matches the input format.
	 * Otherwise, ISO-8601 is used, as FHIR requires an offset for date-times with a time.
	 */
	private static String formatDateTime(final LocalDateTime value, final ColumnConfiguration columnConfiguration) {
		final DateTimeFormatConfiguration formatConfiguration =
				columnConfiguration.getConfiguration(DateTimeFormatConfiguration.class);
		if (formatConfiguration != null) {
			final String format = formatConfiguration.getDateTimeFormatter();
			final String unquotedFormat = format.replaceAll("'[^']*'", "");
			if (OFFSET_PATTERN_LETTERS.matcher(unquotedFormat).find()) {
				return value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
			}
		}

		return value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	private static ColumnConfiguration columnConfiguration(final DataType type, final Configuration... configurations) {
		final ColumnConfiguration columnConfiguration = new ColumnConfiguration();
		columnConfiguration.setType(type);
		for (final Configuration configuration : configurations) {
			columnConfiguration.addConfiguration(configuration);
		}
		return columnConfiguration;
	}
}
