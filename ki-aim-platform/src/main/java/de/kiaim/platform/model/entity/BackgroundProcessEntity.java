package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.configuration.KiAimConfiguration;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.lang.Nullable;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Data @NoArgsConstructor
public class BackgroundProcessEntity {

	/**
	 * ID of the process.
	 */
	@Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
	private Long id;

	/**
	 * UUID used for the callback URL.
	 * Null if the process is not running.
	 */
	@Nullable
	private UUID uuid;

	/**
	 * If this process should be skipped.
	 */
	@Column(nullable = false)
	private boolean skip = false;

	/**
	 * The endpoint used for this process.
	 * Index is based on the list {@link KiAimConfiguration#getExternalServerEndpoints()}
	 */
	@Column(nullable = false)
	private int endpoint;

	/**
	 * The status of the external processing.
	 */
	@Getter @Setter
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ProcessStatus externalProcessStatus = ProcessStatus.NOT_STARTED;

	/**
	 * Time when the process has been scheduled.
	 * Null if status is not SCHEDULED.
	 */
	@Setter
	@Nullable
	private Timestamp scheduledTime;

	/**
	 * URL to start the process.
	 * Null if status is not SCHEDULED.
	 */
	@Setter
	@Nullable
	private String processUrl;

	/**
	 * Process id in the module.
	 */
	@Setter
	@Nullable
	private String externalId;

	/**
	 * Additional files created during the process.
	 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "background_process_result_files", joinColumns = @JoinColumn(name = "background_process_id"))
	@MapKeyColumn(name = "filename")
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "lob_id")
	private final Map<String, LobWrapperEntity> resultFiles = new HashMap<>();

	@OneToOne(optional = false, orphanRemoval = false, cascade = CascadeType.ALL)
	private ProcessOwner owner;

	@Nullable
	public String getConfiguration() {
		return null;
	}

	public ProjectEntity getProject() {
		return owner.getProject();
	}
}
