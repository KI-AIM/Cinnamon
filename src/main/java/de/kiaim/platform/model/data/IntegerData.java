package de.kiaim.platform.model.data;

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

		public IntegerDataBuilder setValue(String value) throws Exception {
			//TODO: Add validation and return custom errors here
			try {
				this.value = Integer.parseInt(value);
				return this;
			} catch(Exception e) {
				throw new Exception("Could not parse int", e);
			}
		}

		public IntegerData build() {
			return new IntegerData(this.value);
		}
	}
}
