package de.kiaim.cinnamon.platform.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.ByteArrayInputStream;

/**
 * Wrapper class for a Lob string to prevent issues with auto-commit mode.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter
@NoArgsConstructor
public class LobWrapperEntity {

	/**
	 * ID and primary key.
	 */
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Id
	private Long id;

	/**
	 * The LOB.
	 */
	@Lob
	@Setter
	private byte[] lob;

	/**
	 * Creates a new LOB.
	 * @param lob The LOB.
	 */
	public LobWrapperEntity(final byte[] lob) {
		this.lob = lob;
	}

	/**
	 * Creates a new LOB.
	 * @param lob The LOB.
	 */
	public LobWrapperEntity(final String lob) {
		this.lob = lob.getBytes();
	}

	/**
	 * Returns the lob as a String.
	 * @return The LOB. As a String.
	 */
	public String getLobString() {
		return new String(lob);
	}

	/**
	 * Returns the lob as a stream.
	 *
	 * @return The LOB. As a stream.
	 */
	public ByteArrayInputStream getLobStream() {
		return new ByteArrayInputStream(lob);
	}
}
