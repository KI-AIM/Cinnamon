package de.kiaim.platform.controller;

import de.kiaim.platform.model.dto.RegisterRequest;
import de.kiaim.platform.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/user")
public class UserController {

	private final UserService userService;

	@Autowired
	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
	public Principal getUser(final Principal user) {
		return user;
	}

	@PostMapping("/register")
	public ResponseEntity<Object> register(
			@RequestBody RegisterRequest registerRequest
	) {
		userService.save(email, password);
	}

}
