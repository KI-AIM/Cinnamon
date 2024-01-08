package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.Data;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RangeConfiguration implements Configuration {
	private final Data minValue;
	private final Data maxValue;
}
