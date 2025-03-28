package de.kiaim.cinnamon.model.data;

import de.kiaim.cinnamon.model.configuration.data.Configuration;
import de.kiaim.cinnamon.model.configuration.data.StringPatternConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.StringPatternException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class StringData extends Data {

	@Nullable
	private final String value;

	@Override
	public DataType getDataType() {
		return DataType.STRING;
	}


	/**
	 * Builder pattern to set and validate a value.
	 * Performs validation based on the different configurations
	 * that were parsed for the column by the frontend
	 */
	public static class StringDataBuilder implements DataBuilder {
		private String value;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public DataType getDataType() {
			return DataType.STRING;
		}

		/**
		 * Sets the value of the resulting StringData Object
		 * @param value The value to be set
		 * @param configuration The List of Configuration objects for the column
		 * @return StringDataBuilder (this)
		 * @throws StringPatternException if validation is failed
		 */
		@Override
		public StringDataBuilder setValue(String value, List<Configuration> configuration) throws StringPatternException {
			processConfigurations(value, configuration);

			this.value = value;
			return this;
		}

		/**
		 * Builds the StringData Object.
		 * Only to be called after setValue()
		 * @return new StringData object
		 */
		@Override
		public StringData build() {
			return new StringData(this.value);
		}

		/**
		 * Builds the StringData object containing a null value.
		 * @return the new StringData object.
		 */
		@Override
		public StringData buildNull() {
			return new StringData(null);
		}

		/**
		 * Processes the parsed configurations one by one for validation
		 * @param value The value that should be validated
		 * @param configurationList A List of different Configuration objects
		 * @throws StringPatternException if validation fails
		 */
		private void processConfigurations(String value, List<Configuration> configurationList) throws StringPatternException {
			for (Configuration configuration : configurationList) {
				if (configuration instanceof StringPatternConfiguration) {
					processStringPatternConfiguration(value, (StringPatternConfiguration) configuration);
				}
			}
		}

		/**
		 * Processes an existing StringPatternConfiguration.
		 * The String pattern is a Regex that conducts, which
		 * pattern a String should match to.
		 * @param value The value to be matched against the pattern
		 * @param stringPatternConfiguration the StringPatternConfiguration with the pattern
		 * @throws StringPatternException if the value does not match the pattern
		 */
		private void processStringPatternConfiguration(String value, StringPatternConfiguration stringPatternConfiguration) throws StringPatternException {
			if (!value.matches(stringPatternConfiguration.getPattern())) {
				throw new StringPatternException();
			}
		}
	}
}
