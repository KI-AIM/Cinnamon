package de.kiaim.cinnamon.test.platform.controller;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.model.dto.AdminUserRoleChangeRequest;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.dto.TestMailRequest;
import de.kiaim.cinnamon.platform.model.dto.UserInfo;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import jakarta.mail.internet.MimeMessage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the user management and mail settings endpoints of
 * {@link de.kiaim.cinnamon.platform.controller.AdminController}.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class AdminControllerTest extends ControllerTest {

	private static final String ADMIN_USER = "admin_user";
	private static final String ADMIN_PASSWORD = "changeme";

	@Autowired
	private UserService userService;

	private GreenMail greenMail;
	private int greenMailPort;

	@BeforeEach
	public void createAdminUser() throws BadUserException {
		userService.register(ADMIN_USER, ADMIN_PASSWORD, Set.of(UserRole.ROLE_ADMIN), null);
	}

	@BeforeEach
	public void setUpGreenMail() {
		greenMailPort = TestSocketUtils.findAvailableTcpPort();
		greenMail = new GreenMail(new ServerSetup(greenMailPort, null, ServerSetup.PROTOCOL_SMTP));
		greenMail.start();
	}

	@AfterEach
	public void tearDownGreenMail() {
		greenMail.stop();
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ GET /api/admin/users ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void getAllUsersUnauthorized() throws Exception {
		mockMvc.perform(get("/api/admin/users"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void getAllUsersForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(get("/api/admin/users").with(httpBasic(getTestUser().getUsername(), "changeme")))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void getAllUsersReturnsAllUsers() throws Exception {
		final String response = mockMvc.perform(get("/api/admin/users").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		                               .andExpect(status().isOk())
		                               .andReturn().getResponse().getContentAsString();

		final UserInfo[] users = objectMapper.readValue(response, UserInfo[].class);
		final Map<String, Set<UserRole>> rolesByUsername =
				Arrays.stream(users).collect(Collectors.toMap(UserInfo::getUsername, UserInfo::getRoles));

		assertEquals(Set.of(UserRole.ROLE_ADMIN), rolesByUsername.get(ADMIN_USER), "Unexpected admin roles!");
		assertEquals(Set.of(UserRole.ROLE_USER), rolesByUsername.get(getTestUser().getUsername()),
		             "Unexpected roles for test user!");
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ PATCH /api/admin/users/roles ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void updateUserRolesUnauthorized() throws Exception {
		mockMvc.perform(patch("/api/admin/users/roles")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                createRoleChangeRequest(getTestUser().getUsername(),
						                                        AdminUserRoleChangeRequest.Action.ADD,
						                                        UserRole.ROLE_API))))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void updateUserRolesForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(getTestUser().getUsername(), "changeme"))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                createRoleChangeRequest(getTestUser().getUsername(),
						                                        AdminUserRoleChangeRequest.Action.ADD,
						                                        UserRole.ROLE_API))))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void updateUserRolesWithBlankUsername() throws Exception {
		final AdminUserRoleChangeRequest request =
				createRoleChangeRequest(" ", AdminUserRoleChangeRequest.Action.ADD, UserRole.ROLE_API);

		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("username", "Username must not be blank."));
	}

	@Test
	public void updateUserRolesWithoutAction() throws Exception {
		final AdminUserRoleChangeRequest request =
				createRoleChangeRequest(getTestUser().getUsername(), null, UserRole.ROLE_API);

		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("action", "Action must not be null."));
	}

	@Test
	public void updateUserRolesWithInvalidAction() throws Exception {
		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content("{\"username\":\"" + getTestUser().getUsername() +
				                         "\",\"action\":\"INVALID_ACTION\",\"roles\":[\"ROLE_API\"]}"))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("action", "Invalid value 'INVALID_ACTION'. Possible values are: [ADD, REMOVE]"));
	}

	@Test
	public void updateUserRolesWithoutRoles() throws Exception {
		final AdminUserRoleChangeRequest request =
				createRoleChangeRequest(getTestUser().getUsername(), AdminUserRoleChangeRequest.Action.ADD);

		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("roles", "At least one role must be specified."));
	}

	@Test
	public void updateUserRolesUserNotFound() throws Exception {
		final AdminUserRoleChangeRequest request =
				createRoleChangeRequest("unknown_user", AdminUserRoleChangeRequest.Action.ADD, UserRole.ROLE_API);

		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isNotFound())
		       .andExpect(errorCode("PLATFORM_1_16_1"));
	}

	@Test
	public void updateUserRolesAddsRole() throws Exception {
		final AdminUserRoleChangeRequest request =
				createRoleChangeRequest(getTestUser().getUsername(), AdminUserRoleChangeRequest.Action.ADD,
				                        UserRole.ROLE_API);

		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.username").value(getTestUser().getUsername()))
		       .andExpect(jsonPath("$.roles", Matchers.containsInAnyOrder("ROLE_USER", "ROLE_API")));
	}

	@Test
	public void updateUserRolesRemovesRole() throws Exception {
		addRole(getTestUser().getUsername(), UserRole.ROLE_API);

		final AdminUserRoleChangeRequest request =
				createRoleChangeRequest(getTestUser().getUsername(), AdminUserRoleChangeRequest.Action.REMOVE,
				                        UserRole.ROLE_API);

		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.username").value(getTestUser().getUsername()))
		       .andExpect(jsonPath("$.roles", Matchers.containsInAnyOrder("ROLE_USER")));
	}

	@Test
	public void updateUserRolesRemovingLastAdminRoleFails() throws Exception {
		final AdminUserRoleChangeRequest request =
				createRoleChangeRequest(ADMIN_USER, AdminUserRoleChangeRequest.Action.REMOVE, UserRole.ROLE_ADMIN);

		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isConflict())
		       .andExpect(errorCode("PLATFORM_1_18_1"));
	}

	@Test
	public void updateUserRolesRemovingAdminRoleSucceedsWithOtherAdminPresent() throws Exception {
		userService.register("second_admin", "changeme", Set.of(UserRole.ROLE_ADMIN), null);

		final AdminUserRoleChangeRequest request =
				createRoleChangeRequest(ADMIN_USER, AdminUserRoleChangeRequest.Action.REMOVE, UserRole.ROLE_ADMIN);

		mockMvc.perform(patch("/api/admin/users/roles")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.roles", Matchers.not(Matchers.hasItem("ROLE_ADMIN"))));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ GET /api/admin/settings/mail ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void getMailSettingsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/admin/settings/mail"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void getMailSettingsForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(get("/api/admin/settings/mail").with(httpBasic(getTestUser().getUsername(), "changeme")))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void getMailSettingsNotConfigured() throws Exception {
		mockMvc.perform(get("/api/admin/settings/mail").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		       .andExpect(status().isNotFound())
		       .andExpect(errorCode("PLATFORM_1_19_1"));
	}

	@Test
	public void getMailSettingsReturnsConfiguredSettings() throws Exception {
		setMailSettings("mail.example.com");

		mockMvc.perform(get("/api/admin/settings/mail").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                   mailHost: 'mail.example.com',
		                                   mailPort: 587,
		                                   mailTLS: true,
		                                   mailSMTPAuth: true,
		                                   mailUsername: 'mailer',
		                                   mailPasswordSet: true,
		                                   mailSender: 'no-reply@example.com'
		                                 }"""))
		       .andExpect(jsonPath("$.mailPassword").doesNotExist());
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ PUT /api/admin/settings/mail ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void setMailSettingsUnauthorized() throws Exception {
		mockMvc.perform(put("/api/admin/settings/mail")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("mail.example.com"))))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void setMailSettingsForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(getTestUser().getUsername(), "changeme"))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("mail.example.com"))))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void setMailSettingsWithBlankHost() throws Exception {
		final EMailSettingsDTO request = createRequest("mail.example.com");
		request.setMailHost(" ");

		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("mailHost", "Host must not be blank."));
	}

	@Test
	public void setMailSettingsWithInvalidPort() throws Exception {
		final EMailSettingsDTO request = createRequest("mail.example.com");
		request.setMailPort(0);

		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("mailPort", "Port must be between 1 and 65535."));
	}

	@Test
	public void setMailSettingsWithInvalidSender() throws Exception {
		final EMailSettingsDTO request = createRequest("mail.example.com");
		request.setMailSender("not-an-email");

		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("mailSender", "Sender must be a valid email address."));
	}

	@Test
	public void setMailSettingsWithBlankUsernameAndSmtpAuth() throws Exception {
		final EMailSettingsDTO request = createRequest("mail.example.com");
		request.setMailUsername(" ");

		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("mailUsername",
		                                  "Username must not be blank if SMTP authentication is enabled."));
	}

	@Test
	public void setMailSettingsWithoutCredentialsAndWithoutSmtpAuth() throws Exception {
		final EMailSettingsDTO request = createRequest("mail.example.com");
		request.setMailSMTPAuth(false);
		request.setMailUsername(null);
		request.setMailPassword(null);

		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.mailSMTPAuth").value(false))
		       .andExpect(jsonPath("$.mailUsername").doesNotExist())
		       .andExpect(jsonPath("$.mailPasswordSet").value(false));
	}

	@Test
	public void setMailSettingsCreatesSettings() throws Exception {
		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("mail.example.com"))))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                   mailHost: 'mail.example.com',
		                                   mailPort: 587,
		                                   mailTLS: true,
		                                   mailSMTPAuth: true,
		                                   mailUsername: 'mailer',
		                                   mailPasswordSet: true,
		                                   mailSender: 'no-reply@example.com'
		                                 }"""))
		       .andExpect(jsonPath("$.mailPassword").doesNotExist());
	}

	@Test
	public void setMailSettingsOverwritesExistingSettings() throws Exception {
		setMailSettings("mail.example.com");

		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("mail2.example.com"))))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.mailHost").value("mail2.example.com"));

		mockMvc.perform(get("/api/admin/settings/mail").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.mailHost").value("mail2.example.com"));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ POST /api/admin/settings/mail/test ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void testMailSettingsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/admin/settings/mail/test")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                createTestMailRequest("recipient@example.com"))))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void testMailSettingsForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(post("/api/admin/settings/mail/test")
				                .with(httpBasic(getTestUser().getUsername(), "changeme"))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                createTestMailRequest("recipient@example.com"))))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void testMailSettingsNotConfigured() throws Exception {
		mockMvc.perform(post("/api/admin/settings/mail/test")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                createTestMailRequest("recipient@example.com"))))
		       .andExpect(status().isNotFound())
		       .andExpect(errorCode("PLATFORM_1_19_1"));
	}

	@Test
	public void testMailSettingsWithBlankAddress() throws Exception {
		mockMvc.perform(post("/api/admin/settings/mail/test")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createTestMailRequest(" "))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("mailAddress", "Mail address must not be blank.",
		                                  "Mail address must be a valid email address."));
	}

	@Test
	public void testMailSettingsWithInvalidAddress() throws Exception {
		mockMvc.perform(post("/api/admin/settings/mail/test")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createTestMailRequest("not-an-email"))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("mailAddress", "Mail address must be a valid email address."));
	}

	@Test
	public void testMailSettingsSendsMail() throws Exception {
		setMailSettings(createGreenMailRequest());

		mockMvc.perform(post("/api/admin/settings/mail/test")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                createTestMailRequest("recipient@example.com"))))
		       .andExpect(status().isOk());

		assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
		final MimeMessage[] messages = greenMail.getReceivedMessages();
		assertEquals(1, messages.length);
		assertEquals("Cinnamon test mail", messages[0].getSubject());
		assertEquals("no-reply@example.com", messages[0].getFrom()[0].toString());
		assertEquals("recipient@example.com", messages[0].getAllRecipients()[0].toString());
	}

	@Test
	public void testMailSettingsFailsWhenServerUnreachable() throws Exception {
		setMailSettings(createGreenMailRequest());
		greenMail.stop();

		mockMvc.perform(post("/api/admin/settings/mail/test")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                createTestMailRequest("recipient@example.com"))))
		       .andExpect(status().isInternalServerError())
		       .andExpect(errorCode("PLATFORM_2_9_1"));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ helpers ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	private AdminUserRoleChangeRequest createRoleChangeRequest(final String username,
	                                                           final AdminUserRoleChangeRequest.Action action,
	                                                           final UserRole... roles) {
		final AdminUserRoleChangeRequest request = new AdminUserRoleChangeRequest();
		request.setUsername(username);
		request.setAction(action);
		request.setRoles(roles.length == 0 ? Set.of() : Set.of(roles));
		return request;
	}

	private void addRole(final String username, final UserRole role) throws BadUserException {
		userService.addRoles(username, Set.of(role));
	}

	private void setMailSettings(final String host) throws Exception {
		setMailSettings(createRequest(host));
	}

	private void setMailSettings(final EMailSettingsDTO request) throws Exception {
		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isOk());
	}

	private EMailSettingsDTO createRequest(final String host) {
		final EMailSettingsDTO request = new EMailSettingsDTO();
		request.setMailHost(host);
		request.setMailPort(587);
		request.setMailTLS(true);
		request.setMailSMTPAuth(true);
		request.setMailUsername("mailer");
		request.setMailPassword("changeme");
		request.setMailSender("no-reply@example.com");
		return request;
	}

	/**
	 * Settings pointing at the embedded GreenMail test server, used to actually verify mails are sent.
	 */
	private EMailSettingsDTO createGreenMailRequest() {
		final EMailSettingsDTO request = createRequest("localhost");
		request.setMailPort(greenMailPort);
		request.setMailTLS(false);
		request.setMailSMTPAuth(false);
		return request;
	}

	private TestMailRequest createTestMailRequest(final String mailAddress) {
		final TestMailRequest request = new TestMailRequest();
		request.setMailAddress(mailAddress);
		return request;
	}

}
