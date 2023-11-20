package de.kiaim.platform.model.data;

import de.kiaim.platform.model.DataConfiguration;
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

		public DecimalDataBuilder setValue(String value, DataConfiguration configuration) throws Exception {
			//TODO: Add validation and return custom errors here
			try {
				this.value = Float.parseFloat(value);
				return this;
			} catch (Exception e) {
				throw new Exception("Could not parse float", e);
			}
		}

		public DecimalData build() {
			return new DecimalData(this.value);
		}
	}
}
