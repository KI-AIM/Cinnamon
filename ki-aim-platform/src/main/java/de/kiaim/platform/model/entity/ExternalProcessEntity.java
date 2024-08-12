package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a planned or running external process like the anonymization.
 */
@Entity
@Getter
@NoArgsConstructor
public class ExternalProcessEntity {

	/**
	 * ID of the process.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Associated step of the process.
	 */
	@Setter
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Step step;

	/**
	 * The status of the external processing.
	 */
	@Setter
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ProcessStatus externalProcessStatus = ProcessStatus.NOT_STARTED;

	/**
	 * Process id in the module.
	 */
	@Setter
	private String externalId;

	/**
	 * The corresponding project.
	 */
	@ManyToOne(fetch = FetchType.EAGER)
	@Setter
	private ProjectEntity project;

	/**
	 * The result data set.
	 */
	@Lob
	@Setter
	private byte[] resultDataSet;

	/**
	 * Additional files created during the process.
	 */
	@ElementCollection(fetch = FetchType.LAZY)
	@MapKeyColumn(name = "filename")
	@Lob
	private final Map<String, byte[]> additionalResultFiles = new HashMap<>();

}
