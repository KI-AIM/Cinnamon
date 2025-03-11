package de.kiaim.cinnamon.model.data;

import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.Configuration;
import de.kiaim.cinnamon.model.configuration.data.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.RangeConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.DateFormatException;
import de.kiaim.cinnamon.model.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

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
		 */
		private static final List<String> FORMATS = List.of(
				"EEEE, MMMM d, yyyy",
				"yyyy-MM-dd",
				"yyyy:MM:dd",
				"yyyy.MM.dd",
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
					LocalDate.parse(value, DateTimeFormatter.ofPattern(format));
					columnConfiguration.addConfiguration(new DateFormatConfiguration(format));
					columnConfiguration.setType(DataType.DATE);
					break;
				} catch (final DateTimeParseException ignored) {
				}
			}

			return columnConfiguration;
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
			this.formatter = DateTimeFormatter.ofPattern(configuration.getDateFormatter());
		}

		private void processRangeConfiguration(RangeConfiguration rangeConfiguration) {
			this.minValue = rangeConfiguration.getMinValue().asDate();
			this.maxValue = rangeConfiguration.getMaxValue().asDate();
		}
	}
}
