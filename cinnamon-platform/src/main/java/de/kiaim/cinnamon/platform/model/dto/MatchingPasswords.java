package de.kiaim.cinnamon.platform.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.cinnamon.platform.model.validation.PasswordMatches;

/**
 * Interface for DTOs that contain two password fields that need to match.
 *
 * @author Daniel Preciado-Marquez
 */
@PasswordMatches
public interface MatchingPasswords {
	String getPassword();
	String getPasswordRepeated();

	@JsonIgnore
	default String getPasswordRepeatedFieldName() {
		return "passwordRepeated";
	}
}
