package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
	@Schema(description = "The session key required for authentication.")
	@JsonProperty(value = "sessionKey")
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
	 * Process id in the module.
	 */
	@JsonIgnore
	@Setter
	private String externalId;

	/**
	 * The corresponding project.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER)
	@Setter
	private ProjectEntity project;

	/**
	 * The result data set.
	 */
	@JsonIgnore
	@Lob
	@Setter
	private byte[] resultDataSet;

	/**
	 * Additional files created during the process.
	 */
	@JsonIgnore
	@ElementCollection(fetch = FetchType.LAZY)
	@MapKeyColumn(name = "filename")
	@Lob
	private final Map<String, byte[]> additionalResultFiles = new HashMap<>();

}
