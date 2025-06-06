package de.kiaim.cinnamon.platform.exception;

import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Getter
public class RequestRuntimeException extends RuntimeException {

	private final ResponseEntity<ExternalProcessResponse> response;

	public RequestRuntimeException(final ResponseEntity<ExternalProcessResponse> response) {
		this.response = response;
	}
}
