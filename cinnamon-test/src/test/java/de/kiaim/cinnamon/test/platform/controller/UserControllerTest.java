package de.kiaim.cinnamon.test.platform.controller;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import de.kiaim.cinnamon.platform.model.dto.UpdatePasswordRequest;
import de.kiaim.cinnamon.platform.model.dto.UpdateUsernameRequest;
import de.kiaim.cinnamon.platform.model.dto.UserInvitationInfo;
import de.kiaim.cinnamon.platform.model.dto.UserInvitationRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.AppSettingsService;
import de.kiaim.cinnamon.platform.service.UserInvitationService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Transactional
public class UserControllerTest extends ControllerTest {

	@Autowired PasswordEncoder passwordEncoder;

	@Autowired
	UserService userService;

	@Autowired
	UserInvitationService userInvitationService;

	@Autowired
	AppSettingsService appSettingsService;

	private GreenMail greenMail;
	private int greenMailPort;

	@BeforeEach
	public void setUpGreenMail() {
		greenMailPort = TestSocketUtils.findAvailableTcpPort();
		greenMail = new GreenMail(new ServerSetup(greenMailPort, null, ServerSetup.PROTOCOL_SMTP));
		greenMail.start();

		final EMailSettingsDTO mailSettings = new EMailSettingsDTO();
		mailSettings.setMailHost("localhost");
		mailSettings.setMailPort(greenMailPort);
		mailSettings.setMailTLS(false);
		mailSettings.setMailSMTPAuth(false);
		mailSettings.setMailSender("no-reply@example.com");
		appSettingsService.setMailSettings(mailSettings);
	}

	@AfterEach
	public void tearDownGreenMail() {
		greenMail.stop();
	}

	@Test
	@WithUserDetails("test_user")
	public void login() throws Exception {
		mockMvc.perform(get("/api/user/login"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{username: 'test_user', roles: ['ROLE_USER']}"));
	}

	@Test
	public void loginUnauthorized() throws Exception {
		mockMvc.perform(get("/api/user/login"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void register() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "$tr0ngPa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(jsonMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isOk());

		assertTrue(userService.doesUserWithUsernameExist(username), "User has not been created!");
		final UserEntity user = userService.loadUserByUsername(username);
		assertNotEquals(password, user.getPassword(), "Password should not be stored as clear text!");
	}

	@Test
	public void registerExisting() throws Exception {
		String username = getTestUser().getUsername();
		String password = "$tr0ngPa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(jsonMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("username", "Username is not available!"));
	}

	@Test
	public void registerMatchingPassword() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "$tr0ngPa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, "wrong_" + password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("passwordRepeated", "Passwords do not match!"));
	}

	@Test
	@WithUserDetails("test_user")
	public void deleteForbidden() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.DELETE, "/api/user/-/delete")
		                                      .param("username", getTestUser().getUsername())
		                                      .param("password", "wrong_password"))
		       .andExpect(status().isForbidden());

		assertTrue(userService.doesUserWithUsernameExist(getTestUser().getUsername()),
		           "User should have not been deleted!");
	}

