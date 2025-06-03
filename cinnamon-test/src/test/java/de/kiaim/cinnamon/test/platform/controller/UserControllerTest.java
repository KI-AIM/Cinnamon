package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class UserControllerTest extends ControllerTest {

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
		String mail = "new_" + getTestUser().getUsername();
		String password = "password";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(jsonMapper.writeValueAsString(
						                new RegisterRequest(mail, password, password))))
		       .andExpect(status().isOk());

		assertTrue(userService.doesUserWithEmailExist(mail), "User has not been created!");
		final UserEntity user = userService.loadUserByUsername(mail);
		assertNotEquals(password, user.getPassword(), "Password should not be stored as clear text!");
	}

	@Test
	public void registerExisting() throws Exception {
		String mail = getTestUser().getUsername();
		String password = "password";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(
						                jsonMapper.writeValueAsString(new RegisterRequest(mail, password, password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("email", "Project name is not available!"));
	}

	@Test
	public void registerMatchingPassword() throws Exception {
		String mail = "new_" + getTestUser().getUsername();
		String password = "password";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(mail, password, "wrong_" + password))))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("passwordRepeated", "Passwords do not match!"));
	}

	@Test
	@WithUserDetails("test_user")
	public void deleteForbidden() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.DELETE, "/api/user/delete")
		                                      .param("email", getTestUser().getUsername())
		                                      .param("password", "wrong_password"))
		       .andExpect(status().isForbidden());

		assertTrue(userService.doesUserWithEmailExist(getTestUser().getUsername()),
		           "User should have not been deleted!");
	}

	@Test
	@WithUserDetails("test_user")
	public void delete() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.DELETE, "/api/user/delete")
		                                      .param("email", getTestUser().getUsername())
		                                      .param("password", "changeme"))
		       .andExpect(status().isOk());

		assertFalse(userService.doesUserWithEmailExist("test_user"), "User has not been deleted!");
	}
}
