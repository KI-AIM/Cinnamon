package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.*;
import de.kiaim.platform.model.data.exception.DateTimeFormatException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@AllArgsConstructor
public class DateTimeData extends Data {

	private final LocalDateTime value;

	@Override
	public DataType getDataType() {
		return DataType.DATE_TIME;
	}


	public static class DateTimeDataBuilder {
		private LocalDateTime value;

		private DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

		public DateTimeDataBuilder setValue(String value, ColumnConfiguration columnConfiguration) throws Exception {
			processConfigurations(columnConfiguration.getConfigurations());

			try {
				this.value = LocalDateTime.parse(value, formatter);
				return this;
			} catch(Exception e) {
				throw new DateTimeFormatException();
			}

		}

		public DateTimeData build() {
			return new DateTimeData(this.value);
		}

		private void processConfigurations(List<Configuration> configurationList) {
			for (Configuration configuration : configurationList) {
				if (configuration instanceof DateTimeFormatConfiguration) {
					processDateTimeFormatConfiguration((DateTimeFormatConfiguration) configuration);
				}
			}
		}

		private void processDateTimeFormatConfiguration(DateTimeFormatConfiguration configuration) {
			this.formatter = configuration.getDateTimeFormatter();
		}
	}
}
