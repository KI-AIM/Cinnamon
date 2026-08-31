package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A language that can be configured for an email template.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "A language that can be configured for an email template.")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class SupportedLanguageDTO {

	@Schema(description = "The name of the language as used in the API.", example = "ENGLISH")
	private String name;

	@Schema(description = "The name of the language as it should be presented to the user.", example = "English")
	private String displayName;

}
