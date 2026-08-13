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
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Service for managing user invitations.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class UserInvitationService {

	private final Duration expirationDuration;
	private final byte[] secret;

	private final UserInvitationRepository repository;

	private final UserInvitationMapper mapper;

	private final MailService mailService;
	private final ResourceSelectorService resourceSelectorService;
	private final UserService userService;

	public UserInvitationService(
			@Value("${cinnamon.users.invitation.expiration}") final Duration expirationDuration,
			@Value("${cinnamon.users.invitation.secret}") final String secret,
			final UserInvitationRepository repository,
			final UserInvitationMapper mapper,
			final MailService mailService,
			final ResourceSelectorService resourceSelectorService,
			final UserService userService
	) {
		this.expirationDuration = expirationDuration;
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.repository = repository;
		this.mapper = mapper;
		this.mailService = mailService;
		this.resourceSelectorService = resourceSelectorService;
		this.userService = userService;
	}

	@Transactional(readOnly = true)
	public Set<UserInvitationInfo> getAllInvitations() {
		Set<UserInvitationInfo> invitations = new HashSet<>();
		repository.findAll().forEach(
			invitation -> invitations.add(mapper.toInfo(invitation))
		);
		return invitations;
	}

	@Transactional(readOnly = true)
	public UserInvitationInfo getInvitationById(final Long invitationId) throws BadUserInvitationException {
		final var optional = repository.findById(invitationId);
		if (optional.isEmpty()) {
			throw new BadUserInvitationException(BadUserInvitationException.ID_NOT_FOUND,
			                                     "User invitation not found");
		}
		return mapper.toInfo(optional.get());
	}

	@Transactional
	public UserInvitationInfo createInvitation(final UserInvitationRequest request, final String invitedBy)
			throws BadUserException {
		final UserInvitationEntity entity = new UserInvitationEntity();

		var invitedByUser = userService.getUserByUsernameOrThrow(invitedBy);
		entity.setInvitedBy(invitedByUser);
		entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));

		mapper.updateEntity(entity, request);

		final var savedEntity = repository.save(entity);
		return mapper.toInfo(savedEntity);
	}

	@Transactional
	public UserInvitationInfo updateInvitation(final Long id, final UserInvitationRequest request)
			throws BadUserInvitationException {
		final UserInvitationEntity entity;
		final var optional = repository.findById(id);
		if (optional.isEmpty()) {
			throw new BadUserInvitationException(BadUserInvitationException.ID_NOT_FOUND,
			                                     "User invitation not found");
		}

		entity = optional.get();

		if (entity.getStatus() == UserInvitationStatus.ACCEPTED) {
			throw new BadUserInvitationException(BadUserInvitationException.ALREADY_ACCEPTED,
			                                     "Cannot update an already accepted invitation");
		}

		// Clear already set token
		entity.setStatus(UserInvitationStatus.NOT_SENT);
		entity.setTokenHash(null);
		entity.setLastSentAt(null);
		entity.setExpiresAt(null);
		entity.setRevokedAt(null);

		mapper.updateEntity(entity, request);

		final var savedEntity = repository.save(entity);
		return mapper.toInfo(savedEntity);
	}

	@Transactional
	public UserInvitationInfo sendInvitation(final Long invitationId)
			throws BadUserInvitationException, BadMailSettingsException, InternalMailException,
					       InternalUserInvitationException {
		final var optional = repository.findById(invitationId);
		if (optional.isEmpty()) {
			throw new BadUserInvitationException(BadUserInvitationException.ID_NOT_FOUND,
			                                     "User invitation not found");
		}

		final var entity = optional.get();

		if (entity.getStatus() == UserInvitationStatus.ACCEPTED) {
			throw new BadUserInvitationException(BadUserInvitationException.ALREADY_ACCEPTED,
			                                     "Cannot send an already accepted invitation");
		}

		final var now = System.currentTimeMillis();

		final String token = generateToken();
		entity.setTokenHash(hash(token));
		entity.setLastSentAt(new Timestamp(now));
		entity.setExpiresAt(new Timestamp(now + expirationDuration.toMillis()));
		entity.setStatus(UserInvitationStatus.PENDING);

		sentInvitationEmail(entity, token);

		final var savedEntity = repository.save(entity);
		return mapper.toInfo(savedEntity);
	}

	@Transactional
	public UserInvitationInfo revokeInvitation(final Long invitationId) throws BadUserInvitationException {
		final var optional = repository.findById(invitationId);
		if (optional.isEmpty()) {
			throw new BadUserInvitationException(BadUserInvitationException.ID_NOT_FOUND,
			                                     "User invitation not found");
		}

		final var entity = optional.get();

		if (entity.getStatus() == UserInvitationStatus.ACCEPTED) {
			throw new BadUserInvitationException(BadUserInvitationException.ALREADY_ACCEPTED,
			                                     "Cannot revoke an already accepted invitation");
		}

		entity.setStatus(UserInvitationStatus.REVOKED);
		entity.setRevokedAt(new Timestamp(System.currentTimeMillis()));
		entity.setTokenHash(null);

		final var savedEntity = repository.save(entity);
		return mapper.toInfo(savedEntity);
	}

	@Transactional(readOnly = true)
	public RegisterRequest getInvitationByToken(final String token)
			throws BadUserInvitationException, InternalUserInvitationException {
		final var entity = validateToken(token);

		final var request = new RegisterRequest();
		request.setEmail(entity.getEmail());

		return request;
	}

	@Transactional
	public void acceptInvitation(final String token, final RegisterRequest request)
			throws BadUserInvitationException, BadUserException, InternalUserInvitationException {
		final var entity = validateToken(token);

		final UserEntity user = userService.register(request.getUsername(), request.getPassword(),
		                                             entity.getUserRoles(), request.getEmail());

		entity.setStatus(UserInvitationStatus.ACCEPTED);
		entity.setTokenHash(null);
		entity.setAcceptedAt(new Timestamp(System.currentTimeMillis()));
		entity.setAcceptedBy(user);

		repository.save(entity);
	}

	private void sentInvitationEmail(final UserInvitationEntity invitation, final String token)
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

		try {
			body = resourceSelectorService.replaceSelectorsInString(body, null, invitation, token);
		} catch (ApiException e) {
			throw new InternalMailException(InternalMailException.BODY_PLACEHOLDER_REPLACEMENT,
			                                "Failed to replace placeholder in email body", e);
		}

		mailService.sendMail(invitation.getEmail(), subject, body);
	}

	public String generateToken() {
		BytesKeyGenerator generator = KeyGenerators.secureRandom(32);
		final byte[] bytes = generator.generateKey();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hash(final String token) throws InternalUserInvitationException {
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

		if (entity.getExpiresAt() != null && System.currentTimeMillis() > entity.getExpiresAt().getTime()) {
			throw new BadUserInvitationException(BadUserInvitationException.EXPIRED,
			                                     "User invitation has expired");
		}
		return entity;
	}
}
