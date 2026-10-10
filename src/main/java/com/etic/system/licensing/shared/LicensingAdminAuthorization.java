package com.etic.system.licensing.shared;

import com.etic.system.auth.domain.AuthenticatedUser;
import com.etic.system.auth.security.WebIdentity;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LicensingAdminAuthorization {
	public AuthenticatedUser requireAdministrator(HttpSession session) {
		WebIdentity identity = WebIdentity.current();
		if (identity == null || identity.id() == null || identity.id().isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere una sesión activa");
		}
		return new AuthenticatedUser(identity.id(),identity.username(),identity.firstName(),identity.email(),null,null,null,null);
	}
}
