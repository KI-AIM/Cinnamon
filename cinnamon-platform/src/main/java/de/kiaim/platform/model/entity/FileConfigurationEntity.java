package de.kiaim.platform.model.entity;

import de.kiaim.platform.model.file.FileType;
import jakarta.persistence.*;
import lombok.Getter;

/**
 * Entity class for the file configuration.
 * Contains metadata that is required in order to read a file.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "file_type", discriminatorType = DiscriminatorType.STRING)
public abstract class FileConfigurationEntity {

	/**
	 * Primary key.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Type of the file.
	 */
	@Column(name = "file_type", nullable = false, insertable = false, updatable = false)
	@Enumerated(EnumType.STRING)
	@Getter
	protected FileType fileType;
}
