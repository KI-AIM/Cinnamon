package de.kiaim.model.data;

import de.kiaim.model.configuration.Configuration;
import de.kiaim.model.configuration.DateFormatConfiguration;
import de.kiaim.model.configuration.RangeConfiguration;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.exception.DateFormatException;
import de.kiaim.model.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class DateData extends Data {

	@Nullable
	private final LocalDate value;

	@Override public DataType getDataType() {
		return DataType.DATE;
	}

	/**
	 * Builder pattern to set and validate a value.
	 * Performs validation based on the different configurations
	 * that were parsed for the column by the frontend
	 */
	public static class DateDataBuilder implements DataBuilder {
		private LocalDate value;

		private DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
		private LocalDate minValue = LocalDate.MIN;
		private LocalDate maxValue = LocalDate.MAX;

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
