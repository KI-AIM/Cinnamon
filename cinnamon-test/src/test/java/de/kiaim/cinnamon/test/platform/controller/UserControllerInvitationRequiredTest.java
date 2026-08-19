package de.kiaim.cinnamon.test.platform.controller;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import de.kiaim.cinnamon.platform.model.dto.UserInvitationRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.AppSettingsService;
import de.kiaim.cinnamon.platform.service.UserInvitationService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.GreenMailPort;
import de.kiaim.cinnamon.test.util.WithGreenMail;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@code POST /api/user/register} while {@code cinnamon.users.invitation.is-required} is enabled.
 * Uses a dedicated Spring context because the flag is only read once, at application startup.
 *
 * @author Daniel Preciado-Marquez
 */
@TestPropertySource(properties = "cinnamon.users.invitation.is-required=true")
@Transactional
@WithGreenMail
public class UserControllerInvitationRequiredTest extends ControllerTest {

	@Autowired
	private UserService userService;
	@Autowired
	private UserInvitationService userInvitationService;
	@Autowired
	private AppSettingsService appSettingsService;

	private GreenMail greenMail;
	@GreenMailPort private int greenMailPort;

	@BeforeEach
	public void configureMailSettings() {
		final EMailSettingsDTO mailSettings = new EMailSettingsDTO();
		mailSettings.setMailHost("localhost");
		mailSettings.setMailPort(greenMailPort);
		mailSettings.setMailTLS(false);
		mailSettings.setMailSMTPAuth(false);
		mailSettings.setMailSender("no-reply@example.com");
		appSettingsService.setMailSettings(mailSettings);
	}

	@Test
	public void registerFailsWhenInvitationRequired() throws Exception {
		final String password = "$tr0ngPa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest("blocked_user", null, password, password))))
		       .andExpect(status().isConflict())
		       .andExpect(errorCode("PLATFORM_1_18_2"));

		assertFalse(userService.doesUserWithUsernameExist("blocked_user"), "User should not have been created!");
	}

	@Test
	public void registerWithInvitationTokenSucceedsWhileInvitationRequired() throws Exception {
		final UserInvitationRequest invitationRequest = new UserInvitationRequest();
		invitationRequest.setEmail("invitee@example.com");
		invitationRequest.setUserRoles(Set.of(UserRole.ROLE_API));
		invitationRequest.setEmailCustomSubject("Invitation subject");
		invitationRequest.setEmailCustomBody("${invitation.token}");

		final var created = userInvitationService.createInvitation(invitationRequest, getTestUser().getUsername());

		final var mockRequest = new MockHttpServletRequest();
		mockRequest.setScheme("http");
		mockRequest.setServerName("localhost");
		mockRequest.setServerPort(80);
		mockRequest.setRequestURI("/api/admin/invitations/" + created.getExternalId() + "/send");
		userInvitationService.sendInvitation(created.getExternalId(), mockRequest);

		assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
		final MimeMessage[] messages = greenMail.getReceivedMessages();
		final String link = GreenMailUtil.getBody(messages[messages.length - 1]).trim();
		final int tokenIndex = link.indexOf("token=");
		assertTrue(tokenIndex >= 0, "Invitation link did not contain a token: " + link);
		final String token = link.substring(tokenIndex + "token=".length());

		final String password = "$tr0ngPa$$w0rd";
		mockMvc.perform(post("/api/user/register")
				                .param("token", token)
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest("invited_user", null, password, password))))
		       .andExpect(status().isOk());

		final UserEntity user = userService.getUserByUsername("invited_user");
		assertNotNull(user, "User has not been created!");
		assertEquals(Set.of(UserRole.ROLE_API), user.getUserRoles(), "Unexpected roles!");
	}

}
