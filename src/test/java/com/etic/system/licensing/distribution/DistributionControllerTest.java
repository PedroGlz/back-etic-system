package com.etic.system.licensing.distribution;

import com.etic.system.auth.domain.AuthenticatedUser;
import com.etic.system.licensing.shared.LicensingAdminAuthorization;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DistributionControllerTest {
	@TempDir Path directory;
	@Test void onlyAdministratorCanManageVersionsAndAccess() {
		DistributionService service = mock(DistributionService.class);
		var controller = new DistributionController(service, new LicensingAdminAuthorization());
		MockHttpSession session = new MockHttpSession();
		var ordinary = new AuthenticatedUser("U1", "user", "User", null, null, "Usuarios", null, null);
		session.setAttribute("authenticatedUser", ordinary);
		when(service.activeUser(session)).thenReturn(ordinary);
		assertThrows(ResponseStatusException.class, () -> controller.versions(session));
		verify(service, never()).adminVersions();
		var admin = new AuthenticatedUser("A1", "admin", "Admin", null, null, "Administradores", null, null);
		session.setAttribute("authenticatedUser", admin);
		when(service.activeUser(session)).thenReturn(admin);
		when(service.adminVersions()).thenReturn(List.of());
		assertEquals(List.of(), controller.versions(session));
		verify(service).adminVersions();
	}

	@Test void validDownloadHasApkHeadersAndLength() throws Exception {
		Path file = directory.resolve("application.apk");
		Files.write(file, new byte[] {1, 2, 3});
		var response = DistributionController.apk(file, "V1");
		assertEquals(MediaType.parseMediaType("application/vnd.android.package-archive"), response.getHeaders().getContentType());
		assertEquals(3, response.getHeaders().getContentLength());
		assertTrue(response.getHeaders().getContentDisposition().toString().contains("application-V1.apk"));
	}
}
