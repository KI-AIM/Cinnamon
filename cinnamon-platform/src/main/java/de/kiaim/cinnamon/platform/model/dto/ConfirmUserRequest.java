package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Username and password authentication for an already authenticated user.
 * Do not use this for Springs authentication!
 *
 * @author Daniel Preciado-Marquez
 */
@Data
@AllArgsConstructor
public class ConfirmUserRequest {

	/**
	 * Username of the user.
	 */
	@NotBlank(message = "Username is required!")
	@Schema(description = "Username of the user.", example = "john_doe")
	private String username;

	/**
	 * Password of the user.
	 */
	@NotBlank(message = "Password is required!")
	@Schema(description = "Password of the user.", example = "<PASSWORD>")
	private String password;
}
