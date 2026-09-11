package de.kiaim.cinnamon.platform.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.jspecify.annotations.Nullable;

import java.util.List;

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
	private @Nullable Long id;

	/**
	 * Name of the file.
	 */
	@Getter @Setter
	@Nullable
	private String name = null;

	@Embedded
	@Getter @Setter
	@Nullable
	private FileCompatibilityEntity compatibility = null;

	/**
	 * Names of the attributes in the file.
	 * Null if the file or the file configuration has not been set.
	 */
    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    @Getter @Setter
	@Nullable
	private List<String> attributeNames = null;

	/**
	 * Configuration for retrieving the data from the data source.
	 */
	@OneToOne(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
	@JoinColumn(name = "data_source_configuration_id", referencedColumnName = "id")
	@Getter @Setter
	@Nullable
	private DataSourceConfigurationEntity dataSourceConfiguration = null;

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

	public int getNumberOfAttributes() {
		if (attributeNames == null) {
			return 0;
		}
		return attributeNames.size();
	}
}
