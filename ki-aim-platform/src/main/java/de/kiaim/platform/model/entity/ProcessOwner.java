package de.kiaim.platform.model.entity;

import jakarta.persistence.*;

/**
 * Entity for process owner.
 *
 * @author Daniel Preciado-Marquez
 */
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Entity
public abstract class ProcessOwner {

	/**
	 * ID and primary key.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private Long id;

	/**
	 * The project.
	 * @return The project.
	 */
	abstract ProjectEntity getProject();


}
