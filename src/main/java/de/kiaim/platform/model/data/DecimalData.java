package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.Configuration;
import de.kiaim.platform.model.data.configuration.RangeConfiguration;
import de.kiaim.platform.model.data.exception.FloatFormatException;
import de.kiaim.platform.model.data.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

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
	public static class DecimalDataBuilder implements DataBuilder {
		private float value;

		private float minValue = Float.MIN_VALUE;
		private float maxValue = Float.MAX_VALUE;

		/**
		 * Sets the value of the resulting Decimal Object
		 * @param value The String value to be set
		 * @param configuration The List of Configuration objects for the column
		 * @return DecimalDataBuilder (this)
		 * @throws Exception if validation is failed
		 */
		@Override
		public DecimalDataBuilder setValue(String value, List<Configuration> configuration) throws Exception {
			final float parsedValue;
			try {
				parsedValue = Float.parseFloat(value);
			} catch (Exception e) {
				throw new FloatFormatException();
			}

			processConfigurations(parsedValue, configuration);

			this.value = parsedValue;
			return this;
		}

		/**
		 * Builds the DecimalData Object.
		 * Only to be called after setValue()
		 * @return new DecimalData object
		 */
		@Override
		public DecimalData build() {
			return new DecimalData(this.value);
		}

		private void processConfigurations(float value, List<Configuration> configurationList) throws Exception {
			for (Configuration configuration : configurationList) {
				if (configuration instanceof RangeConfiguration) {
					processRangeConfiguration(value, (RangeConfiguration) configuration);
				}
			}
		}

		private void processRangeConfiguration(float value, RangeConfiguration rangeConfiguration) throws ValueNotInRangeException {
			if (value < rangeConfiguration.getMinValue().asDecimal() || value > rangeConfiguration.getMaxValue().asDecimal()) {
				throw new ValueNotInRangeException();
			}
		}
	}
}
