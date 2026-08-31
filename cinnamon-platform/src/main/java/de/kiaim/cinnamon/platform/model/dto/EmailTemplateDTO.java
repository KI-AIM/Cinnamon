package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.validation.UniqueTemplateLanguages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An email template with its content in all configured languages.
 * Used both for retrieving and for creating or updating a template.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "An email template with its content in all configured languages.")
@UniqueTemplateLanguages
@Getter @Setter
public class EmailTemplateDTO {

	@Schema(description = "The ID of the template. Only part of a response, ignored when creating or updating "
	                      + "a template.",
	        example = "1",
	        accessMode = Schema.AccessMode.READ_ONLY)
	private Long id;

	@Schema(description = "The unique name of the template.", example = "Registration confirmation")
	@NotBlank(message = "Name must not be blank.")
	private String name;

	@Schema(description = "The content of the template. Contains at most one entry per language. Languages that "
	                      + "are not part of the request are removed from the template.")
	@NotEmpty(message = "The template must be configured for at least one language.")
	@Valid
	private List<EmailTemplateItemDTO> items = new ArrayList<>();

}
