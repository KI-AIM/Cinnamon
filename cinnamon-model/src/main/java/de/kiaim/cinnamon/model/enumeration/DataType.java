package de.kiaim.cinnamon.model.enumeration;

import lombok.Getter;

@Getter
public enum DataType {
	BOOLEAN(DataScale.NOMINAL),
	DATE_TIME(DataScale.DATE),
	DECIMAL(DataScale.RATIO),
	INTEGER(DataScale.INTERVAL),
	STRING(DataScale.NOMINAL),
	DATE(DataScale.DATE),
	UNDEFINED(DataScale.NOMINAL);

	DataType(final DataScale defaultScale) {
		this.defaultScale = defaultScale;
	}

	private final DataScale defaultScale;
}
