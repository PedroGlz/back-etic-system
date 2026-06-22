package com.etic.system.auth.application.port;

import com.etic.system.auth.domain.AuthenticatedUser;

import java.util.Optional;

public interface AuthenticationPort {
	Optional<AuthenticatedUser> authenticate(String username, String password);
}
