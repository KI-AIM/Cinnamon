package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.Data;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class RangeConfiguration implements Configuration {
	private final Data minValue;
	private final Data maxValue;
}
