package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Request to send a test mail using the configured mail settings.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request to send a test mail using the configured mail settings.")
@Getter @Setter
public class TestMailRequest {

	@Schema(description = "Mail address the test mail is sent to.", example = "test@example.com")
	@NotBlank(message = "Mail address must not be blank.")
	@Email(message = "Mail address must be a valid email address.")
	private String mailAddress;

}
