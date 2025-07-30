package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.ExternalServerInstance;
import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.lang.Nullable;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter @Setter
@NoArgsConstructor
public class BackgroundProcessEntity {

	/**
	 * Index of the job within the stage.
	 */
	private Integer jobIndex;

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
	 * Index is based on the list {@link CinnamonConfiguration#getExternalServerEndpoints()}
	 */
	@Column(nullable = false)
	private int endpoint;

	/**
	 * The server instance executing the process.
	 * See {@link ExternalServerInstance#getId()} for the form.
	 * Null if status is not RUNNING.
	 */
	@Nullable
	private String serverInstance;

	/**
	 * The configuration used for the process.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "configuration_id")
	@Nullable
	private BackgroundProcessConfiguration configuration = null;

	/**
	 * The status of the external processing.
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ProcessStatus externalProcessStatus = ProcessStatus.NOT_STARTED;

	/**
	 * Time when the process has been scheduled.
	 * Null if status is not SCHEDULED.
	 */
	@Nullable
	private Timestamp scheduledTime;

	/**
	 * Process id in the module.
	 */
	@Nullable
	private String externalId;

	/**
	 * Additional files created during the process.
	 */
	@CollectionTable(name = "background_process_result_files", joinColumns = @JoinColumn(name = "background_process_id"))
	@MapKeyColumn(name = "filename")
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	private final Map<String, LobWrapperEntity> resultFiles = new HashMap<>();

	/**
	 * Owner of the process.
	 */
	@ManyToOne(optional = false, cascade = CascadeType.PERSIST)
	@JoinColumn(name = "owner_id", nullable = false)
	private ProcessOwner owner;

	/**
	 * Creates a new process for the given owner.
	 *
	 * @param owner The owner of the process.
	 */
	public BackgroundProcessEntity(final ProcessOwner owner) {
		this.owner = owner;
	}

	public void setConfiguration(@Nullable final BackgroundProcessConfiguration newConfiguration) {
		final BackgroundProcessConfiguration oldConfiguration = this.configuration;
		this.configuration = newConfiguration;
		if (oldConfiguration != null && oldConfiguration.getUsages().contains(this)) {
			oldConfiguration.removeUsage(this);
		}
		if (newConfiguration != null && !newConfiguration.getUsages().contains(this)) {
			newConfiguration.addUsage(this);
		}
	}

	/**
	 * Returns the configuration string of this process.
	 * Can be overwritten by other process entities.
	 * The basic background process does not have a configuration and null is always returned.
	 *
	 * @return The configuration string. Is always null.
	 */
	@Nullable
	public String getConfigurationString() {
		if (configuration != null) {
			return configuration.getConfiguration();
		}
		return null;
	}

	/**
	 * The project this process belongs to.
	 *
	 * @return The corresponding project.
	 */
	public ProjectEntity getProject() {
		return owner.getProject();
	}

	/**
	 * Resets the results of the process.
	 */
	public void reset() {
		resultFiles.clear();
		externalProcessStatus = ProcessStatus.NOT_STARTED;
		serverInstance = null;
		scheduledTime = null;
	}

	/**
	 * Validates the status matches the rest of the process' state.
	 */
	@PrePersist @PreUpdate
	private void validateStatus() {
		if (this.externalProcessStatus == ProcessStatus.RUNNING && this.serverInstance == null) {
			throw new IllegalStateException("The stage is running but the current server instance is not set.");
		}
		if (this.externalProcessStatus != ProcessStatus.RUNNING && this.serverInstance != null) {
			throw new IllegalStateException("The server instance is set but the stage is not running.");
		}

		if (this.externalProcessStatus == ProcessStatus.SCHEDULED && this.scheduledTime == null) {
			throw new IllegalStateException("The stage is scheduled but no scheduled time is set.");
		}
		if (this.externalProcessStatus != ProcessStatus.SCHEDULED && this.scheduledTime != null) {
			throw new IllegalStateException("The stage is not scheduled but a scheduled time is set.");
		}
	}
}
