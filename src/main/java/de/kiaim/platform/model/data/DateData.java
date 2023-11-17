package de.kiaim.platform.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class DateData extends Data {

	private final LocalDate value;

	@Override public DataType getDataType() {
		return DataType.DATE;
	}

	public static class DateDataBuilder {
		private LocalDate value;

		public DateDataBuilder setValue(String value) throws Exception {
			//TODO: Add validation and return custom errors here
			try {
				this.value = LocalDate.parse(value);
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
