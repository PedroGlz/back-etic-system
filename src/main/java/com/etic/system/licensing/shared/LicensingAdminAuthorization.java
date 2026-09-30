package com.etic.system.licensing.shared;

import com.etic.system.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LicensingAdminAuthorization {
	public AuthenticatedUser requireAdministrator(HttpSession session) {
		Object value = session.getAttribute("authenticatedUser");
		if (!(value instanceof AuthenticatedUser user)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere una sesión activa");
		}
		if (user.groupName() == null || !user.groupName().equalsIgnoreCase("Administradores")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requieren permisos de administrador");
		}
		return user;
	}
}
