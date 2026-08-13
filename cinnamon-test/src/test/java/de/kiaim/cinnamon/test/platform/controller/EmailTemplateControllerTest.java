package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateDTO;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateItemDTO;
import de.kiaim.cinnamon.platform.model.enumeration.SupportedLanguage;
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
 * Tests for the email template endpoints of {@link de.kiaim.cinnamon.platform.controller.AdminController}.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class EmailTemplateControllerTest extends ControllerTest {

	private static final String ADMIN_USER = "admin_user";
	private static final String ADMIN_PASSWORD = "changeme";

	private static final String TEMPLATES_PATH = "/api/admin/settings/mail/templates";

	@Autowired
	private UserService userService;

	@BeforeEach
	public void createAdminUser() throws BadUserException {
		userService.register(ADMIN_USER, ADMIN_PASSWORD, Set.of(UserRole.ROLE_ADMIN), null);
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ GET /api/admin/settings/mail/templates ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void getEmailTemplatesUnauthorized() throws Exception {
		mockMvc.perform(get(TEMPLATES_PATH))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void getEmailTemplatesForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(get(TEMPLATES_PATH).with(httpBasic(getTestUser().getUsername(), "changeme")))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void getEmailTemplatesReturnsSupportedLanguages() throws Exception {
		mockMvc.perform(get(TEMPLATES_PATH).with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.templates").isEmpty())
		       .andExpect(jsonPath("$.languages[0].name").value("ENGLISH"))
		       .andExpect(jsonPath("$.languages[0].displayName").value("English"))
		       .andExpect(jsonPath("$.languages[1].name").value("GERMAN"))
		       .andExpect(jsonPath("$.languages[1].displayName").value("German"));
	}

	@Test
	public void getEmailTemplatesReturnsCreatedTemplates() throws Exception {
		createTemplate(createRequest("Registration", "Welcome", "Hello!"));

		mockMvc.perform(get(TEMPLATES_PATH).with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.templates.length()").value(1))
		       .andExpect(jsonPath("$.templates[0].name").value("Registration"))
		       .andExpect(jsonPath("$.templates[0].items.length()").value(1))
		       .andExpect(jsonPath("$.templates[0].items[0].language").value("ENGLISH"))
		       .andExpect(jsonPath("$.templates[0].items[0].subject").value("Welcome"))
		       .andExpect(jsonPath("$.templates[0].items[0].body").value("Hello!"));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ POST /api/admin/settings/mail/templates ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void createEmailTemplateUnauthorized() throws Exception {
		mockMvc.perform(post(TEMPLATES_PATH)
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("Registration", "Welcome",
				                                                                       "Hello!"))))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void createEmailTemplateForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(post(TEMPLATES_PATH)
				                .with(httpBasic(getTestUser().getUsername(), "changeme"))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("Registration", "Welcome",
				                                                                       "Hello!"))))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void createEmailTemplateCreatesTemplate() throws Exception {
		mockMvc.perform(post(TEMPLATES_PATH)
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("Registration", "Welcome",
				                                                                       "Hello!"))))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").isNumber())
		       .andExpect(jsonPath("$.name").value("Registration"))
		       .andExpect(jsonPath("$.items[0].language").value("ENGLISH"));
	}

	@Test
	public void createEmailTemplateWithMultipleLanguages() throws Exception {
		final EmailTemplateDTO request = createRequest("Registration", "Welcome", "Hello!");
		request.getItems().add(createItem(SupportedLanguage.GERMAN, "Willkommen", "Hallo!"));

		mockMvc.perform(post(TEMPLATES_PATH)
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.items.length()").value(2))
		       .andExpect(jsonPath("$.items[0].language").value("ENGLISH"))
		       .andExpect(jsonPath("$.items[1].language").value("GERMAN"))
		       .andExpect(jsonPath("$.items[1].subject").value("Willkommen"))
		       .andExpect(jsonPath("$.items[1].body").value("Hallo!"));
	}

	@Test
	public void createEmailTemplateWithBlankName() throws Exception {
		final EmailTemplateDTO request = createRequest(" ", "Welcome", "Hello!");

		mockMvc.perform(post(TEMPLATES_PATH)
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("name", "Name must not be blank."));
	}

	@Test
	public void createEmailTemplateWithoutContent() throws Exception {
		final EmailTemplateDTO request = createRequest("Registration", "Welcome", "Hello!");
		request.getItems().clear();

		mockMvc.perform(post(TEMPLATES_PATH)
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("items", "The template must be configured for at least one language."));
	}

	@Test
	public void createEmailTemplateWithBlankSubject() throws Exception {
		final EmailTemplateDTO request = createRequest("Registration", " ", "Hello!");

		mockMvc.perform(post(TEMPLATES_PATH)
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("items[0].subject", "Subject must not be blank."));
	}

	@Test
	public void createEmailTemplateWithDuplicatedLanguage() throws Exception {
		final EmailTemplateDTO request = createRequest("Registration", "Welcome", "Hello!");
		request.getItems().add(createItem(SupportedLanguage.ENGLISH, "Welcome again", "Hello again!"));

		mockMvc.perform(post(TEMPLATES_PATH)
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(request)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("items", "Each language must not be configured more than once."));
	}

	@Test
	public void createEmailTemplateWithExistingName() throws Exception {
		createTemplate(createRequest("Registration", "Welcome", "Hello!"));

		mockMvc.perform(post(TEMPLATES_PATH)
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("Registration", "Other",
				                                                                       "Other body"))))
		       .andExpect(status().isConflict())
		       .andExpect(errorCode("PLATFORM_1_20_2"));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ PUT /api/admin/settings/mail/templates/{id} ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void updateEmailTemplateForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(put(TEMPLATES_PATH + "/1")
				                .with(httpBasic(getTestUser().getUsername(), "changeme"))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("Registration", "Welcome",
				                                                                       "Hello!"))))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void updateEmailTemplateUpdatesTemplate() throws Exception {
		final Long id = createTemplate(createRequest("Registration", "Welcome", "Hello!"));

		mockMvc.perform(put(TEMPLATES_PATH + "/" + id)
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("Confirmation", "Confirm",
				                                                                       "Please confirm!"))))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(id.intValue()))
		       .andExpect(jsonPath("$.name").value("Confirmation"))
		       .andExpect(jsonPath("$.items[0].subject").value("Confirm"))
		       .andExpect(jsonPath("$.items[0].body").value("Please confirm!"));
	}

	@Test
	public void updateEmailTemplateNotFound() throws Exception {
		mockMvc.perform(put(TEMPLATES_PATH + "/42")
				                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .content(objectMapper.writeValueAsString(createRequest("Registration", "Welcome",
				                                                                       "Hello!"))))
		       .andExpect(status().isNotFound())
		       .andExpect(errorCode("PLATFORM_1_20_1"));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ DELETE /api/admin/settings/mail/templates/{id} ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void deleteEmailTemplateForbiddenWithoutAdminRole() throws Exception {
		mockMvc.perform(delete(TEMPLATES_PATH + "/1").with(httpBasic(getTestUser().getUsername(), "changeme")))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void deleteEmailTemplateDeletesTemplate() throws Exception {
		final Long id = createTemplate(createRequest("Registration", "Welcome", "Hello!"));

		mockMvc.perform(delete(TEMPLATES_PATH + "/" + id).with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		       .andExpect(status().isOk());

		mockMvc.perform(get(TEMPLATES_PATH).with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.templates").isEmpty());
	}

	@Test
	public void deleteEmailTemplateNotFound() throws Exception {
		mockMvc.perform(delete(TEMPLATES_PATH + "/42").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
		       .andExpect(status().isNotFound())
		       .andExpect(errorCode("PLATFORM_1_20_1"));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ helpers ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	private Long createTemplate(final EmailTemplateDTO request) throws Exception {
		final String response = mockMvc.perform(post(TEMPLATES_PATH)
				                                        .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
				                                        .contentType(MediaType.APPLICATION_JSON_VALUE)
				                                        .content(objectMapper.writeValueAsString(request)))
		                               .andExpect(status().isOk())
		                               .andReturn().getResponse().getContentAsString();

		return objectMapper.readValue(response, EmailTemplateDTO.class).getId();
	}

	private EmailTemplateDTO createRequest(final String name, final String subject, final String body) {
		final EmailTemplateDTO request = new EmailTemplateDTO();
		request.setName(name);
		request.getItems().add(createItem(SupportedLanguage.ENGLISH, subject, body));
		return request;
	}

	private EmailTemplateItemDTO createItem(final SupportedLanguage language, final String subject,
	                                        final String body) {
		final EmailTemplateItemDTO item = new EmailTemplateItemDTO();
		item.setLanguage(language);
		item.setSubject(subject);
		item.setBody(body);
		return item;
	}

}
