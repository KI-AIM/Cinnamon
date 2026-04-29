package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.model.configuration.data.DataSourceServerConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.lang.Nullable;

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
	 * Source of the file.
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	@Getter @Setter
	private DataSourceType dataSourceType;

	/**
	 * Configuration for the server where the file is located.
	 * Must be available if the {@link #dataSourceType} is {@link DataSourceType#SERVER}.
	 */
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	@Getter @Setter
	@Nullable
	private DataSourceServerConfiguration server;

	/**
	 * Type of the file.
	 */
	@Column(name = "file_type", nullable = false, insertable = false, updatable = false)
	@Enumerated(EnumType.STRING)
	@Getter
	protected FileType fileType;
}
