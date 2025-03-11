package de.kiaim.cinnamon.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.cinnamon.platform.converter.ExternalConfigurationAttributeConverter;
import de.kiaim.cinnamon.platform.model.configuration.ExternalConfiguration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * List of configurations for one defined external configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@NoArgsConstructor
public class ConfigurationListEntity {

	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Associated configuration Name.
	 */
	@Column(nullable = false)
	@Convert(converter = ExternalConfigurationAttributeConverter.class)
	@Getter
	private ExternalConfiguration configuration;

	@OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	@OrderBy("configurationIndex")
	@Getter
	private final List<BackgroundProcessConfiguration> configurations = new ArrayList<>();

	/**
	 * The corresponding project.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER)
	@Getter @Setter
	private ProjectEntity project;

	public ConfigurationListEntity(final ExternalConfiguration configuration) {
		this.configuration= configuration;
	}
}
