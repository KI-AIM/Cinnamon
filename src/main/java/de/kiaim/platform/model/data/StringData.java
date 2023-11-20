package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.*;
import de.kiaim.platform.model.data.exception.StringPatternException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class StringData extends Data {

	private final String value;

	@Override
	public DataType getDataType() {
		return DataType.STRING;
	}


	public static class StringDataBuilder {
		private String value;

		public StringDataBuilder setValue(String value, ColumnConfiguration configuration) throws Exception {
			processConfigurations(value, configuration.getConfigurations());

			this.value = value;
			return this;
		}

		public StringData build() {
			return new StringData(this.value);
		}

		private void processConfigurations(String value, List<Configuration> configurationList) throws StringPatternException {
			for (Configuration configuration : configurationList) {
				if (configuration instanceof StringPatternConfiguration) {
					processStringPatternConfiguration(value, (StringPatternConfiguration) configuration);
				}
			}
		}

		private void processStringPatternConfiguration(String value, StringPatternConfiguration stringPatternConfiguration) throws StringPatternException {
			if (!value.matches(stringPatternConfiguration.getPattern())) {
				throw new StringPatternException();
			}
		}
	}
}
