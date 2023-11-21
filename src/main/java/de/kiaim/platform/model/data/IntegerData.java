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


	/**
	 * Builder pattern to set and validate a value.
	 * Performs validation based on the different configurations
	 * that were parsed for the column by the frontend
	 */
	public static class IntegerDataBuilder {
		private int value;

		/**
		 * Sets the value of the resulting Integer Object
		 * @param value The String value to be set
		 * @param configuration The ColumnConfiguration object for the column
		 * @return IntegerDataBuilder (this)
		 * @throws Exception if validation is failed
		 */
		public IntegerDataBuilder setValue(String value, ColumnConfiguration configuration) throws Exception {
			try {
				this.value = Integer.parseInt(value);
				return this;
			} catch(Exception e) {
				throw new IntFormatException();
			}
		}

		/**
		 * Builds the IntegerData Object.
		 * Only to be called after setValue()
		 * @return new IntegerData object
		 */
		public IntegerData build() {
			return new IntegerData(this.value);
		}
	}
}
