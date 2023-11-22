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

	/**
	 * Builder pattern to set and validate a value.
	 * Performs validation based on the different configurations
	 * that were parsed for the column by the frontend
	 */
	public static class DecimalDataBuilder {
		private float value;

		/**
		 * Sets the value of the resulting Decimal Object
		 * @param value The String value to be set
		 * @param configuration The ColumnConfiguration object for the column
		 * @return DecimalDataBuilder (this)
		 * @throws Exception if validation is failed
		 */
		public DecimalDataBuilder setValue(String value, ColumnConfiguration configuration) throws Exception {
			try {
				this.value = Float.parseFloat(value);
				return this;
			} catch (Exception e) {
				throw new FloatFormatException();
			}
		}

		/**
		 * Builds the DecimalData Object.
		 * Only to be called after setValue()
		 * @return new DecimalData object
		 */
		public DecimalData build() {
			return new DecimalData(this.value);
		}
	}
}
