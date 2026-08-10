package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.SupportedLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * The content of an email template for a single language.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "The content of an email template for a single language.")
@Getter @Setter
public class EmailTemplateItemDTO {

	@Schema(description = "The language of the content.", example = "ENGLISH")
	@NotNull(message = "Language must not be null.")
	private SupportedLanguage language;

	@Schema(description = "The subject of the email.", example = "Welcome to Cinnamon")
	@NotBlank(message = "Subject must not be blank.")
	private String subject;

	@Schema(description = "The body of the email.", example = "Hello, welcome to Cinnamon!")
	@NotBlank(message = "Body must not be blank.")
	private String body;

}
