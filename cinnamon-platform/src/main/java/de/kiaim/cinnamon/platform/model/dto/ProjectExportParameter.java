package de.kiaim.cinnamon.platform.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Parameter for a project export.
 *
 * @author Daniel Preciado-Marquez
 */
@Data
public class ProjectExportParameter {
	/**
	 * If all configurations should be bundled into one file.
	 */
	private final boolean bundleConfigurations;

	/**
	 * Names of the configurations to export.
	 */
	private final List<String> configurationNames;

	/**
	 * Results to export.
	 * Each entry must be in the form: [pipeline].[stage].[job].[dataset | statistics | other] or [original].[dataset | statistics]
	 */
	private final List<String> results;
}
