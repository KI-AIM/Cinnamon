package de.kiaim.cinnamon.platform.controller;


import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.model.dto.AdminUserRoleChangeRequest;
import de.kiaim.cinnamon.platform.model.dto.UserInfo;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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

	public AdminController(final UserService userService) {
		this.userService = userService;
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
	           description = "Updates the roles of a user based on the provided request.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "The roles are updated successfully. Returns the updated user information."),
			@ApiResponse(responseCode = "400",
			             description = "Invalid request. The request body is missing or invalid."),
			@ApiResponse(responseCode = "404", description = "User not found."),
			@ApiResponse(responseCode = "409", description = "Conflict. Attempting to remove the last admin role.")
	})
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

}
