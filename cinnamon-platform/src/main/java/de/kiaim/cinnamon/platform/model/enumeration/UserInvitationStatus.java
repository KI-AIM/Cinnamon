package de.kiaim.cinnamon.platform.model.enumeration;

/**
 * Status of a user's invitation.
 *
 * @author Daniel Preciado-Marquez
 */
public enum UserInvitationStatus {
	/**
	 * The user has not been invited to the platform yet.
	 */
	NOT_SENT,
	/**
	 * The user has been invited to the platform but has not yet accepted the invitation.
	 * This status includes valid and expired invitations.
	 * The user can still accept the invitation if it is valid.
	 */
	PENDING,
	/**
	 * The user accepted the invitation. The invitation is no longer valid.
	 */
	ACCEPTED,
	/**
	 * The inviter revoked the invitation. The user can no longer accept the invitation.
	 */
	REVOKED,
}
