package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import de.kiaim.cinnamon.platform.model.dto.UserInvitationInfo;
import de.kiaim.cinnamon.platform.model.dto.UserInvitationRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.entity.UserInvitationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserInvitationStatus;
import de.kiaim.cinnamon.platform.model.mapper.UserInvitationMapper;
import de.kiaim.cinnamon.platform.repository.UserInvitationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

/**
 * Service for managing user invitations.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class UserInvitationService {

	private final Duration expirationDuration;
	private final Duration retentionDuration;
	private final byte[] secret;

	private final UserInvitationRepository repository;

	private final UserInvitationMapper mapper;

	private final MailService mailService;
	private final ResourceSelectorService resourceSelectorService;
	private final UserService userService;

	public UserInvitationService(
			@Value("${cinnamon.users.invitation.expiration}") final Duration expirationDuration,
			@Value("${cinnamon.users.invitation.retention}") final Duration retentionDuration,
			@Value("${cinnamon.users.invitation.secret}") final String secret,
			final UserInvitationRepository repository,
			final UserInvitationMapper mapper,
			final MailService mailService,
			final ResourceSelectorService resourceSelectorService,
			final UserService userService
	) {
		this.expirationDuration = expirationDuration;
		this.retentionDuration = retentionDuration;
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.repository = repository;
		this.mapper = mapper;
		this.mailService = mailService;
		this.resourceSelectorService = resourceSelectorService;
		this.userService = userService;
	}

	/**
	 * Returns all user invitations in the system.
	 *
	 * @return a set of user invitation information.
	 */
	@Transactional(readOnly = true)
	public Set<UserInvitationInfo> getAllInvitations() {
		Set<UserInvitationInfo> invitations = new HashSet<>();
		repository.findAll().forEach(
			invitation -> invitations.add(mapper.toInfo(invitation))
		);
		return invitations;
	}

	/**
	 * Returns the invitation for the given external ID.
	 *
	 * @param externalId the external ID of the user invitation.
	 * @return the user invitation information.
	 * @throws BadArgumentException       if the external ID is invalid.
	 * @throws BadUserInvitationException if the user invitation is not found.
	 */
	@Transactional(readOnly = true)
	public UserInvitationInfo getInvitationById(final String externalId)
			throws BadArgumentException, BadUserInvitationException {
		final UserInvitationEntity invitation = getByExternalId(externalId);
		return mapper.toInfo(invitation);
	}

	/**
	 * Creates a new user invitation.
	 *
	 * @param request   The user invitation request.
	 * @param invitedBy The username of the user who is creating the invitation.
	 * @return The created user invitation information.
	 * @throws BadUserException if no user with the given username exists.
	 */
	@Transactional
	public UserInvitationInfo createInvitation(final UserInvitationRequest request, final String invitedBy)
			throws BadUserException {
		final UserInvitationEntity entity = new UserInvitationEntity();

		var invitedByUser = userService.getUserByUsernameOrThrow(invitedBy);
		entity.setStatus(UserInvitationStatus.NOT_SENT);
		entity.setExternalId(UUID.randomUUID());
		entity.setInvitedBy(invitedByUser);
		entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));

		mapper.updateEntity(entity, request);

		final var savedEntity = repository.save(entity);
		return mapper.toInfo(savedEntity);
	}

	/**
	 * Updates an existing user invitation.
	 * Cannot be done if the invitation has already been accepted.
	 * Resets the invitation status to NOT_SENT and clears the token, last sent time, expiration time, and revoked time.
	 *
	 * @param externalId The external ID of the user invitation.
	 * @param request    The user invitation request.
	 * @return The updated user invitation information.
	 * @throws BadArgumentException       if the external ID is invalid.
	 * @throws BadUserInvitationException if the user invitation is not found or already accepted.
	 */
	@Transactional
	public UserInvitationInfo updateInvitation(final String externalId, final UserInvitationRequest request)
			throws BadArgumentException, BadUserInvitationException {
		final UserInvitationEntity entity = getByExternalId(externalId);

		if (entity.getStatus() == UserInvitationStatus.ACCEPTED) {
			throw new BadUserInvitationException(BadUserInvitationException.ALREADY_ACCEPTED,
			                                     "Cannot update an already accepted invitation");
		}

		// Clear already set token
		if (!Objects.equals(entity.getEmail(), request.getEmail())) {
			entity.setStatus(UserInvitationStatus.NOT_SENT);
			entity.setTokenHash(null);
			entity.setLastSentAt(null);
			entity.setExpiresAt(null);
			entity.setRevokedAt(null);
		}

		mapper.updateEntity(entity, request);

		final var savedEntity = repository.save(entity);
		return mapper.toInfo(savedEntity);
	}

	/**
	 * Sends an invitation email for the invitation with the given external ID.
	 * Cannot be done if the invitation has already been accepted.
	 * Requires the mail settings to be configured.
	 *
	 * @param externalId The external ID of the user invitation.
	 * @param request    The HTTP servlet request triggering the method call.
	 * @return The updated user invitation information.
	 * @throws BadArgumentException            If the external ID is invalid.
	 * @throws BadUserInvitationException      If the user invitation is not found or already accepted.
	 * @throws BadMailSettingsException        If the mail settings are invalid.
	 * @throws InternalMailException           If an internal mail error occurs.
	 * @throws InternalUserInvitationException If the invitation token cannot be generated or hashed.
	 */
	@Transactional
	public UserInvitationInfo sendInvitation(final String externalId, final HttpServletRequest request)
			throws BadArgumentException, BadUserInvitationException, BadMailSettingsException, InternalMailException,
					       InternalUserInvitationException {
		final var entity = getByExternalId(externalId);

		if (entity.getStatus() == UserInvitationStatus.ACCEPTED) {
			throw new BadUserInvitationException(BadUserInvitationException.ALREADY_ACCEPTED,
			                                     "Cannot send an already accepted invitation");
		}

		// First try to send the email
		final String token = generateToken();
		sendInvitationEmail(entity, token, request);

		// If successful, update the entity with the new token and timestamps
		final var now = System.currentTimeMillis();
		entity.setTokenHash(hash(token));
		entity.setLastSentAt(new Timestamp(now));
		entity.setExpiresAt(new Timestamp(now + expirationDuration.toMillis()));
		entity.setStatus(UserInvitationStatus.PENDING);

		final var savedEntity = repository.save(entity);
		return mapper.toInfo(savedEntity);
	}

	/**
	 * Revokes the invitation with the given external ID.
	 * Cannot be done if the invitation has already been accepted.
	 *
	 * @param externalId The external ID of the user invitation.
	 * @return The updated user invitation information.
	 * @throws BadArgumentException       If the external ID is invalid.
	 * @throws BadUserInvitationException If the user invitation is not found or already accepted.
	 */
	@Transactional
	public UserInvitationInfo revokeInvitation(final String externalId)
			throws BadArgumentException, BadUserInvitationException {
		final var entity = getByExternalId(externalId);

		if (entity.getStatus() == UserInvitationStatus.ACCEPTED) {
			throw new BadUserInvitationException(BadUserInvitationException.ALREADY_ACCEPTED,
			                                     "Cannot revoke an already accepted invitation");
		}

		entity.setStatus(UserInvitationStatus.REVOKED);
		entity.setRevokedAt(new Timestamp(System.currentTimeMillis()));

		final var savedEntity = repository.save(entity);
		return mapper.toInfo(savedEntity);
	}

	/**
	 * Deletes all invitations that have been accepted or revoked longer ago than the configured retention duration.
	 */
	@Transactional
	public void deleteExpiredInvitations() {
		final Timestamp cutoff = new Timestamp(System.currentTimeMillis() - retentionDuration.toMillis());
		final var expiredInvitations = repository.findAllAcceptedOrRevokedBefore(cutoff);
		repository.deleteAll(expiredInvitations);
	}

	/**
	 * Accepts the invitation with the given token and registers a new user with the provided registration request.
	 *
	 * @param token   The unhashed token of the user invitation.
	 * @param request The registration request containing user details.
	 * @throws BadUserInvitationException      If the user invitation is invalid.
	 * @throws BadUserException                If the user registration fails.
	 * @throws InternalUserInvitationException If an internal error occurs while processing the invitation.
	 */
	@Transactional
	public void acceptInvitation(final String token, final RegisterRequest request)
			throws BadUserInvitationException, BadUserException, InternalUserInvitationException {
		final var entity = validateToken(token);

		final UserEntity user = userService.register(request.getUsername(), request.getPassword(),
		                                             entity.getUserRoles(), request.getEmail());

		entity.setStatus(UserInvitationStatus.ACCEPTED);
		entity.setAcceptedAt(new Timestamp(System.currentTimeMillis()));
		entity.setAcceptedBy(user);

		repository.save(entity);
	}

	/**
	 * Sends an invitation email to the user specified in the invitation entity.
	 *
	 * @param invitation The user invitation entity.
	 * @param token      The unhashed token of the user invitation.
	 * @param request    The HTTP servlet request triggering the method call.
	 * @throws BadMailSettingsException If the mail settings are invalid.
	 * @throws InternalMailException    If an internal error occurs while sending the email.
	 */
	private void sendInvitationEmail(final UserInvitationEntity invitation, final String token,
	                                 final HttpServletRequest request)
			throws BadMailSettingsException, InternalMailException {
		String subject = invitation.getEmailTemplateItem() == null
		                 ? invitation.getEmailCustomSubject()
		                 : invitation.getEmailTemplateItem().getSubject();
		String body = invitation.getEmailTemplateItem() == null
		              ? invitation.getEmailCustomBody()
		              : invitation.getEmailTemplateItem().getBody();

		if (body == null) {
			throw new InternalMailException(InternalMailException.MISSING_BODY, "Email body is null for invitation email");
		}

		final String invitationLink = assembleInvitationLink(request, token);

		try {
			body = resourceSelectorService.replaceSelectorsInString(body, null, invitation, invitationLink);
		} catch (ApiException e) {
			throw new InternalMailException(InternalMailException.BODY_PLACEHOLDER_REPLACEMENT,
			                                "Failed to replace placeholder in email body", e);
		}

		mailService.sendMail(invitation.getEmail(), subject, body);
	}

	/**
	 * Generates a secure random token for user invitations.
	 *
	 * @return A base64 URL-encoded string representing the generated token.
	 */
	private String generateToken() {
		BytesKeyGenerator generator = KeyGenerators.secureRandom(32);
		final byte[] bytes = generator.generateKey();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Hashes the given token using HMAC-SHA256 with the configured secret.
	 *
	 * @param token The token to hash.
	 * @return A base64 URL-encoded string representing the hashed token.
	 * @throws InternalUserInvitationException If the hashing algorithm is not available or the key is invalid.
	 */
	private String hash(final String token) throws InternalUserInvitationException {
		try {
			final Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));

			final byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (final NoSuchAlgorithmException | InvalidKeyException e) {
			throw new InternalUserInvitationException(InternalUserInvitationException.TOKEN_GENERATION_FAILED,
			                                          "Could not hash invitation token", e);
		}
	}

	/**
	 * Validates the given token by checking its existence, status, and expiration.
	 * Returns the corresponding UserInvitationEntity if valid, otherwise throws an exception.
	 *
	 * @param token The unhashed token to validate.
	 * @return The corresponding UserInvitationEntity if the token is valid.
	 * @throws BadUserInvitationException      If the token is invalid, revoked, already accepted, or expired.
	 * @throws InternalUserInvitationException If there is an internal error while validating the token.
	 */
	private UserInvitationEntity validateToken(final String token)
			throws BadUserInvitationException, InternalUserInvitationException {
		final String tokenHash = hash(token);
		final var optional = repository.findByTokenHash(tokenHash);
		if (optional.isEmpty()) {
			throw new BadUserInvitationException(BadUserInvitationException.TOKEN_NOT_FOUND,
			                                     "User invitation not found");
		}

		final var entity = optional.get();

		if (entity.getStatus() == UserInvitationStatus.REVOKED) {
			throw new BadUserInvitationException(BadUserInvitationException.REVOKED,
			                                     "User invitation has been revoked");
		}
		if (entity.getStatus() == UserInvitationStatus.ACCEPTED) {
			throw new BadUserInvitationException(BadUserInvitationException.ALREADY_ACCEPTED,
			                                     "Cannot accept an already accepted invitation");
		}

		if (entity.isExpired()) {
			throw new BadUserInvitationException(BadUserInvitationException.EXPIRED,
			                                     "User invitation has expired");
		}
		return entity;
	}

	/**
	 * Retrieves a UserInvitationEntity by its external ID.
	 *
	 * @param externalId The external ID of the user invitation.
	 * @return The corresponding UserInvitationEntity if found.
	 * @throws BadArgumentException       If the external ID is invalid.
	 * @throws BadUserInvitationException If the user invitation is not found or has an invalid status.
	 */
	@Transactional(readOnly = true)
	protected UserInvitationEntity getByExternalId(final String externalId)
			throws BadArgumentException, BadUserInvitationException {
		UUID id;

		try {
			id = UUID.fromString(externalId);
		} catch (final IllegalArgumentException e) {
			throw new BadArgumentException(BadArgumentException.INVALID_INVITATION_ID, "Invalid invitation ID format");
		}

		final var optional = repository.findByExternalId(id);
		if (optional.isEmpty()) {
			throw new BadUserInvitationException(BadUserInvitationException.ID_NOT_FOUND,
			                                     "User invitation not found");
		}
		return optional.get();
	}

	/**
	 * Assembles the invitation link for the user.
	 * Uses the current request URL to determine the base URL and appends the registration path with the token as a query parameter.
	 *
	 * @param request The HTTP servlet request triggering the invitation.
	 * @param token   The unhashed invitation token.
	 * @return The complete invitation link.
	 */
	private String assembleInvitationLink(final HttpServletRequest request, final String token) {
		return request.getRequestURL().subSequence(0, request.getRequestURL().lastIndexOf("/api")) +
		       "/register?token=" + token;
	}

}
