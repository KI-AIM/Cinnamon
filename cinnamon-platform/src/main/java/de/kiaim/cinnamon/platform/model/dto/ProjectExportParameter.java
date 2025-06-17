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
	 * Names of the configurations to export.
	 */
	private final List<String> configurationNames;
}
