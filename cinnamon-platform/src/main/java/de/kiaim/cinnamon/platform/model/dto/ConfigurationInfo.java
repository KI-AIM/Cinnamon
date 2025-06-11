package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * General information about a configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "General information about a configuration.")
@Getter @AllArgsConstructor
public class ConfigurationInfo {
	@Schema(description = "Information about all processes that use this configuration.")
	private final List<ProcessInfo> processes = new ArrayList<>();
}
