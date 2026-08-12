package de.kiaim.cinnamon.platform.controller;


import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.exception.BadUserInvitationException;
import de.kiaim.cinnamon.platform.model.dto.*;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.AppSettingsService;
import de.kiaim.cinnamon.platform.service.EmailTemplateService;
import de.kiaim.cinnamon.platform.service.UserInvitationService;
import de.kiaim.cinnamon.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Controller for administrative actions.
 *
 * @author Daniel Preciado-Marquez
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "/api/admin", description = "API for administrative actions.")
public class AdminController {

	private final UserService userService;
	private final UserInvitationService userInvitationService;
	private final AppSettingsService appSettingsService;
	private final EmailTemplateService emailTemplateService;

	public AdminController(final UserService userService,
	                       final UserInvitationService userInvitationService,
	                       final AppSettingsService appSettingsService,
	                       final EmailTemplateService emailTemplateService) {
		this.userService = userService;
		this.userInvitationService = userInvitationService;
		this.appSettingsService = appSettingsService;
		this.emailTemplateService = emailTemplateService;
	}

	@Operation(summary = "Returns a list of all users.",
	           description = "Returns a list of all users in the system.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Response contains the list."),
	})
	@GetMapping(value = "/users",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public Set<UserInfo> getAllUsers() {
		return userService.getAllUserInfos();
	}

	@Operation(summary = "Updates the roles of a user.",
	           description = "Updates the roles of a user based on the provided request.") @ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "The roles are updated successfully. Returns the updated user information."),
			@ApiResponse(responseCode = "400", description = "Invalid request. The request body is missing or invalid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = UserInfo.class))}),
			@ApiResponse(responseCode = "404", description = "User not found.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = UserInfo.class))}),
			@ApiResponse(responseCode = "409", description = "Conflict. Attempting to remove the last admin role.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = UserInfo.class))})})
	@PatchMapping(value = "/users/roles",
				  consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE},
	              produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public UserInfo updateUserRoles(@RequestBody @Valid final AdminUserRoleChangeRequest request)
			throws ApiException {
		UserEntity updatedUser = switch (request.getAction()) {
			case ADD -> userService.addRoles(request.getUsername(), request.getRoles());
			case REMOVE -> userService.removeRoles(request.getUsername(), request.getRoles());
		};

		return userService.getUserInfo(updatedUser);
	}

	// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ Invitations ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@GetMapping(value = "/invitations")
	public Set<UserInvitationInfo> getAllInvitations() {
		return userInvitationService.getAllInvitations();
	}

	@PostMapping(value = "/invitations")
	public UserInvitationInfo createInvitation(
			@RequestBody @Valid final UserInvitationRequest request,
			@AuthenticationPrincipal final UserEntity currentUser
	) throws ApiException {
		return userInvitationService.createInvitation(request, currentUser.getUsername());
	}

	@GetMapping(value = "/invitations/{id}")
	public UserInvitationInfo getInvitation(@PathVariable("id") final Long invitationId) throws ApiException {
		return userInvitationService.getInvitationById(invitationId);
	}

	@PutMapping(value = "/invitations/{id}")
	public UserInvitationInfo updateInvitation(
			@PathVariable("id") final Long invitationId,
			@RequestBody @Valid final UserInvitationRequest request
	) throws ApiException {
		return userInvitationService.updateInvitation(invitationId, request);
	}

	@PostMapping(value = "/invitations/{id}/send")
	public UserInvitationInfo sendInvitation(@PathVariable("id") final Long invitationId) throws ApiException {
		return userInvitationService.sendInvitation(invitationId);
	}

	@PostMapping(value = "/invitations/{id}/revoke")
	public UserInvitationInfo revokeInvitation(@PathVariable("id") final Long invitationId) throws ApiException {
		return userInvitationService.revokeInvitation(invitationId);
	}

	// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ MailSettings ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Operation(summary = "Returns the mail settings of the application.",
	           description = "Returns the configured mail settings of the application. The configured password is "
	                         + "not part of the response.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Response contains the mail settings."),
			@ApiResponse(responseCode = "404", description = "The mail settings have not been configured yet.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EMailSettingsDTO.class))}),
	})
	@GetMapping(value = "/settings/mail",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public EMailSettingsDTO getMailSettings() throws ApiException {
		return appSettingsService.getMailSettings();
	}

	@Operation(summary = "Sets the mail settings of the application.",
	           description = "Creates or overwrites the mail settings of the application based on the given "
	                         + "request.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "The mail settings have been updated. Returns the updated settings."),
			@ApiResponse(responseCode = "400",
			             description = "Invalid request. The request body is missing or invalid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EMailSettingsDTO.class))}),
	})
	@PutMapping(value = "/settings/mail",
	            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE},
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public EMailSettingsDTO setMailSettings(@RequestBody @Valid final EMailSettingsDTO request) {
		return appSettingsService.setMailSettings(request);
	}

	@Operation(summary = "Sends a test mail to verify the mail settings.",
	           description = "Sends a test mail to the given address using the configured mail settings.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "The test mail has been sent."),
			@ApiResponse(responseCode = "400",
			             description = "Invalid request. The request body is missing or invalid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = TestMailRequest.class))}),
			@ApiResponse(responseCode = "404", description = "The mail settings have not been configured yet.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = TestMailRequest.class))}),
			@ApiResponse(responseCode = "500", description = "Sending the test mail failed.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = TestMailRequest.class))}),
	})
	@PostMapping(value = "/settings/mail/test",
	             consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Void> testMailSettings(@RequestBody @Valid final TestMailRequest request)
			throws ApiException {
		appSettingsService.sendTestMail(request.getMailAddress());
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Returns all email templates.",
	           description = "Returns all email templates of the application together with all languages that can "
	                         + "be configured for a template.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Response contains the templates and the languages."),
	})
	@GetMapping(value = "/settings/mail/templates",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public EmailTemplateListDTO getEmailTemplates() {
		return emailTemplateService.getEmailTemplates();
	}

	@Operation(summary = "Returns a single email template.",
	           description = "Returns the email template with the given ID.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Response contains the template."),
			@ApiResponse(responseCode = "404", description = "The template does not exist.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EmailTemplateDTO.class))}),
	})
	@GetMapping(value = "/settings/mail/templates/{id}",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public EmailTemplateDTO getEmailTemplate(@PathVariable final Long id) throws ApiException {
		return emailTemplateService.getEmailTemplate(id);
	}

	@Operation(summary = "Creates a new email template.",
	           description = "Creates a new email template based on the given request.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "The template has been created. Returns the template."),
			@ApiResponse(responseCode = "400",
			             description = "Invalid request. The request body is missing or invalid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EmailTemplateDTO.class))}),
			@ApiResponse(responseCode = "409", description = "A template with the same name already exists.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EmailTemplateDTO.class))}),
	})
	@PostMapping(value = "/settings/mail/templates",
	             consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public EmailTemplateDTO createEmailTemplate(@RequestBody @Valid final EmailTemplateDTO request)
			throws ApiException {
		return emailTemplateService.createEmailTemplate(request);
	}

	@Operation(summary = "Updates an email template.",
	           description = "Updates the email template with the given ID. The request contains the complete "
	                         + "content of the template, so languages that are not part of the request are removed.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "The template has been updated. Returns the updated template."),
			@ApiResponse(responseCode = "400",
			             description = "Invalid request. The request body is missing or invalid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EmailTemplateDTO.class))}),
			@ApiResponse(responseCode = "404", description = "The template does not exist.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EmailTemplateDTO.class))}),
			@ApiResponse(responseCode = "409", description = "Another template with the same name already exists.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EmailTemplateDTO.class))}),
	})
	@PutMapping(value = "/settings/mail/templates/{id}",
	            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE},
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public EmailTemplateDTO updateEmailTemplate(@PathVariable final Long id,
	                                            @RequestBody @Valid final EmailTemplateDTO request)
			throws ApiException {
		return emailTemplateService.updateEmailTemplate(id, request);
	}

	@Operation(summary = "Deletes an email template.",
	           description = "Deletes the email template with the given ID and all its content.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "The template has been deleted."),
			@ApiResponse(responseCode = "404", description = "The template does not exist.",
			             content = {@Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = EmailTemplateDTO.class))}),
	})
	@DeleteMapping(value = "/settings/mail/templates/{id}")
	public ResponseEntity<Void> deleteEmailTemplate(@PathVariable final Long id) throws ApiException {
		emailTemplateService.deleteEmailTemplate(id);
		return ResponseEntity.ok().build();
	}

}
