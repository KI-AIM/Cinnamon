package de.kiaim.cinnamon.model.data;

import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
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
public class TextData extends Data {

	@Nullable
	private final String value;

	@Override
	public DataType getDataType() {
		return DataType.TEXT;
	}

	public static class TextDataBuilder implements DataBuilder {
		private static final int TEXT_LENGTH_THRESHOLD = 256;
		private String value;

		@Override
		public DataType getDataType() {
			return DataType.TEXT;
		}

		@Override
		public TextDataBuilder setValue(String value, List<Configuration> configuration) throws StringPatternException {
			processConfigurations(value, configuration);
			this.value = value;
			return this;
		}

		@Override
		public TextData build() {
			return new TextData(this.value);
		}

		@Override
		public TextData buildNull() {
			return new TextData(null);
		}

		@Override
		public ColumnConfiguration estimateColumnConfiguration(final String value) {
			final var columnConfiguration = new ColumnConfiguration();
			if (value.length() > TEXT_LENGTH_THRESHOLD) {
				columnConfiguration.setType(getDataType());
			}
			return columnConfiguration;
		}

		private void processConfigurations(String value, List<Configuration> configurationList) throws StringPatternException {
			// TODO: TextEncodingConfiguration is currently metadata-only.
			// Real charset conversion/re-encoding of text values is not implemented yet.
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
