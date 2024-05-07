package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.*;
import de.kiaim.platform.model.data.exception.DateTimeFormatException;
import de.kiaim.platform.model.data.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
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
		private LocalDateTime value;

		private DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
		private LocalDateTime minValue = LocalDateTime.MIN;
		private LocalDateTime maxValue = LocalDateTime.MAX;

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
