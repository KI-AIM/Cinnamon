package de.kiaim.platform.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

		public DateTimeDataBuilder setValue(String value) throws Exception {
			//TODO: Add validation and return custom errors here
			try {
				this.value = LocalDateTime.parse(value);
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
