package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a planned or running external process like the anonymization.
 */
@Schema(description = "Information about an external process.")
@Entity
@Getter
@NoArgsConstructor
public class ExternalProcessEntity {

	/**
	 * ID of the process.
	 */
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Associated step of the process.
	 */
	@JsonIgnore
	@Setter
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Step step;

	/**
	 * The status of the external processing.
	 */
	@Schema(description = "The status of the external processing.")
	@Setter
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ProcessStatus externalProcessStatus = ProcessStatus.NOT_STARTED;

	/**
	 * Time when the process has been scheduled.
	 * Null if status is not SCHEDULED.
	 */
	@JsonIgnore
	@Setter
	@Nullable
	private Timestamp scheduledTime;

	/**
	 * URL to start the process.
	 * Null if status is not SCHEDULED.
	 */
	@JsonIgnore
	@Setter
	@Nullable
	private String processUrl;

	/**
	 * Process id in the module.
	 */
	@JsonIgnore
	@Setter
	private String externalId;

	/**
	 * The corresponding execution step.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER)
	@Setter
	private ExecutionStepEntity executionStep;

	/**
	 * Detailed status information.
	 * Can have any form.
	 */
	@Schema(description = "The detailed status object retrieved from the server.")
	@Column(length = 1000)
	@Setter
	private String status;

	/**
	 * Additional files created during the process.
	 */
	@JsonIgnore
	@ElementCollection(fetch = FetchType.LAZY)
	@MapKeyColumn(name = "filename")
	@Lob
	private final Map<String, byte[]> additionalResultFiles = new HashMap<>();
}
