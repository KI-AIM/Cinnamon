package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.data.exception.BooleanFormatException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BooleanData extends Data {

	private final Boolean value;

	@Override
	public DataType getDataType() {
		return DataType.BOOLEAN;
	}

	public static class BooleanDataBuilder{

		private boolean value;

		public BooleanDataBuilder setValue(String value, ColumnConfiguration configuration) throws Exception {
			if (value.equalsIgnoreCase("yes") || value.equals("1") || value.equalsIgnoreCase("true")) {
				this.value = true;
			} else if (value.equalsIgnoreCase("no") || value.equalsIgnoreCase("0") || value.equalsIgnoreCase("false")) {
				this.value = false;
			} else {
				throw new BooleanFormatException();
			}
			return this;
		}

		public BooleanData build() {
			return new BooleanData(this.value);
		}
	}
}
