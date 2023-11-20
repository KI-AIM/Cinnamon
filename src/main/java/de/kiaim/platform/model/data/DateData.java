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

	public static class DateDataBuilder {
		private LocalDate value;

		private DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

		public DateDataBuilder setValue(String value, ColumnConfiguration columnConfiguration) throws Exception {
			processConfigurations(columnConfiguration.getConfigurations());

			try {
				this.value = LocalDate.parse(value, formatter);
				return this;
			} catch(Exception e) {
				throw new DateFormatException();
			}

		}

		public DateData build() {
			return new DateData(this.value);
		}

		private void processConfigurations(List<Configuration> configurationList) {
			for (Configuration configuration : configurationList) {
				if (configuration instanceof DateFormatConfiguration) {
					processDateFormatConfiguration((DateFormatConfiguration) configuration);
				}
			}
		}

		private void processDateFormatConfiguration(DateFormatConfiguration configuration) {
			this.formatter = configuration.getDateFormatter();
		}
	}
}
