package de.kiaim.cinnamon.platform.model.entity.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * Table for storing the email settings of the application.
 */
@Entity
@Getter @Setter
public class EmailSettingsEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private @Nullable Long id;

	/**
	 * Host of the application mailer.
	 */
	@Column(nullable = false)
	private String mailHost;

	/**
	 * Port of the application mailer.
	 */
	@Column(nullable = false)
	private int mailPort;

	/**
	 * Enable TLS.
	 */
	@Column(nullable = false)
	private boolean mailTLS;

	/**
	 * SMTP authentication.
	 */
	@Column(nullable = false)
	private boolean mailSMTPAuth;

	/**
	 * Username of the application mailer.
	 * Only required if SMTP authentication is enabled.
	 */
	@Column
	private @Nullable String mailUsername;

	/**
	 * Password of the application mailer.
	 */
	@Column
	private @Nullable String mailPassword;

	/**
	 * Sender of the emails sent from the application mailer.
	 */
	@Column(nullable = false)
	private String mailSender;

}
