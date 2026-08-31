package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * All email templates of the application together with the languages that can be configured for them.
 * The languages are part of the response so that clients do not have to know the supported languages
 * and automatically support languages added in the future.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "All email templates of the application and the languages that can be configured for them.")
@Getter @Setter
public class EmailTemplateListDTO {

	@Schema(description = "All languages that can be configured for a template.")
	private List<SupportedLanguageDTO> languages = new ArrayList<>();

	@Schema(description = "All email templates, sorted by their name.")
	private List<EmailTemplateDTO> templates = new ArrayList<>();

}
