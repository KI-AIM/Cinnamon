package de.kiaim.model.data;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.Configuration;
import de.kiaim.model.configuration.data.DateTimeFormatConfiguration;
import de.kiaim.model.configuration.data.RangeConfiguration;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.exception.DateTimeFormatException;
import de.kiaim.model.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DateTimeData extends Data {

	@Nullable
	private final LocalDateTime value;

	@Override
	public DataType getDataType() {
		return DataType.DATE_TIME;
	}


	/**
	 * Builder pattern to set and validate a value.
	 * Performs validation based on the different configurations
	 * that were parsed for the column by the frontend
	 */
	public static class DateTimeDataBuilder implements DataBuilder {
		/**
		 * List of date time formats used for the estimation of the column configuration.
		 */
		private static final List<String> FORMATS = List.of(
				"E, y-M-d 'at' h:m:s a z",
				"E yyyy.MM.dd 'at' hh:mm:ss a zzz",
				"yyyy-MM-dd hh:mm:ss",
				"yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
				"yyyy-MM-dd'T'HH:mm:ss"
		);

		private LocalDateTime value;

		private DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
		private LocalDateTime minValue = LocalDateTime.MIN;
		private LocalDateTime maxValue = LocalDateTime.MAX;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public DataType getDataType() {
			return DataType.DATE_TIME;
		}

		/**
		 * Sets the value of the resulting DateTime Object
		 * @param value The String value to be set
		 * @param configuration The List of Configuration objects for the column
		 * @return DateTimeDataBuilder (this)
		 * @throws DateTimeFormatException if the given value could not be transformed into a date time using the configured format.
		 * @throws ValueNotInRangeException if the transformed date time is not in the configured range.
		 */
		@Override
		public DateTimeDataBuilder setValue(String value, List<Configuration> configuration)
				throws DateTimeFormatException, ValueNotInRangeException {
			processConfigurations(configuration);

			try {
				this.value = LocalDateTime.parse(value, formatter);
			} catch(Exception e) {
				throw new DateTimeFormatException();
			}

			if (this.value.isBefore(minValue) || this.value.isAfter(maxValue)) {
				throw new ValueNotInRangeException();
			}

			return this;
		}

		/**
		 * Builds the DateTimeData Object.
		 * Only to be called after setValue()
		 * @return new DateTimeData object
		 */
		@Override
		public DateTimeData build() {
			return new DateTimeData(this.value);
		}

		/**
		 * Builds the DateTimeData object containing a null value.
		 * @return the new DateTimeData object.
		 */
		@Override
		public DateTimeData buildNull() {
			return new DateTimeData(null);
		}

		/**
		 * Estimates the data type and the date time format configuration for the given value.
		 * @param value The raw value.
		 * @return The estimated ColumnConfiguration.
		 */
		@Override
		public ColumnConfiguration estimateColumnConfiguration(final String value) {
			final var columnConfiguration = new ColumnConfiguration();

			for (final String format : FORMATS) {
				try {
					LocalDateTime.parse(value, DateTimeFormatter.ofPattern(format));
					columnConfiguration.addConfiguration(new DateTimeFormatConfiguration(format));
					columnConfiguration.setType(DataType.DATE_TIME);
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
				if (configuration instanceof DateTimeFormatConfiguration) {
					processDateTimeFormatConfiguration((DateTimeFormatConfiguration) configuration);
				} else if (configuration instanceof RangeConfiguration) {
					processRangeConfiguration((RangeConfiguration) configuration);
				}
			}
		}

		/**
		 * Process the DateTimeFormatConfiguration that sets a date-time
		 * format that a String should match.
		 * The configuration changes the internal formatter
		 * used to parse a String
		 * @param configuration The DateTimeFormatConfiguration object
		 */
		private void processDateTimeFormatConfiguration(DateTimeFormatConfiguration configuration) {
			this.formatter = DateTimeFormatter.ofPattern(configuration.getDateTimeFormatter());
		}

		private void processRangeConfiguration(RangeConfiguration rangeConfiguration) {
			this.minValue = rangeConfiguration.getMinValue().asDateTime();
			this.maxValue = rangeConfiguration.getMaxValue().asDateTime();
		}
	}
}
