package com.etic.system.auth.application;

import com.etic.system.auth.application.port.AuthenticationPort;
import com.etic.system.auth.domain.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthenticationService {

	private final AuthenticationPort authenticationPort;

	public AuthenticationService(AuthenticationPort authenticationPort) {
		this.authenticationPort = authenticationPort;
	}

	public Optional<AuthenticatedUser> authenticate(String username, String password) {
		if (username == null || username.isBlank() || password == null || password.isBlank()) {
			return Optional.empty();
		}
		return authenticationPort.authenticate(username.trim(), password);
	}
}
