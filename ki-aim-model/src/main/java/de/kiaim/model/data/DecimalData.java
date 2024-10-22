package de.kiaim.model.data;

import de.kiaim.model.configuration.data.Configuration;
import de.kiaim.model.configuration.data.RangeConfiguration;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.exception.FloatFormatException;
import de.kiaim.model.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DecimalData extends Data {

	@Nullable
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

		private float minValue = -Float.MAX_VALUE;
		private float maxValue = Float.MAX_VALUE;

		/**
		 * Sets the value of the resulting Decimal Object
		 * @param value The String value to be set
		 * @param configuration The List of Configuration objects for the column
		 * @return DecimalDataBuilder (this)
		 * @throws FloatFormatException if the given value could not be transformed into a float.
		 * @throws ValueNotInRangeException if the transformed float is not in the configured range.
		 */
		@Override
		public DecimalDataBuilder setValue(String value, List<Configuration> configuration)
				throws FloatFormatException, ValueNotInRangeException {
			processConfigurations(configuration);

			final float parsedValue;
			try {
				parsedValue = Float.parseFloat(value);
			} catch (Exception e) {
				throw new FloatFormatException();
			}

			if (parsedValue < minValue || parsedValue > maxValue) {
				throw new ValueNotInRangeException();
			}

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

		/**
		 * Builds the DecimalData object containing a null value.
		 * @return the new DecimalData object.
		 */
		@Override
		public DecimalData buildNull() {
			return new DecimalData(null);
		}

		private void processConfigurations(List<Configuration> configurationList) {
			for (Configuration configuration : configurationList) {
				if (configuration instanceof RangeConfiguration) {
					processRangeConfiguration((RangeConfiguration) configuration);
				}
			}
		}

		private void processRangeConfiguration(RangeConfiguration rangeConfiguration) {
			minValue = rangeConfiguration.getMinValue().asDecimal();
			maxValue = rangeConfiguration.getMaxValue().asDecimal();
		}
	}
}
