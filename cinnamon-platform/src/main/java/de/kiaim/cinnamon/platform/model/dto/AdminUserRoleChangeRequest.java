package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

/**
 * Request to change roles for a user.
 *
 * @author Daniel Preciado-Marquez
 */
@Data
@Schema(description = "Request to change roles for a user.")
public class AdminUserRoleChangeRequest {

	/**
	 * Username to identify the user to change roles for.
	 */
	@Schema(description = "Username to identify the user to change roles for.", example = "john_doe")
	@NotBlank(message = "Username must not be blank.")
	private String username;

	/**
	 * Whether to add or remove the roles.
	 */
	@Schema(description = "Whether to add or remove the roles.")
	@NotNull(message = "Action must not be null.")
	private Action action;

	/**
	 * The roles to add or remove.
	 */
	@Schema(description = "The roles to add or remove.")
	@NotEmpty(message = "At least one role must be specified.")
	private Set<UserRole> roles;

	/**
	 * The action to perform on the roles.
	 */
	public enum Action {
		ADD,
		REMOVE
	}

}
