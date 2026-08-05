package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * General information about a user.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "General information about a user.")
@RequiredArgsConstructor
@Getter
public class UserInfo {

	/**
	 * The username of the user.
	 */
	@Schema(description = "The username of the user.", example = "john_doe")
	private final String username;

	/**
	 * The roles of the user.
	 */
	@Schema(description = "The roles of the user.")
	private final Set<UserRole> roles;

}
