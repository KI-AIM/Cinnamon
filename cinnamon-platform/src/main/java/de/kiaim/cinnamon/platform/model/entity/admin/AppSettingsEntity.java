package de.kiaim.cinnamon.platform.model.entity.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * Database entity for general settings of the platform.
 * Should only exist once in the database.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
public class AppSettingsEntity {

	/**
	 * The ID.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * The imprint.
	 * Formatted as HTML.
	 */
	@Column(columnDefinition = "TEXT")
	@Getter @Setter
	@Nullable
	private String imprint;

}
