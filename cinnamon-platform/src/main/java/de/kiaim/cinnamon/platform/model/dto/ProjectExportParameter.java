package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import lombok.*;
import org.springframework.lang.Nullable;

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
	 * Names of the configurations to export.
	 */
	@Nullable
	private List<String> configurationNames = new ArrayList<>();

	/**
	 * Hold-out selector of the original dataset.
	 */
	private HoldOutSelector holdOutSelector = HoldOutSelector.ALL;

	/**
	 * Results to export.
	 * Each entry must be in the form: [pipeline].[stage].[job].[dataset | statistics | other] or [original].[dataset | statistics]
	 */
	private List<String> results = new ArrayList<>();
}
