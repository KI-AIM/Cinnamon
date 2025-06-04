package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.validation.EmailAvailable;
import de.kiaim.cinnamon.platform.model.validation.PasswordMatches;
import de.kiaim.cinnamon.platform.model.validation.PasswordRequirements;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "Request for registering a new user.")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@PasswordMatches
public class RegisterRequest {

	@Schema(description = "Email address of the user.", example = "mail@example.de")
	@NotBlank
	@EmailAvailable
	private String email;

	@Schema(description = "Password of the user.", example = "changeme")
	@PasswordRequirements
	private String password;

	@Schema(description = "Repeated password of the user.", example = "changeme")
	private String passwordRepeated;
}
