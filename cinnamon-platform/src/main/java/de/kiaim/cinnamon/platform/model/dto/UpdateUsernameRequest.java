package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.validation.Username;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request for updating the username of an existing user.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request for updating the username of an existing user.")
@Data
public class UpdateUsernameRequest {

	@Schema(description = "Current password of the user.", example = "changeme")
	@NotBlank(message = "Current password is required.")
	private String currentPassword;

	@Schema(description = "New username for the user.", example = "newuser")
	@Username
	private String newUsername;
}
