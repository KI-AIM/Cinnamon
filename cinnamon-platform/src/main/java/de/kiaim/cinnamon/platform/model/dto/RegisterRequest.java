package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.validation.Username;
import de.kiaim.cinnamon.platform.model.validation.PasswordRequirements;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Request for registering a new user.")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class RegisterRequest implements MatchingPasswords {

	@Schema(description = "Username of the user.", example = "john_doe")
	@Username
	private String username;

	@Schema(description = "Email address of the user.", example = "john_doe@example.com")
	@Email
	private String email;

	@Schema(description = "Password of the user.", example = "changeme")
	@PasswordRequirements
	private String password;

	@Schema(description = "Repeated password of the user.", example = "changeme")
	private String passwordRepeated;
}
