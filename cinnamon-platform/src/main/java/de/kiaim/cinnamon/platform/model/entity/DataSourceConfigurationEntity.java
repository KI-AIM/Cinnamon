package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.model.configuration.data.DataSourceServerConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.lang.Nullable;

/**
 * Entity class for the data source configuration.
 * Defines where the file containing the data is located.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter @Setter
public class DataSourceConfigurationEntity {

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
	 * Must be available if the {@link #dataSourceType} is {@link DataSourceType#FHIR_SERVER}.
	 */
	@Type(JsonType.class)
	@Column(columnDefinition = "json")
	@Getter @Setter
	@Nullable
	private DataSourceServerConfiguration server;

}
