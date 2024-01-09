package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.*;
import de.kiaim.platform.model.data.exception.IntFormatException;
import de.kiaim.platform.model.data.exception.ValueNotInRangeException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
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
	public static class IntegerDataBuilder implements DataBuilder {
		private int value;

		private int minValue = Integer.MIN_VALUE;
		private int maxValue = Integer.MAX_VALUE;

		/**
		 * Sets the value of the resulting Integer Object
		 * @param value The String value to be set
		 * @param configuration The List of Configuration objects for the column
		 * @return IntegerDataBuilder (this)
		 * @throws Exception if validation is failed
		 */
		@Override
		public IntegerDataBuilder setValue(String value, List<Configuration> configuration) throws Exception {
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
