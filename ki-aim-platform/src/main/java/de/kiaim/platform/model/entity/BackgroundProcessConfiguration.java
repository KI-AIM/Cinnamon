package de.kiaim.platform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Contains information that is required for starting a specific algorithm.
 * The algorithm is defined either by the URL or the endpoint configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
public class BackgroundProcessConfiguration {

	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * Index used for identifying the configuration instance between other configurations of the same configuration endpoint definition.
	 */
	@Column(nullable = false)
	@Getter @Setter
	private Integer configurationIndex;

	/**
	 * URL to start the process.
	 * For endpoints where the URL depends on the configuration.
	 * Only used if the endpoint does not define a process URL.
	 */
	@Getter @Setter
	@Nullable
	private String processUrl;

	/**
	 * The configuration for the process.
	 */
	@Column(length = Integer.MAX_VALUE)
	@Getter @Setter
	@Nullable
	private String configuration;

	/**
	 * The corresponding project.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@Getter @Setter
	private ConfigurationListEntity configurationList;

	/**
	 * Processes where this configuration is used.
	 */
	@OneToMany(mappedBy = "configuration", orphanRemoval = false)
	@Getter
	private final Set<BackgroundProcessEntity> usages = new HashSet<>();

	public void addUsage(final BackgroundProcessEntity usage) {
		usages.add(usage);
		if (usage.getConfiguration() != this) {
			usage.setConfiguration(this);
		}
	}

	public void removeUsage(final BackgroundProcessEntity usage) {
		usages.remove(usage);
		if (usage.getConfiguration() == this) {
			usage.setConfiguration(null);
		}
	}
}
