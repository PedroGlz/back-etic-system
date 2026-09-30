package com.etic.system.licensing.shared;

import com.etic.system.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LicensingAdminAuthorizationTest {
	private final LicensingAdminAuthorization authorization=new LicensingAdminAuthorization();
	@Test void rejectsMissingSessionUser(){HttpSession session=mock(HttpSession.class);assertEquals(401,assertThrows(ResponseStatusException.class,()->authorization.requireAdministrator(session)).getStatusCode().value());}
	@Test void rejectsNonAdministrator(){HttpSession session=mock(HttpSession.class);when(session.getAttribute("authenticatedUser")).thenReturn(user("Operadores"));assertEquals(403,assertThrows(ResponseStatusException.class,()->authorization.requireAdministrator(session)).getStatusCode().value());}
	@Test void acceptsAdministrator(){HttpSession session=mock(HttpSession.class);AuthenticatedUser user=user("Administradores");when(session.getAttribute("authenticatedUser")).thenReturn(user);assertSame(user,authorization.requireAdministrator(session));}
	private AuthenticatedUser user(String group){return new AuthenticatedUser("U1","admin","Admin",null,"G1",group,null,null);}
}
