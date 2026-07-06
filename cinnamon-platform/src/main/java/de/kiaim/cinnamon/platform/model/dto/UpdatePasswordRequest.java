package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.validation.PasswordRequirements;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request for updating the password of an existing user.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request for updating the password of an existing user.")
@Data
public class UpdatePasswordRequest implements MatchingPasswords {

	@Schema(description = "Current password of the user.", example = "changeme")
	@NotBlank(message = "Current password is required.")
	private String currentPassword;

	@Schema(description = "New password of the user.", example = "changeme")
	@PasswordRequirements
	private String newPassword;

	@Schema(description = "Repeated new password of the user.", example = "changeme")
	private String newPasswordRepeated;

	//━━━━━━━━━━━━━━━━━━━━━━━━ Implementation of MatchingPasswords ━━━━━━━━━━━━━━━━━━━━━━━━

	@Override
	public String getPassword() {
		return newPassword;
	}

	@Override
	public String getPasswordRepeated() {
		return newPasswordRepeated;
	}

	@Override
	public String getPasswordRepeatedFieldName() {
		return "newPasswordRepeated";
	}
}
