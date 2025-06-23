package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.file.FileType;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter for a project export.
 *
 * @author Daniel Preciado-Marquez
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class ProjectExportParameter {
	/**
	 * If all configurations should be bundled into one file.
	 */
	private boolean bundleConfigurations = true;

	/**
	 * The target type for the exported dataset.
	 */
	private FileType datasetFileType = FileType.CSV;

	/**
	 * Hold-out selector of the original dataset.
	 */
	private HoldOutSelector holdOutSelector = HoldOutSelector.ALL;

	/**
	 * Results to export.
	 * Each entry must have one of the following forms:
	 * <ul>
	 *     <li>configuration.[name]</li>
	 *     <li>pipeline.[stage].[job].['dataset' | 'statistics' | 'other']</li>
	 *     <li>original.['dataset' | 'statistics']</li>
	 * </ul>
	 */
	private List<String> resources = new ArrayList<>();
}
