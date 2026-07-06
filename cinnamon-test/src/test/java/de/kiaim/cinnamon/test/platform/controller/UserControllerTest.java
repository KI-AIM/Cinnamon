package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Transactional
public class UserControllerTest extends ControllerTest {

	@Autowired PasswordEncoder passwordEncoder;

	@Autowired
	UserService userService;

	@Test
	@WithUserDetails("test_user")
	public void login() throws Exception {
		mockMvc.perform(get("/api/user/login"))
		       .andExpect(status().isOk())
		       .andExpect(content().string("true"));
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
						                new RegisterRequest(username, password, password))))
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
				                .content(
						                jsonMapper.writeValueAsString(new RegisterRequest(username, password, password))))
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
						                new RegisterRequest(username, password, "wrong_" + password))))
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
						                new RegisterRequest(username, password, password))))
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
						                new RegisterRequest(username, password, password))))
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
						                new RegisterRequest(username, password, password))))
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
						                new RegisterRequest(username, password, password))))
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
						                new RegisterRequest(username, password, password))))
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
						                new RegisterRequest(username, password, password))))
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
						                new RegisterRequest(username, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("password", "Password must be at least 12 characters long!",
		                                  "Password must contain at least one uppercase character!"));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ updatePassword ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	@WithUserDetails("test_user")
	public void updatePassword() throws Exception {
		mockMvc.perform(post("/api/user/-/update-password")
				                .contentType(MediaType.MULTIPART_FORM_DATA)
				                .param("currentPassword", "changeme")
				                .param("newPassword", "$tr0ngPa$$w0rd")
				                .param("newPasswordRepeated", "$tr0ngPa$$w0rd")
		       )
		       .andExpect(status().isOk());

		var user = getTestUser();
		assertTrue(passwordEncoder.matches("$tr0ngPa$$w0rd", user.getPassword()));
	}

	@Test
	@WithUserDetails("test_user")
	public void updatePasswordWrongCurrentPassword() throws Exception {
		mockMvc.perform(post("/api/user/-/update-password")
				                .contentType(MediaType.MULTIPART_FORM_DATA)
				                .param("currentPassword", "invalid")
				                .param("newPassword", "$tr0ngPa$$w0rd")
				                .param("newPasswordRepeated", "$tr0ngPa$$w0rd")
		       )
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
				                .contentType(MediaType.MULTIPART_FORM_DATA)
				                .param("currentPassword", "changeme")
				                .param("newPassword", "$tr0ngPa$$w0rd")
				                .param("newPasswordRepeated", "invalid")
		       )
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("newPasswordRepeated", "Passwords do not match!"));

		var user = getTestUser();
		assertTrue(passwordEncoder.matches("changeme", user.getPassword()));
	}

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
