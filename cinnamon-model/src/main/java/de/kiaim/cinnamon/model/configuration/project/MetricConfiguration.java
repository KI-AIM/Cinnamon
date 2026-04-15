package de.kiaim.cinnamon.model.configuration.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for metrics to be used in the project.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configuration for metrics to be used in the project.")
@NoArgsConstructor
@Getter @Setter
@EqualsAndHashCode
public class MetricConfiguration {

	/**
	 * Color scheme to be used for visualizations.
	 * Valid values are defined by the frontend.
	 * If {@code null}, the default color scheme is used.
	 */
	@Schema(description = "Color scheme to be used for visualizations. Values are defined by the frontend. If null, the default color scheme is used.",
	        example = "red-blue")
	@Nullable
	private String colorScheme = null;

	/**
	 * If the user-defined importance should be used.
	 */
	@Schema(description = "If the user-defined importance should be used.")
	@NotNull
	private Boolean useUserDefinedImportance = false;

	/**
	 * Map of user-defined metric importance.
	 * Contains the metrics provided by the statistics endpoint.
	 */
	@Schema(description = "Map of user-defined metric importance. Contains the metrics provided by the statistics endpoint.")
	private final Map<String, MetricImportance> userDefinedImportance = new HashMap<>();

}
