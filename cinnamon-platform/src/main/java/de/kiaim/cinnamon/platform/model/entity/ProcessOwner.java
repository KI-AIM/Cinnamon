package de.kiaim.cinnamon.platform.model.entity;

import jakarta.persistence.*;

/**
 * Entity for process owner.
 *
 * @author Daniel Preciado-Marquez
 */
@Inheritance(strategy = InheritanceType.JOINED)
@Entity
public abstract class ProcessOwner {

	/**
	 * ID and primary key.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	protected Long id;

	/**
	 * Returns the corresponding project.
	 * @return The corresponding project.
	 */
	@Transient
	abstract ProjectEntity getProject();
}
