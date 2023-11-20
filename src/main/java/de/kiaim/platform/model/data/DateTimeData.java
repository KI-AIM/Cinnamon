package de.kiaim.platform.model.data;

import de.kiaim.platform.model.DataConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

		private final DateTimeFormatter standardFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

		public DateTimeDataBuilder setValue(String value, DataConfiguration configuration) throws Exception {
			//TODO: Add validation and return custom errors here
			try {
				if (configuration.getDateTimeFormatter() != null) {
					this.value = LocalDateTime.parse(value, configuration.getDateTimeFormatter());
				} else {
					this.value = LocalDateTime.parse(value, standardFormat);
				}
				return this;
			} catch(Exception e) {
				throw new Exception("Could not parse date time", e);
			}
		}

		public DateTimeData build() {
			return new DateTimeData(this.value);
		}
	}
}
