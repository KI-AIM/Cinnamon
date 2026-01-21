package de.kiaim.cinnamon.model.enumeration;

import lombok.Getter;

import java.util.Set;

/**
 * Data types supported by Cinnamon.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter
public enum DataType {
	BOOLEAN(DataScale.NOMINAL, Set.of(DataScale.NOMINAL)),
	DATE_TIME(DataScale.DATE, Set.of(DataScale.DATE)),
	DECIMAL(DataScale.RATIO, Set.of(DataScale.NOMINAL, DataScale.ORDINAL, DataScale.INTERVAL, DataScale.RATIO)),
	INTEGER(DataScale.INTERVAL, Set.of(DataScale.NOMINAL, DataScale.ORDINAL, DataScale.INTERVAL)),
	STRING(DataScale.NOMINAL, Set.of(DataScale.NOMINAL, DataScale.ORDINAL)),
	DATE(DataScale.DATE, Set.of(DataScale.DATE)),
	UNDEFINED(DataScale.NOMINAL, Set.of());

	DataType(final DataScale defaultScale, final Set<DataScale> supportedScales) {
		this.defaultScale = defaultScale;
		this.supportedScales = supportedScales;
	}

	/**
	 * The default data sale for the data type.
	 */
	private final DataScale defaultScale;

	/**
	 * Set of data scales which can be used in combination with the data type.
	 */
	private final Set<DataScale> supportedScales;
}
