package de.kiaim.cinnamon.platform.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.kiaim.cinnamon.platform.model.enumeration.UserInvitationStatus;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

/**
 * DTO for viewing user invitation information.
 *
 * @author Daniel Preciado-Marquez
 */
@Data
@Schema(description = "DTO for viewing user invitation information.")
public class UserInvitationInfo {

	/**
	 * ID of the invitation.
	 */
	@JsonProperty("id")
	@Schema(description = "ID of the invitation.", example = "123e4567-e89b-12d3-a456-426614174000")
	private String externalId;

	/**
	 * Status of the invitation.
	 */
	@Schema(description = "Status of the invitation.")
	private UserInvitationStatus status;

	/**
	 * Email address of the invited user.
	 */
	@Schema(description = "Email address of the invited user.", example = "user@example.com")
	private String email;

	/**
	 * The roles of this invitation.
	 * Will be assigned to the user when the invitation is accepted.
	 */
	@Schema(description = "The roles of this invitation. Will be assigned to the user when the invitation is accepted.")
	private final Set<UserRole> userRoles = new HashSet<>();

	/**
	 * The ID of the email template item used for sending the invitation email.
	 * Null if the invitation was sent with a custom subject and body, or the template was deleted.
	 * If null, the custom subject and body will be used for sending the invitation email.
	 */
	@Schema(description = "The ID of the email template item used for sending the invitation email. " +
	                      "Null if the invitation was sent with a custom subject and body, or the template was deleted. " +
	                      "If null, the custom subject and body will be used for sending the invitation email.")
	@Nullable
	private Long emailTemplateItemId;

	/**
	 * The custom subject of the invitation email.
	 * Null if the invitation was sent with an email template item.
	 */
	@Schema(description = "The custom subject of the invitation email. Null if the invitation was sent with an email template item.",
	        example = "Welcome to our platform!")
	@Nullable
	private String emailCustomSubject;

	/**
	 * The custom body of the invitation email.
	 * Null if the invitation was sent with an email template item.
	 */
	@Schema(description = "The custom body of the invitation email. Null if the invitation was sent with an email template item.",
	        example = "Hello, welcome to our platform!")
	@Nullable
	private String emailCustomBody;

	/**
	 * Timestamp when the invitation was created.
	 */
	@Schema(description = "Timestamp when the invitation was created.", example = "2024-06-01T12:00:00Z")
	private Timestamp createdAt;

	/**
	 * Timestamp when the invitation was sent.
	 * Null if the invitation has not been sent or revoked.
	 */
	@Schema(description = "Timestamp when the invitation was sent. Null if the invitation has not been sent or revoked.",
	        example = "2024-06-01T12:00:00Z")
	@Nullable
	private Timestamp lastSentAt;

	/**
	 * Timestamp when the invitation expires.
	 * Null if the invitation has not been sent.
	 */
	@Schema(description = "Timestamp when the invitation expires.", example = "2024-06-01T12:00:00Z")
	@Nullable
	private Timestamp expiresAt;

	/**
	 * Timestamp when the invitation was accepted.
	 * Null if the invitation has not been accepted.
	 */
	@Schema(description = "Timestamp when the invitation was accepted. Null if the invitation has not been accepted.",
	        example = "2024-06-01T12:00:00Z")
	@Nullable
	private Timestamp acceptedAt;

	/**
	 * Timestamp when the invitation was revoked.
	 * Null if the invitation has not been revoked.
	 */
	@Schema(description = "Timestamp when the invitation was revoked. Null if the invitation has not been revoked.",
	        example = "2024-06-01T12:00:00Z")
	@Nullable
	private Timestamp revokedAt;

	/**
	 * Username of the user that sent the invitation.
	 * Null if the user was deleted after the invitation was sent.
	 */
	@Schema(description = "Username of the user that sent the invitation. Null if the user was deleted after the invitation was sent.",
	        example = "admin_user")
	@Nullable
	private String invitedBy;

	/**
	 * Username of the user that accepted the invitation.
	 * Null if the invitation has not been accepted.
	 */
	@Schema(description = "Username of the user that accepted the invitation. Null if the invitation has not been accepted.",
	        example = "accepted_user")
	@Nullable
	private String acceptedBy;

}
