package de.kiaim.cinnamon.platform.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Entity for saving the content and the metadata of a file.
 */
@Entity
public class FileEntity {

	/**
	 * Primary key.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Name of the file.
	 */
	@Getter @Setter
	@Nullable
	private String name = null;

	/**
	 * Number of attributes in the file.
	 */
	@Column(nullable = false)
	@Getter @Setter
	private int numberOfAttributes = 0;

	/**
	 * Configuration for reading the file.
	 */
	@OneToOne(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "file_configuration_id", referencedColumnName = "id")
	@Getter @Setter
	@Nullable
	private FileConfigurationEntity fileConfiguration = null;

	@OneToOne(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "file_id")
	@Getter @Setter
	@Nullable
	private LobWrapperEntity file = null;
}
