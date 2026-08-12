package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.model.validation.EmailContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Request for inviting a new user to the platform.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request for inviting a new user to the platform.")
@Data
@EmailContent
public class UserInvitationRequest {

	/**
	 * Email address of the invited user.
	 */
	@Schema(description = "Email address of the invited user.")
	@NotNull(message = "Email address is required.")
	@Email(message = "Email address is not valid.")
	private String email;

	/**
	 * The roles of this invitation.
	 * Will be assigned to the user when the invitation is accepted.
	 */
	@Schema(description = "The roles of this invitation. Will be assigned to the user when the invitation is accepted.")
	private Set<UserRole> userRoles = new HashSet<>();

	/**
	 * The ID of the email template item to be used for sending the invitation email.
	 * Either this or the custom subject and body must be provided.
	 */
	@Schema(description = "The ID of the email template item to be used for sending the invitation email.")
	private Long emailTemplateItem;

	/**
	 * The custom subject of the invitation email.
	 * Either this and the custom body or the email template item must be provided.
	 */
	@Schema(description = "The custom subject of the invitation email.")
	private String emailCustomSubject;

	/**
	 * The custom body of the invitation email.
	 * Either this and the custom subject or the email template item must be provided.
	 */
	@Schema(description = "The custom body of the invitation email.")
	private String emailCustomBody;
}
