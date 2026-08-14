package de.kiaim.cinnamon.platform.model.entity;

import de.kiaim.cinnamon.platform.model.entity.admin.EmailTemplateItemEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserInvitationStatus;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a user invitation.
 *
 * @author Daniel Preciado-Marquez
 */
@Entity
@Getter @Setter
public class UserInvitationEntity {

	/**
	 * ID of the invitation.
	 */
	@Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Setter(AccessLevel.NONE)
	private Long id;

	/**
	 * External ID of the invitation.
	 */
	@Column(nullable = false, unique = true)
	private UUID externalId;

	/**
	 * Status of the invitation.
	 */
	@Column(nullable = false)
	private UserInvitationStatus status;

	/**
	 * Email address of the invited user.
	 */
	@Column(nullable = false)
	private String email;

	/**
	 * The roles of this invitation.
	 * Will be assigned to the user when the invitation is accepted.
	 */
	@Setter(AccessLevel.NONE)
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_invitation_entity_role",
	                 joinColumns = @JoinColumn(name = "user_invitation_id", nullable = false),
	                 uniqueConstraints = @UniqueConstraint(columnNames = {"user_invitation_id", "user_role"}))
	@Column(name = "user_role", nullable = false)
	@Enumerated(EnumType.STRING)
	private final Set<UserRole> userRoles = new HashSet<>();

	/**
	 * The email template item used for sending the invitation email.
	 * Null if the invitation was sent with a custom subject and body, or the template was deleted.
	 * If null, the custom subject and body will be used for sending the invitation email.
	 * Mapped by {@link EmailTemplateItemEntity#getUsages()}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@Nullable
	private EmailTemplateItemEntity emailTemplateItem;

	/**
	 * The custom subject of the invitation email.
	 * Null if the invitation was sent with an email template item.
	 */
	@Nullable
	private String emailCustomSubject;

	/**
	 * The custom body of the invitation email.
	 * Null if the invitation was sent with an email template item.
	 */
	@Nullable
	private String emailCustomBody;

	/**
	 * Token used to identify the invitation.
	 * This token is hashed for security reasons.
	 * Null if the invitation has not been sent or revoked.
	 */
	@Nullable
	private String tokenHash;

	/**
	 * Timestamp when the invitation was created.
	 */
	@Column(nullable = false)
	private Timestamp createdAt;

	/**
	 * Timestamp when the invitation was sent.
	 * Null if the invitation has not been sent or revoked.
	 */
	@Nullable
	private Timestamp lastSentAt;

	/**
	 * Timestamp when the invitation expires.
	 * Null if the invitation has not been sent.
	 */
	@Nullable
	private Timestamp expiresAt;

	/**
	 * Timestamp when the invitation was accepted.
	 * Null if the invitation has not been accepted.
	 */
	@Nullable
	private Timestamp acceptedAt;

	/**
	 * Timestamp when the invitation was revoked.
	 * Null if the invitation has not been revoked.
	 */
	@Nullable
	private Timestamp revokedAt;

	/**
	 * User that sent the invitation.
	 * Null if the user was deleted after the invitation was sent.
	 * Mapped by {@link UserEntity#getInvitations()}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@Nullable
	private UserEntity invitedBy;

	/**
	 * User that accepted the invitation.
	 * Null if the invitation has not been accepted.
	 */
	@OneToOne(fetch = FetchType.LAZY)
	@Nullable
	private UserEntity acceptedBy;

	public void setEmailTemplateItem(@Nullable final EmailTemplateItemEntity emailTemplateItem) {
		EmailTemplateItemEntity oldEmailTemplateItem = this.emailTemplateItem;
		this.emailTemplateItem = emailTemplateItem;
		if (oldEmailTemplateItem != null) {
			oldEmailTemplateItem.getUsages().remove(this);
		}
		if (emailTemplateItem != null) {
			emailTemplateItem.getUsages().add(this);
		}
	}

	public void setAcceptedBy(final UserEntity acceptedBy) {
		this.acceptedBy = acceptedBy;
		if (acceptedBy != null && acceptedBy.getInvitation() != this) {
			acceptedBy.setInvitation(this);
		}
	}

}
