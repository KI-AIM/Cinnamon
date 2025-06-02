package de.kiaim.cinnamon.platform.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.platform.exception.UnhealthyException;
import de.kiaim.cinnamon.platform.model.configuration.ExternalServer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Health indicator that checks if all external servers are healthy.
 *
 * @author Daniel Preciado-Marquez
 */
public class ExternalServerHealthIndicator implements HealthIndicator {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ExternalServer externalServer;
	private final WebClient webClient;

	public ExternalServerHealthIndicator(ExternalServer externalServer) {
		this.externalServer = externalServer;
		webClient = WebClient.builder().baseUrl(externalServer.getUrlServer()).build();
	}

	/**
	 * Checks if the external server is healthy.
	 * @return Health object.
	 */
	@Override
	public Health health() {
		Health.Builder builder = Health.up();

		var healthEndpoint = externalServer.getHealthEndpoint();
		try {
			var response = webClient.method(HttpMethod.GET)
			                        .uri(healthEndpoint)
			                        .retrieve()
			                        .onStatus(HttpStatusCode::isError,
			                                  errorResponse -> errorResponse.toEntity(String.class)
			                                                                .map(r -> this.buildErrorResponse(r,
			                                                                                                  errorResponse.statusCode())))
			                        .bodyToMono(Map.class)
			                        .block();

			var status = (response != null && response.containsKey("status"))
			             ? (String) response.get("status")
			             : Status.UNKNOWN.getCode();
			builder.withDetail("ping", "UP");
			builder.withDetail("health", status);

			builder.status(status);
		} catch (UnhealthyException e) {
			builder.withDetail("ping", "UP");
			builder.withDetail("health", e.getStatus());
			builder.withDetail("error", e.getStatusCode().toString());

			builder.status(e.getStatus());
		} catch (Exception e) {
			builder.withDetail("ping", "DOWN");
			builder.withDetail("health", "DOWN");

			builder.down(e);
		}

		builder.withDetail("url", externalServer.getUrlServer());
		builder.withDetail("healthEndpoint", healthEndpoint);

		return builder.build();
	}

	/**
	 * Handles health checks responses with error HTTP status codes.
	 * @param response The response body.
	 * @return An exception.
	 */
	private UnhealthyException buildErrorResponse(final ResponseEntity<String> response, final HttpStatusCode statusCode) {
		var status = externalServer.getHealthEndpoint().isBlank()
		             ? Status.UNKNOWN.getCode()
		             : Status.DOWN.getCode();
		try {
			var body = objectMapper.readValue(response.getBody(), Map.class);
			if (body != null && body.containsKey("status")) {
				// Status can also be the HTTP Status of an error response
				if (body.get("status") instanceof String bodyStatus) {
					status = bodyStatus;
				}
			}
		} catch (JsonProcessingException ignored) {
		}

		return new UnhealthyException(status, statusCode);
	}

}
