package de.kiaim.cinnamon.test.platform.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import de.kiaim.cinnamon.platform.exception.BadArgumentException;
import de.kiaim.cinnamon.platform.exception.BadMailSettingsException;
import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.exception.BadUserInvitationException;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import de.kiaim.cinnamon.platform.model.dto.UserInvitationRequest;
import de.kiaim.cinnamon.platform.model.entity.UserInvitationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserInvitationStatus;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.repository.UserInvitationRepository;
import de.kiaim.cinnamon.platform.service.AppSettingsService;
import de.kiaim.cinnamon.platform.service.UserInvitationService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.GreenMailPort;
import de.kiaim.cinnamon.test.util.WithGreenMail;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UserInvitationService}.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
@WithGreenMail
public class UserInvitationServiceTest extends ContextRequiredTest {

	@Autowired private UserInvitationService userInvitationService;
	@Autowired private UserService userService;
	@Autowired private AppSettingsService appSettingsService;
	@Autowired private UserInvitationRepository userInvitationRepository;
	@PersistenceContext private EntityManager entityManager;

	private GreenMail greenMail;
	@GreenMailPort private int greenMailPort;

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ createInvitation ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void createInvitation() throws Exception {
		final var info = userInvitationService.createInvitation(createRequest("invitee@example.com", UserRole.ROLE_API),
		                                                        "test_user");

		assertEquals("invitee@example.com", info.getEmail());
		assertEquals(UserInvitationStatus.NOT_SENT, info.getStatus());
		assertEquals(Set.of(UserRole.ROLE_API), info.getUserRoles());
		assertEquals("test_user", info.getInvitedBy());
		assertNotNull(info.getCreatedAt());
		assertNull(info.getExpiresAt());
	}

	@Test
	public void createInvitationUserNotFound() {
		final var request = createRequest("invitee@example.com");

		final var e = assertThrows(BadUserException.class,
		                           () -> userInvitationService.createInvitation(request, "unknown_user"));
		assertEquals("PLATFORM_1_16_1", e.getErrorCode());
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ getInvitationById ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void getInvitationByIdInvalidFormat() {
		final var e = assertThrows(BadArgumentException.class,
		                           () -> userInvitationService.getInvitationById("not-a-uuid"));
		assertEquals("PLATFORM_1_11_4", e.getErrorCode());
	}

	@Test
	public void getInvitationByIdNotFound() {
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.getInvitationById(UUID.randomUUID().toString()));
		assertEquals("PLATFORM_1_21_1", e.getErrorCode());
	}

	@Test
	public void getInvitationByIdReturnsInvitation() throws Exception {
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");

		final var info = userInvitationService.getInvitationById(created.getExternalId());

		assertEquals(created.getExternalId(), info.getExternalId());
		assertEquals("invitee@example.com", info.getEmail());
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ updateInvitation ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void updateInvitationNotFound() {
		final var request = createRequest("invitee@example.com");

		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.updateInvitation(UUID.randomUUID().toString(), request));
		assertEquals("PLATFORM_1_21_1", e.getErrorCode());
	}

	@Test
	public void updateInvitationDifferentEmailChangesContentAndResetsStatus() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		userInvitationService.sendInvitation(created.getExternalId(), mockRequest(created.getExternalId()));

		final var updated = userInvitationService.updateInvitation(
				created.getExternalId(), createRequest("updated@example.com", UserRole.ROLE_API));

