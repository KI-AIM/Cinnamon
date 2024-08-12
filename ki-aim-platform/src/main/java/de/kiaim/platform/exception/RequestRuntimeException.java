package de.kiaim.platform.exception;

import de.kiaim.platform.model.dto.SynthetizationResponse;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Getter
public class RequestRuntimeException extends RuntimeException {

	private final ResponseEntity<SynthetizationResponse> response;

	public RequestRuntimeException(final ResponseEntity<SynthetizationResponse> response) {
		this.response = response;
	}
}
