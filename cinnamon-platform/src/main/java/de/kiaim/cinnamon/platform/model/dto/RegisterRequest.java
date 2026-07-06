package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.validation.UsernameAvailable;
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

	@Schema(description = "Username of the user.", example = "john_doe")
	@NotBlank
	@UsernameAvailable
	private String username;

	@Schema(description = "Password of the user.", example = "changeme")
	@PasswordRequirements
	private String password;

	@Schema(description = "Repeated password of the user.", example = "changeme")
	private String passwordRepeated;
}
