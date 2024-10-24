package de.kiaim.platform.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
	@Column(nullable = false)
	@Getter @Setter
	private String name;

	/**
	 * Configuration for reading the file.
	 */
	@OneToOne(fetch = FetchType.EAGER, optional = false, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "file_configuration_id", referencedColumnName = "id")
	@Getter @Setter
	private FileConfigurationEntity fileConfiguration;

	/**
	 * Data of the file.
	 */
	@Lob
	@Getter @Setter
	private byte[] file;
}
