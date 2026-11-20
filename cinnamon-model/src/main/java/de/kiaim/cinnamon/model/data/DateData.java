package de.kiaim.cinnamon.model.data;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.Configuration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.RangeConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.DateFormatException;
import de.kiaim.cinnamon.model.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DateData extends Data {

	@Nullable
	private final LocalDate value;

	/**
	 * {@inheritDoc}
	 */
	@Override public DataType getDataType() {
		return DataType.DATE;
	}

	/**
	 * Builder pattern to set and validate a value.
	 * Performs validation based on the different configurations
	 * that were parsed for the column by the frontend
	 */
	public static class DateDataBuilder implements DataBuilder {
		/**
		 * List of date formats used for the estimation of the column configuration.
		 * <p>
		 * The formats 'yyyy' and 'yyyy-MM', which are valid FHIR formats, are not considered in the estimation,
		 * as they are too general and would lead to false positives.
		 */
		private static final List<String> FORMATS = List.of(
				"EEEE, MMMM d, yyyy",
				"MMM d, yyyy",
				"d MMMM yyyy",
				"yyyy-MM-dd",
				"yyyy:MM:dd",
				"yyyy.MM.dd",
				"yyyy/MM/dd",
				"yyyyMMdd",
				"dd-MM-yyyy",
				"dd:MM:yyyy",
				"dd.MM.yyyy",
				"dd/MM/yyyy",
				"MM-dd-yyyy",
				"MM.dd.yyyy",
				"MM:dd:yyyy",
				"MM/dd/yyyy"
		);

		private LocalDate value;

		private DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
		private LocalDate minValue = LocalDate.MIN;
		private LocalDate maxValue = LocalDate.MAX;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public DataType getDataType() {
			return DataType.DATE;
		}

		/**
		 * Sets the value of the resulting Date Object
		 * @param value The String value to be set
		 * @param configuration The List of Configuration objects for the column
		 * @return DateDataBuilder (this)
		 * @throws DateFormatException if the given value could not be transformed into a date using the configured format.
		 * @throws ValueNotInRangeException if the transformed date is not in the configured range.
		 */
		@Override
		public DateDataBuilder setValue(String value, List<Configuration> configuration)
				throws DateFormatException, ValueNotInRangeException {
			processConfigurations(configuration);

			try {
				this.value = LocalDate.parse(value, formatter);
			} catch(Exception e) {
				throw new DateFormatException();
			}

			if (this.value.isBefore(minValue) || this.value.isAfter(maxValue)) {
				throw new ValueNotInRangeException();
			}

			return this;
		}

		/**
		 * Builds the DateData Object.
		 * Only to be called after setValue()
		 * @return new DateData object
		 */
		@Override
		public DateData build() {
			return new DateData(this.value);
		}

		/**
		 * Builds the DateData object containing a null value.
		 * @return the new DateData object.
		 */
		@Override
		public DateData buildNull() {
			return new DateData(null);
		}

		/**
		 * Estimates the data type and the date format configuration for the given value.
		 * @param value The raw value.
		 * @return The estimated ColumnConfiguration.
		 */
		@Override
		public ColumnConfiguration estimateColumnConfiguration(final String value) {
			final var columnConfiguration = new ColumnConfiguration();

			for (final String format : FORMATS) {
				try {
					LocalDate.parse(value, buildFormatter(format));
					columnConfiguration.addConfiguration(new DateFormatConfiguration(format));
					columnConfiguration.setType(DataType.DATE);
					break;
				} catch (final DateTimeParseException ignored) {
				}
			}

			return columnConfiguration;
		}

		/**
		 * Estimates the best-fitting date format for a whole column of samples by testing every known format
		 * against every sample and choosing the format that matches the most samples.
		 * <p>
		 * This resolves ambiguous numeric formats (e.g. {@code dd/MM/yyyy} vs. {@code MM/dd/yyyy}) using evidence
		 * from the whole column instead of the order-dependent, per-sample first match used by
		 * {@link #estimateColumnConfiguration(String)}. List order is only used to break ties between formats
		 * that match the same number of samples.
		 *
		 * @param samples The samples belonging to a single column.
		 * @return The best-fitting format, or empty if no format matches any sample.
		 */
		public Optional<String> estimateFormatForSamples(final List<String> samples) {
			String bestFormat = null;
			int bestCount = 0;

			for (final String format : FORMATS) {
				final DateTimeFormatter formatter = buildFormatter(format);
				int count = 0;
				for (final String sample : samples) {
					try {
						LocalDate.parse(sample, formatter);
						count++;
					} catch (final DateTimeParseException ignored) {
					}
				}

				if (count > bestCount) {
					bestCount = count;
					bestFormat = format;
				}
			}

			return Optional.ofNullable(bestFormat);
		}

		/**
		 * Processes the parsed configurations one by one for validation
		 * @param configurationList A List of different Configuration objects
		 */
		private void processConfigurations(List<Configuration> configurationList) {
			for (Configuration configuration : configurationList) {
				if (configuration instanceof DateFormatConfiguration) {
					processDateFormatConfiguration((DateFormatConfiguration) configuration);
				} else if (configuration instanceof RangeConfiguration) {
					processRangeConfiguration((RangeConfiguration) configuration);
				}
			}
		}

		/**
		 * Process the DateFormatConfiguration that sets a date
		 * format that a String should match.
		 * The configuration changes the internal formatter
		 * used to parse a String
		 * @param configuration The DateFormatConfiguration object
		 */
		private void processDateFormatConfiguration(DateFormatConfiguration configuration) {
			this.formatter = buildFormatter(configuration.getDateFormatter());
		}

		private void processRangeConfiguration(RangeConfiguration rangeConfiguration) {
			this.minValue = rangeConfiguration.getMinValue().asDate();
			this.maxValue = rangeConfiguration.getMaxValue().asDate();
		}

		/**
		 * Build a formatter with defaults months to January and days to the first day of the month.
		 *
		 * @param format The format.
		 * @return The formatter.
		 */
		public DateTimeFormatter buildFormatter(final String format) {
			return new DateTimeFormatterBuilder()
					.appendPattern(format)
					.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
					.parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
					.toFormatter(Locale.ENGLISH);
		}
	}
}
