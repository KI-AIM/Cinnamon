package de.kiaim.platform.model.data;

import de.kiaim.platform.model.data.configuration.DataScale;
import lombok.Getter;

@Getter
public enum DataType {
	BOOLEAN(DataScale.NOMINAL),
	DATE_TIME(DataScale.INTERVAL),
	DECIMAL(DataScale.RATIO),
	INTEGER(DataScale.INTERVAL),
	STRING(DataScale.NOMINAL),
	DATE(DataScale.INTERVAL),
	UNDEFINED(DataScale.NOMINAL);

	DataType(final DataScale defaultScale) {
		this.defaultScale = defaultScale;
	}

	private final DataScale defaultScale;
}
