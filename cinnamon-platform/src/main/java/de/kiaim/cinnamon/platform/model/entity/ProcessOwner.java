package de.kiaim.cinnamon.platform.model.entity;

import jakarta.persistence.*;

/**
 * Entity for process owner.
 *
 * @author Daniel Preciado-Marquez
 */
@Inheritance(strategy = InheritanceType.JOINED)
@Entity
// TODO Without this, negative IDs are generated, this shouldn't be needed
@SequenceGenerator(name = "process_owner_seq", sequenceName = "process_owner_seq", allocationSize = 1)
public abstract class ProcessOwner {

	/**
	 * ID and primary key.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "process_owner_seq")
	protected Long id;

	/**
	 * Returns the corresponding project.
	 * @return The corresponding project.
	 */
	@Transient
	abstract ProjectEntity getProject();
}
