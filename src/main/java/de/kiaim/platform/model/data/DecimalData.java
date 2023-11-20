package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.data.exception.FloatFormatException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DecimalData extends Data {

	private final Float value;

	@Override
	public DataType getDataType() {
		return DataType.DECIMAL;
	}

	public static class DecimalDataBuilder {
		private float value;

		public DecimalDataBuilder setValue(String value, ColumnConfiguration configuration) throws Exception {
			try {
				this.value = Float.parseFloat(value);
				return this;
			} catch (Exception e) {
				throw new FloatFormatException();
			}
		}

		public DecimalData build() {
			return new DecimalData(this.value);
		}
	}
}
