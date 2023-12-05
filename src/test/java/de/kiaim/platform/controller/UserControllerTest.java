package de.kiaim.platform.controller;

import de.kiaim.platform.ControllerTest;
import de.kiaim.platform.model.dto.RegisterRequest;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserControllerTest extends ControllerTest {

	@Autowired
	UserService userService;

	@Test
	public void register() throws Exception {
		String mail = "new_" + user.getUsername();
		String password = "password";

		mockMvc.perform(post("/api/user/register")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(
						                new RegisterRequest(mail, password, password))))
		       .andExpect(status().isOk());

		assertTrue(userService.doesUserWithEmailExist(mail), "User has not been created!");
		final UserEntity user = userService.loadUserByUsername(mail);
		assertNotEquals(password, user.getPassword(), "Password should not be stored as clear text!");
	}

	@Test
	public void registerExisting() throws Exception {
		String mail = user.getUsername();
		String password = "password";

		final String response = mockMvc.perform(post("/api/user/register")
				                                        .contentType(MediaType.APPLICATION_JSON_VALUE)
				                                        .content(objectMapper.writeValueAsString(
						                                        new RegisterRequest(mail, password, password))))
		                               .andExpect(status().isBadRequest())
		                               .andReturn().getResponse().getContentAsString();
		testValidationError(response, "email", "Email is not available!");
	}

	@Test
	public void registerMatchingPassword() throws Exception {
		String mail = "new_" + user.getUsername();
		String password = "password";

		final String response = mockMvc.perform(post("/api/user/register")
				                                        .contentType(MediaType.APPLICATION_JSON_VALUE)
				                                        .content(objectMapper.writeValueAsString(
						                                        new RegisterRequest(mail, password,
						                                                            "wrong_" + password))))
		                               .andExpect(status().isBadRequest())
		                               .andReturn().getResponse().getContentAsString();
		testValidationError(response, "passwordRepeated", "Passwords do not match!");
	}
}
