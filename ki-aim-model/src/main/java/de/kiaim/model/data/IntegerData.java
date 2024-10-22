package de.kiaim.model.data;

import de.kiaim.model.configuration.data.Configuration;
import de.kiaim.model.configuration.data.RangeConfiguration;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.exception.IntFormatException;
import de.kiaim.model.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class IntegerData extends Data {

	@Nullable
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
	public static class IntegerDataBuilder implements DataBuilder {
		private int value;

		private int minValue = Integer.MIN_VALUE;
		private int maxValue = Integer.MAX_VALUE;

		/**
		 * Sets the value of the resulting Integer Object
		 * @param value The String value to be set
		 * @param configuration The List of Configuration objects for the column
		 * @return IntegerDataBuilder (this)
		 * @throws IntFormatException if the given value could not be transformed into an integer.
		 * @throws ValueNotInRangeException if the transformed integer is not in the configured range.
		 */
		@Override
		public IntegerDataBuilder setValue(String value, List<Configuration> configuration)
				throws IntFormatException, ValueNotInRangeException {
			processConfigurations(configuration);

			final int parsedValue;
			try {
				parsedValue = Integer.parseInt(value);
			} catch(Exception e) {
				throw new IntFormatException();
			}

			if (parsedValue < minValue || parsedValue > maxValue) {
				throw new ValueNotInRangeException();
			}

			this.value = parsedValue;
			return this;
		}

		/**
		 * Builds the IntegerData Object.
		 * Only to be called after setValue()
		 * @return new IntegerData object
		 */
		@Override
		public IntegerData build() {
			return new IntegerData(this.value);
		}

		/**
		 * Builds the IntegerData object containing a null value.
		 * @return the new IntegerData object.
		 */
		@Override
		public IntegerData buildNull() {
			return new IntegerData(null);
		}

		private void processConfigurations(List<Configuration> configurationList) {
			for (Configuration configuration : configurationList) {
				if (configuration instanceof RangeConfiguration) {
					processRangeConfiguration((RangeConfiguration) configuration);
				}
			}
		}

		private void processRangeConfiguration(RangeConfiguration rangeConfiguration) {
			minValue = rangeConfiguration.getMinValue().asInteger();
			maxValue = rangeConfiguration.getMaxValue().asInteger();
		}
	}
}
