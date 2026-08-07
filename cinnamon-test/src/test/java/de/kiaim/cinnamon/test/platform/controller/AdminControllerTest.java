package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the mail settings endpoints of {@link de.kiaim.cinnamon.platform.controller.AdminController}.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class AdminControllerTest extends ControllerTest {

	private static final String ADMIN_USER = "admin_user";
	private static final String ADMIN_PASSWORD = "changeme";

	@Autowired
	private UserService userService;

	@BeforeEach
	public void createAdminUser() throws BadUserException {
		userService.register(ADMIN_USER, ADMIN_PASSWORD, Set.of(UserRole.ROLE_ADMIN));
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

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ helpers ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	private void setMailSettings(final String host) throws Exception {
		mockMvc.perform(put("/api/admin/settings/mail")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest(host))))
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

}
