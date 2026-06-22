package com.etic.system.auth.adapter.web;

import com.etic.system.auth.application.AuthenticationService;
import com.etic.system.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

	private static final String USER_SESSION_KEY = "authenticatedUser";
	private final AuthenticationService authenticationService;

	public AuthenticationController(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@PostMapping("/login")
	public AuthenticatedUser login(@Valid @RequestBody LoginRequest request, HttpSession session) {
		AuthenticatedUser user = authenticationService.authenticate(request.username(), request.password())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
				"Usuario o contraseña incorrectos"));
		session.setAttribute(USER_SESSION_KEY, user);
		session.setAttribute("userId", user.id());
		return user;
	}

	@GetMapping("/me")
	public AuthenticatedUser currentUser(HttpSession session) {
		Object user = session.getAttribute(USER_SESSION_KEY);
		if (user instanceof AuthenticatedUser authenticatedUser) {
			return authenticatedUser;
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No hay una sesión activa");
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpSession session) {
		session.invalidate();
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}
}
