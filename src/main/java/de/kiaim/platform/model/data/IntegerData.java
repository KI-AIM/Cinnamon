package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.data.exception.IntFormatException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class IntegerData extends Data {

	private final Integer value;

	@Override
	public DataType getDataType() {
		return DataType.INTEGER;
	}


	public static class IntegerDataBuilder {
		private int value;

		public IntegerDataBuilder setValue(String value, ColumnConfiguration configuration) throws Exception {
			try {
				this.value = Integer.parseInt(value);
				return this;
			} catch(Exception e) {
				throw new IntFormatException();
			}
		}

		public IntegerData build() {
			return new IntegerData(this.value);
		}
	}
}
