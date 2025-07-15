package de.kiaim.cinnamon.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Content for the report.
 * All strings are HTML strings.
 * <p>
 * Supported tags are: `&ltp&gt`
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Report content for a module.")
@AllArgsConstructor @NoArgsConstructor @Getter @Setter
public class ModuleReportContent {

	/**
	 * HTML containing a textual description of the configuration.
	 */
	@Schema(description = "HTML containing a textual description of the configuration.",
	        example = "<p>The following algorithm was applied to the dataset</p><p>For this, these parameters were used.</p>")
	private String configDescription;

	/**
	 * HTML content for the glossar of the report.
	 */
	@Schema(description = "HTML content for the glossar of the report.",
	        example = "<p>The following terms are used in the report.</p>")
	@Nullable
	private String glossar;
}