		assertEquals("updated@example.com", updated.getEmail());
		assertEquals(UserInvitationStatus.NOT_SENT, updated.getStatus());
		assertEquals(Set.of(UserRole.ROLE_API), updated.getUserRoles());
		assertNull(updated.getExpiresAt());
		assertNull(updated.getLastSentAt());
	}

	@Test
	public void updateInvitationSameEmailChangesContentAndKeepsStatus() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		var sent = userInvitationService.sendInvitation(created.getExternalId(), mockRequest(created.getExternalId()));

		final var updated = userInvitationService.updateInvitation(
				created.getExternalId(), createRequest("invitee@example.com", UserRole.ROLE_API));

		assertEquals("invitee@example.com", updated.getEmail());
		assertEquals(UserInvitationStatus.PENDING, updated.getStatus());
		assertEquals(Set.of(UserRole.ROLE_API), updated.getUserRoles());
		assertEquals(sent.getExpiresAt(), updated.getExpiresAt());
		assertEquals(sent.getLastSentAt(), updated.getLastSentAt());
	}

	@Test
	public void updateInvitationAlreadyAcceptedFails() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		final String token = sendInvitationAndExtractToken(created.getExternalId());
		userInvitationService.acceptInvitation(token, registerRequest("invited_update_user"));

		final var request = createRequest("updated@example.com");
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.updateInvitation(created.getExternalId(), request));
		assertEquals("PLATFORM_1_21_2", e.getErrorCode());
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ sendInvitation ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void sendInvitationNotFound() {
		final var request = new MockHttpServletRequest();
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.sendInvitation(UUID.randomUUID().toString(), request));
		assertEquals("PLATFORM_1_21_1", e.getErrorCode());
	}

	@Test
	public void sendInvitationWithoutMailSettingsConfigured() throws Exception {
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");

		final var e = assertThrows(BadMailSettingsException.class,
		                           () -> userInvitationService.sendInvitation(created.getExternalId(),
		                                                                     mockRequest(created.getExternalId())));
		assertEquals("PLATFORM_1_19_1", e.getErrorCode());
	}

	@Test
	public void sendInvitationSendsMailAndUpdatesStatus() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");

		final var sent = userInvitationService.sendInvitation(created.getExternalId(), mockRequest(created.getExternalId()));

		assertEquals(UserInvitationStatus.PENDING, sent.getStatus());
		assertNotNull(sent.getLastSentAt());
		assertNotNull(sent.getExpiresAt());

		assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
		final MimeMessage[] messages = greenMail.getReceivedMessages();
		assertEquals(1, messages.length);
		assertEquals("Invitation subject", messages[0].getSubject());
		assertEquals("invitee@example.com", messages[0].getAllRecipients()[0].toString());
	}

	@Test
	public void sendInvitationAlreadyAcceptedFails() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		final String token = sendInvitationAndExtractToken(created.getExternalId());
		userInvitationService.acceptInvitation(token, registerRequest("invited_send_user"));

		final var request = mockRequest(created.getExternalId());
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.sendInvitation(created.getExternalId(), request));
		assertEquals("PLATFORM_1_21_2", e.getErrorCode());
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ revokeInvitation ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void revokeInvitationNotFound() {
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.revokeInvitation(UUID.randomUUID().toString()));
		assertEquals("PLATFORM_1_21_1", e.getErrorCode());
	}

	@Test
	public void revokeInvitationRevokesInvitation() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		userInvitationService.sendInvitation(created.getExternalId(), mockRequest(created.getExternalId()));

		final var revoked = userInvitationService.revokeInvitation(created.getExternalId());

		assertEquals(UserInvitationStatus.REVOKED, revoked.getStatus());
		assertNotNull(revoked.getRevokedAt());
	}

	@Test
	public void revokeInvitationAlreadyAcceptedFails() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		final String token = sendInvitationAndExtractToken(created.getExternalId());
		userInvitationService.acceptInvitation(token, registerRequest("invited_revoke_user"));

		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.revokeInvitation(created.getExternalId()));
		assertEquals("PLATFORM_1_21_2", e.getErrorCode());
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ acceptInvitation ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void acceptInvitationRegistersUserAndUpdatesInvitation() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(
				createRequest("invitee@example.com", UserRole.ROLE_API), "test_user");
		final String token = sendInvitationAndExtractToken(created.getExternalId());

		userInvitationService.acceptInvitation(token, registerRequest("accepted_user", "accepted@example.com"));

		final var user = userService.getUserByUsername("accepted_user");
		assertNotNull(user, "User has not been created!");
		assertEquals(Set.of(UserRole.ROLE_API), user.getUserRoles());
		assertEquals("accepted@example.com", user.getEmail());

		final var info = userInvitationService.getInvitationById(created.getExternalId());
		assertEquals(UserInvitationStatus.ACCEPTED, info.getStatus());
		assertEquals("accepted_user", info.getAcceptedBy());
		assertNotNull(info.getAcceptedAt());
	}

	@Test
	public void acceptInvitationTokenNotFound() {
		final var request = registerRequest("no_user");
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.acceptInvitation("unknown-token", request));
		assertEquals("PLATFORM_1_21_3", e.getErrorCode());
	}

	@Test
	public void acceptInvitationAfterRevokedFails() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		final String token = sendInvitationAndExtractToken(created.getExternalId());
		userInvitationService.revokeInvitation(created.getExternalId());

		final var request = registerRequest("revoked_user");
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.acceptInvitation(token, request));
		assertEquals("PLATFORM_1_21_5", e.getErrorCode());
	}

	@Test
	public void acceptInvitationTokenExpired() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		final String token = sendInvitationAndExtractToken(created.getExternalId());

		// Force the invitation to already be expired.
		final UserInvitationEntity entity =
				userInvitationRepository.findByExternalId(UUID.fromString(created.getExternalId())).orElseThrow();
		entity.setExpiresAt(new Timestamp(System.currentTimeMillis() - 1000));
		userInvitationRepository.save(entity);

		final var request = registerRequest("expired_user");
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.acceptInvitation(token, request));
		assertEquals("PLATFORM_1_21_4", e.getErrorCode());
	}

	@Test
	public void acceptInvitationTokenReuseAfterAcceptedFails() throws Exception {
		configureMailSettings();
		final var created = userInvitationService.createInvitation(createRequest("invitee@example.com"), "test_user");
		final String token = sendInvitationAndExtractToken(created.getExternalId());
		userInvitationService.acceptInvitation(token, registerRequest("first_user"));

		final var request = registerRequest("second_user");
		final var e = assertThrows(BadUserInvitationException.class,
		                           () -> userInvitationService.acceptInvitation(token, request));
		assertEquals("PLATFORM_1_21_2", e.getErrorCode());
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ deleteExpiredInvitations ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void deleteExpiredInvitationsDeletesOldAcceptedAndRevokedInvitations() throws Exception {
		configureMailSettings();

		// Accepted invitation, old enough to be deleted.
		final var accepted = userInvitationService.createInvitation(createRequest("accepted@example.com"), "test_user");
		final String acceptedToken = sendInvitationAndExtractToken(accepted.getExternalId());
		userInvitationService.acceptInvitation(acceptedToken, registerRequest("expired_accepted_user"));
		ageInvitation(accepted.getExternalId(), UserInvitationEntity::setAcceptedAt);

		// Revoked invitation, old enough to be deleted.
		final var revoked = userInvitationService.createInvitation(createRequest("revoked@example.com"), "test_user");
		userInvitationService.revokeInvitation(revoked.getExternalId());
		ageInvitation(revoked.getExternalId(), UserInvitationEntity::setRevokedAt);

		// Recently revoked invitation that should be kept despite being revoked.
		final var recentlyRevoked =
				userInvitationService.createInvitation(createRequest("recent@example.com"), "test_user");
		userInvitationService.revokeInvitation(recentlyRevoked.getExternalId());

		// Pending invitation that should be kept regardless of age.
		final var pending = userInvitationService.createInvitation(createRequest("pending@example.com"), "test_user");
		userInvitationService.sendInvitation(pending.getExternalId(), mockRequest(pending.getExternalId()));

		// Mirror the cronjob running in its own transaction/session: clear the first-level cache so the
		// following query re-reads the aged timestamps set above from the database instead of relying on
		// entities cached in memory from earlier in this test.
		entityManager.flush();
		entityManager.clear();

		userInvitationService.deleteExpiredInvitations();

		assertTrue(userInvitationRepository.findByExternalId(UUID.fromString(accepted.getExternalId())).isEmpty(),
		           "Old accepted invitation should have been deleted!");
		assertTrue(userInvitationRepository.findByExternalId(UUID.fromString(revoked.getExternalId())).isEmpty(),
		           "Old revoked invitation should have been deleted!");
		assertTrue(userInvitationRepository.findByExternalId(UUID.fromString(recentlyRevoked.getExternalId())).isPresent(),
		           "Recently revoked invitation should have been kept!");
		assertTrue(userInvitationRepository.findByExternalId(UUID.fromString(pending.getExternalId())).isPresent(),
		           "Pending invitation should have been kept!");
	}

	private void ageInvitation(final String externalId, final BiConsumer<UserInvitationEntity, Timestamp> setter) {
		final UserInvitationEntity entity =
				userInvitationRepository.findByExternalId(UUID.fromString(externalId)).orElseThrow();
		setter.accept(entity, new Timestamp(System.currentTimeMillis() - Duration.ofDays(31).toMillis()));
		userInvitationRepository.save(entity);
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ helpers ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	private void configureMailSettings() {
		final EMailSettingsDTO request = new EMailSettingsDTO();
		request.setMailHost("localhost");
		request.setMailPort(greenMailPort);
		request.setMailTLS(false);
		request.setMailSMTPAuth(false);
		request.setMailSender("no-reply@example.com");
		appSettingsService.setMailSettings(request);
	}

	private UserInvitationRequest createRequest(final String email, final UserRole... roles) {
		final UserInvitationRequest request = new UserInvitationRequest();
		request.setEmail(email);
		request.setUserRoles(roles.length == 0 ? Set.of() : Set.of(roles));
		request.setEmailCustomSubject("Invitation subject");
		// The body only contains the invitation link so that the token can be extracted from the received test mail.
		request.setEmailCustomBody("${invitation.token}");
		return request;
	}

	private MockHttpServletRequest mockRequest(final String externalId) {
		final MockHttpServletRequest request = new MockHttpServletRequest();
		request.setScheme("http");
		request.setServerName("localhost");
		request.setServerPort(80);
		request.setRequestURI("/api/admin/invitations/" + externalId + "/send");
		return request;
	}

	private String sendInvitationAndExtractToken(final String externalId) throws Exception {
		userInvitationService.sendInvitation(externalId, mockRequest(externalId));

		assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
		final MimeMessage[] messages = greenMail.getReceivedMessages();
		final String link = GreenMailUtil.getBody(messages[messages.length - 1]).trim();

		final int tokenIndex = link.indexOf("token=");
		assertTrue(tokenIndex >= 0, "Invitation link did not contain a token: " + link);
		return link.substring(tokenIndex + "token=".length());
	}

	private RegisterRequest registerRequest(final String username) {
		return registerRequest(username, null);
	}

	private RegisterRequest registerRequest(final String username, final String email) {
		final String password = "$tr0ngPa$$w0rd";
		return new RegisterRequest(username, email, password, password);
	}

}