	@Test
	@WithUserDetails("test_user")
	public void delete() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.DELETE, "/api/user/-/delete")
		                                      .param("username", getTestUser().getUsername())
		                                      .param("password", "changeme"))
		       .andExpect(status().isOk());

		assertFalse(userService.doesUserWithUsernameExist("test_user"), "User has not been deleted!");
	}

	@Test
	@WithUserDetails("test_user")
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext
	public void deleteWithData() throws Exception {
		var datasetId = postData();

		assertTrue(existsTable(datasetId));

		mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.DELETE, "/api/user/-/delete")
		                                      .param("username", getTestUser().getUsername())
		                                      .param("password", "changeme"))
		       .andExpect(status().isOk());

		assertFalse(userService.doesUserWithUsernameExist("test_user"), "User has not been deleted!");
		assertFalse(existsTable(datasetId));
	}

	@Test
	public void registerPasswordBlank() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "            ";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("password", "Password must not be blank!"));
	}

	@Test
	public void registerPasswordTooShort() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "Pa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("password", "Password must be at least 12 characters long!"));
	}

	@Test
	public void registerPasswordNoLowerCase() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "$TR0NGPA$$W0RD";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("password", "Password must contain at least one lowercase character!"));
	}

	@Test
	public void registerPasswordNoUpperCase() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "$tr0ngpa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("password", "Password must contain at least one uppercase character!"));
	}

	@Test
	public void registerPasswordNoNumber() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "$trongPa$$word";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("password", "Password must contain at least one digit!"));
	}

	@Test
	public void registerPasswordNoSpecialCharacter() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "Str0ngPassw0rd";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("password", "Password must contain at least one special character!"));
	}

	@Test
	public void registerPasswordTooShortNoUppercase() throws Exception {
		String username = "new_" + getTestUser().getUsername();
		String password = "pa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("password", "Password must be at least 12 characters long!",
		                                  "Password must contain at least one uppercase character!"));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ register with invitation token ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void registerWithInvitationToken() throws Exception {
		final String token = createAndSendInvitation("invitee@example.com", UserRole.ROLE_API);
		final String username = "invited_user";
		final String password = "$tr0ngPa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .param("token", token)
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(username, "invited@example.com", password, password))))
		       .andExpect(status().isOk());

		assertTrue(userService.doesUserWithUsernameExist(username), "User has not been created!");
		final UserEntity user = userService.loadUserByUsername(username);
		assertEquals(Set.of(UserRole.ROLE_API), user.getUserRoles(), "Unexpected roles!");
		assertEquals("invited@example.com", user.getEmail());
	}

	@Test
	public void registerWithInvitationTokenUnknown() throws Exception {
		final String password = "$tr0ngPa$$w0rd";

		mockMvc.perform(post("/api/user/register")
				                .param("token", "unknown-token")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest("unknown_token_user", null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_21_3"));
	}

	@Test
	public void registerWithInvitationTokenRevoked() throws Exception {
		final String token = createAndSendInvitation("invitee@example.com");
		final var invitation = getOnlyInvitation();
		userInvitationService.revokeInvitation(invitation.getExternalId());

		final String password = "$tr0ngPa$$w0rd";
		mockMvc.perform(post("/api/user/register")
				                .param("token", token)
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest("revoked_token_user", null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_21_5"));
	}

	@Test
	public void registerWithInvitationTokenExistingUsername() throws Exception {
		final String token = createAndSendInvitation("invitee@example.com");
		final String password = "$tr0ngPa$$w0rd";

		// The @Username validator rejects an unavailable username before the invitation is even looked at.
		mockMvc.perform(post("/api/user/register")
				                .param("token", token)
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(getTestUser().getUsername(), null, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("username", "Username is not available!"));
	}

	private String createAndSendInvitation(final String email, final UserRole... roles) throws Exception {
		final UserInvitationRequest request = new UserInvitationRequest();
		request.setEmail(email);
		request.setUserRoles(roles.length == 0 ? Set.of() : Set.of(roles));
		request.setEmailCustomSubject("Invitation subject");
		// The body only contains the invitation link so that the token can be extracted from the received test mail.
		request.setEmailCustomBody("${invitation.token}");

		final var created = userInvitationService.createInvitation(request, getTestUser().getUsername());

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
		return link.substring(tokenIndex + "token=".length());
	}

	private UserInvitationInfo getOnlyInvitation() {
		final var invitations = userInvitationService.getAllInvitations();
		assertEquals(1, invitations.size(), "Unexpected number of invitations!");
		return invitations.iterator().next();
	}

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ updateUsername ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	@WithUserDetails("test_user")
	public void updateUsername() throws Exception {
		mockMvc.perform(post("/api/user/-/update-username")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updateUsernameJson("changeme", "new_test_user")))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{username: 'new_test_user', roles: ['ROLE_USER']}"));

		var user = userService.getUserByUsername("new_test_user");
		assertNotNull(user);
		assertEquals("new_test_user", user.getUsername());
	}

	@Test
	@WithUserDetails("test_user")
	public void updateUsernameWrongCurrentPassword() throws Exception {
		mockMvc.perform(post("/api/user/-/update-username")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updateUsernameJson("invalid", "new_test_user")))
		       .andExpect(status().isForbidden())
		       .andExpect(errorCode("PLATFORM_1_12_2"))
		       .andExpect(errorMessage("Password incorrect!"));

		var user = getTestUser();
		assertEquals("test_user", user.getUsername());
	}

	@Test
	@WithUserDetails("test_user")
	public void updateUsernameSameAsCurrent() throws Exception {
		mockMvc.perform(post("/api/user/-/update-username")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updateUsernameJson("changeme", "test_user")))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("newUsername", "Username is not available!"));

		var user = getTestUser();
		assertEquals("test_user", user.getUsername());
	}

	@Test
	@WithUserDetails("test_user")
	public void updateUsernameAlreadyExists() throws Exception {
		mockMvc.perform(post("/api/user/-/update-username")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updateUsernameJson("changeme", "test_user")))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("newUsername", "Username is not available!"));

		var user = getTestUser();
		assertEquals("test_user", user.getUsername());
	}

	@Test
	@WithUserDetails("test_user")
	public void updateUsernameMissing() throws Exception {
		mockMvc.perform(post("/api/user/-/update-username")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updateUsernameJson("changeme", null)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("newUsername", "Username is required!"));

		var user = getTestUser();
		assertEquals("test_user", user.getUsername());
	}

	@Test
	@WithUserDetails("test_user")
	public void updateUsernameBlank() throws Exception {
		mockMvc.perform(post("/api/user/-/update-username")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updateUsernameJson("changeme", "  ")))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("newUsername", "Username must be between 1 and 255 characters long!"));

		var user = getTestUser();
		assertEquals("test_user", user.getUsername());
	}

	@Test
	@WithUserDetails("test_user")
	public void updateUsernameTooShort() throws Exception {
		mockMvc.perform(post("/api/user/-/update-username")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updateUsernameJson("changeme", "")))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("newUsername", "Username must be between 1 and 255 characters long!"));

		var user = getTestUser();
		assertEquals("test_user", user.getUsername());
	}

	@Test
	@WithUserDetails("test_user")
	public void updateUsernameTooLong() throws Exception {
		mockMvc.perform(post("/api/user/-/update-username")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updateUsernameJson("changeme", "a".repeat(256))))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("newUsername", "Username must be between 1 and 255 characters long!"));

		var user = getTestUser();
		assertEquals("test_user", user.getUsername());
	}

	private String updateUsernameJson(final String currentPassword, final String newUsername) throws Exception {
		final UpdateUsernameRequest request = new UpdateUsernameRequest();
		request.setCurrentPassword(currentPassword);
		request.setNewUsername(newUsername);
		return objectMapper.writeValueAsString(request);
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ updatePassword ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	@WithUserDetails("test_user")
	public void updatePassword() throws Exception {
		mockMvc.perform(post("/api/user/-/update-password")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updatePasswordJson("changeme", "$tr0ngPa$$w0rd", "$tr0ngPa$$w0rd")))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{username: 'test_user', roles: ['ROLE_USER']}"));

		var user = getTestUser();
		assertTrue(passwordEncoder.matches("$tr0ngPa$$w0rd", user.getPassword()));
	}

	@Test
	@WithUserDetails("test_user")
	public void updatePasswordWrongCurrentPassword() throws Exception {
		mockMvc.perform(post("/api/user/-/update-password")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updatePasswordJson("invalid", "$tr0ngPa$$w0rd", "$tr0ngPa$$w0rd")))
		       .andExpect(status().isForbidden())
		       .andExpect(errorCode("PLATFORM_1_12_2"))
		       .andExpect(errorMessage("Password incorrect!"));

		var user = getTestUser();
		assertTrue(passwordEncoder.matches("changeme", user.getPassword()));
	}

	@Test
	@WithUserDetails("test_user")
	public void updatePasswordNotMatching() throws Exception {
		mockMvc.perform(post("/api/user/-/update-password")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(updatePasswordJson("changeme", "$tr0ngPa$$w0rd", "invalid")))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("newPasswordRepeated", "Passwords do not match!"));

		var user = getTestUser();
		assertTrue(passwordEncoder.matches("changeme", user.getPassword()));
	}

	private String updatePasswordJson(final String currentPassword, final String newPassword,
	                                  final String newPasswordRepeated) throws Exception {
		final UpdatePasswordRequest request = new UpdatePasswordRequest();
		request.setCurrentPassword(currentPassword);
		request.setNewPassword(newPassword);
		request.setNewPasswordRepeated(newPasswordRepeated);
		return objectMapper.writeValueAsString(request);
	}

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ getProjects ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	@WithUserDetails("test_user")
	public void getProjects() throws Exception {
		mockMvc.perform(get("/api/user/-/projects"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 [{
		                                   "info": {
		                                     "id": "%s",
		                                     "name": "Test Project"
		                                   },
		                                   "currentStep": "WELCOME",
		                                   "stageStatuses": ["NOT_STARTED", "NOT_STARTED"]
		                                 }]
		                                 """.formatted(getTestProject().getExternalId())));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ createProject ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	@WithUserDetails("test_user")
	public void createProject() throws Exception {
		mockMvc.perform(post("/api/user/-/projects")
				                .contentType(MediaType.MULTIPART_FORM_DATA)
				                .param("projectName", "Awesome Project"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.name").value("Awesome Project"));
	}

	@Test
	@WithUserDetails("test_user")
	public void createProjectMissingName() throws Exception {
		mockMvc.perform(post("/api/user/-/projects")
				                .contentType(MediaType.MULTIPART_FORM_DATA))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_1_1"))
		       .andExpect(errorMessage("Missing parameter: 'projectName'"));
	}

	@Test
	@WithUserDetails("test_user")
	public void createProjectInvalidName() throws Exception {
		final MockMultipartFile invalidParam = new MockMultipartFile("projectName", "projectName",
		                                                             MediaType.TEXT_PLAIN_VALUE,
		                                                             "EXPERT".getBytes());

		mockMvc.perform(multipart("/api/user/-/projects")
				                .file(invalidParam)
				                .contentType(MediaType.MULTIPART_FORM_DATA))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_1_4"))
		       .andExpect(errorMessage("Parameter 'projectName' must not be a file!"));
	}
}
