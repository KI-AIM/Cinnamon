package de.kiaim.cinnamon.platform.model.entity.admin;

import de.kiaim.cinnamon.platform.model.entity.UserInvitationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.SupportedLanguage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing an email template for a specific language in the database.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "email_template_id", "language" }))
@Getter @Setter
public class EmailTemplateItemEntity {

	/**
	 * Database ID of the email template item.
	 */
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * Language of the email template item.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SupportedLanguage language;

	/**
	 * Subject of the email template item.
	 */
	@Column(nullable = false)
	private String subject;

	/**
	 * The body of the email.
	 * Stored as a long text since a mail body easily exceeds the default column length.
	 */
	@Column(nullable = false, length = Integer.MAX_VALUE)
	private String body;

	/**
	 * Usages of the email template.
	 * Mapped by {@link UserInvitationEntity#getEmailTemplateItem()}.
	 */
	@OneToMany(mappedBy = "emailTemplateItem", fetch = FetchType.LAZY)
	private final Set<UserInvitationEntity> usages = new HashSet<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "email_template_id", nullable = false)
	private EmailTemplateEntity emailTemplate;
}
