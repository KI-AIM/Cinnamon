package de.kiaim.platform.exception;

import de.kiaim.model.dto.ExternalProcessResponse;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Getter
public class RequestRuntimeException extends RuntimeException {

	private final ResponseEntity<ExternalProcessResponse> response;

	public RequestRuntimeException(final ResponseEntity<ExternalProcessResponse> response) {
		this.response = response;
	}
}
