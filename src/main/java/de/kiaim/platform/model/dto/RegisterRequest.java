package de.kiaim.platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class RegisterRequest {

	@NotBlank
	private String email;

	@NotBlank
	private String password;
}
