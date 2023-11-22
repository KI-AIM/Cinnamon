package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.Configuration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.data.configuration.DateFormatConfiguration;
import de.kiaim.platform.model.data.exception.DateFormatException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@AllArgsConstructor
public class DateData extends Data {

	private final LocalDate value;

	@Override public DataType getDataType() {
		return DataType.DATE;
	}

	/**
	 * Builder pattern to set and validate a value.
	 * Performs validation based on the different configurations
	 * that were parsed for the column by the frontend
	 */
	public static class DateDataBuilder {
		private LocalDate value;

		private DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

		/**
		 * Sets the value of the resulting Date Object
		 * @param value The String value to be set
		 * @param configuration The ColumnConfiguration object for the column
		 * @return DateDataBuilder (this)
		 * @throws Exception if validation is failed
		 */
		public DateDataBuilder setValue(String value, ColumnConfiguration configuration) throws Exception {
			processConfigurations(configuration.getConfigurations());

			try {
				this.value = LocalDate.parse(value, formatter);
				return this;
			} catch(Exception e) {
				throw new DateFormatException();
			}

		}

		/**
		 * Builds the DateData Object.
		 * Only to be called after setValue()
		 * @return new DateData object
		 */
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
			this.formatter = configuration.getDateFormatter();
		}
	}
}
