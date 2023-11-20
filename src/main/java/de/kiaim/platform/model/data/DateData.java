package de.kiaim.platform.model.data;

import de.kiaim.platform.model.DataConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Getter
@AllArgsConstructor
public class DateData extends Data {

	private final LocalDate value;

	@Override public DataType getDataType() {
		return DataType.DATE;
	}

	public static class DateDataBuilder {
		private LocalDate value;

		private final DateTimeFormatter standardFormat = DateTimeFormatter.ISO_LOCAL_DATE;

		public DateDataBuilder setValue(String value, DataConfiguration configuration) throws Exception {
			//TODO: Add validation and return custom errors here
			try {
				if (configuration.getDateFormatter() != null) {
					this.value = LocalDate.parse(value, configuration.getDateFormatter());
				} else {
					this.value = LocalDate.parse(value, standardFormat);
				}

				return this;
			} catch(Exception e) {
				throw new Exception("Could not parse date", e);
			}
		}

		public DateData build() {
			return new DateData(this.value);
		}
	}
}
