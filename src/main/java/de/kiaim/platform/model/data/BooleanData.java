package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.Configuration;
import de.kiaim.platform.model.data.exception.BooleanFormatException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class BooleanData extends Data {

	private final Boolean value;

	@Override
	public DataType getDataType() {
		return DataType.BOOLEAN;
	}

	/**
	 * Builder pattern to set and validate a value.
	 * Performs validation based on the different configurations
	 * that were parsed for the column by the frontend
	 */
	public static class BooleanDataBuilder implements DataBuilder{

		private boolean value;

		/**
		 * Sets the value of the resulting Boolean Object
		 * The value is only set, if it is a valid Boolean
		 * format:
		 * "yes" or "no", "1" or "0", "true" or "false"
		 * @param value The String value to be set
		 * @param configuration The List of Configuration objects for the column
		 * @return BooleanDataBuilder (this)
		 * @throws Exception if value does match the Boolean pattern
		 */
		@Override
		public BooleanDataBuilder setValue(String value, List<Configuration> configuration) throws Exception {
			if (value.equalsIgnoreCase("yes") || value.equals("1") || value.equalsIgnoreCase("true")) {
				this.value = true;
			} else if (value.equalsIgnoreCase("no") || value.equalsIgnoreCase("0") || value.equalsIgnoreCase("false")) {
				this.value = false;
			} else {
				throw new BooleanFormatException();
			}
			return this;
		}

		/**
		 * Builds the BooleanData Object.
		 * Only to be called after setValue()
		 * @return new BooleanData object
		 */
		@Override
		public BooleanData build() {
			return new BooleanData(this.value);
		}
	}
}
