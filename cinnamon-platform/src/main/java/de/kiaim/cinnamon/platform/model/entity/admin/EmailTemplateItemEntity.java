package de.kiaim.cinnamon.platform.model.entity.admin;

import de.kiaim.cinnamon.platform.model.enumeration.SupportedLanguage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity representing an email template for a specific language in the database.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "email_template_id", "language" }))
@Getter @Setter
public class EmailTemplateItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SupportedLanguage language;

	@Column(nullable = false)
	private String subject;

	/**
	 * The body of the email.
	 * Stored as a long text since a mail body easily exceeds the default column length.
	 */
	@Column(nullable = false, length = Integer.MAX_VALUE)
	private String body;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "email_template_id", nullable = false)
	private EmailTemplateEntity emailTemplate;
}
