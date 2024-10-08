package de.kiaim.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ExternalProcessResponse {

	private String error;

	private String message;

	private String pid;

	@JsonProperty(value = "session_key")
	private Long sessionKey;
}
