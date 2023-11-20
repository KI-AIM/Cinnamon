package de.kiaim.platform.model.data;

import de.kiaim.platform.model.DataConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StringData extends Data {

	private final String value;

	@Override
	public DataType getDataType() {
		return DataType.STRING;
	}


	public static class StringDataBuilder {
		private String value;

		public StringDataBuilder setValue(String value, DataConfiguration configuration) throws Exception {
			//TODO: Add validation and return custom errors here
			this.value = value;
			return this;
		}

		public StringData build() {
			return new StringData(this.value);
		}
	}
}
