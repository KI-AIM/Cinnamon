package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Set;

/**
 * General information about a user.
 *
 * @author Daniel Preciado-Marquez
 */
@RequiredArgsConstructor
@Getter @Setter
public class UserInfo {

	/**
	 * The username of the user.
	 */
	private final String username;

	/**
	 * The roles of the user.
	 */
	private final Set<UserRole> roles;

}
