package de.kiaim.platform.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SynthetizationResponse {

	private String message;

	private String pid;

	@JsonProperty(value = "session_key")
	private Long sessionKey;
}
