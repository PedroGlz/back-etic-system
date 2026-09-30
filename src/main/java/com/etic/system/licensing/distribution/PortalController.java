package com.etic.system.licensing.distribution;

import com.etic.system.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/portal")
public class PortalController {
	private final DistributionService service;
	public PortalController(DistributionService service) { this.service = service; }

	@GetMapping("/apps")
	public List<DistributionService.PortalApplication> apps(HttpSession session) {
		return service.portalApps(user(session));
	}

	@GetMapping("/apps/{applicationId}")
	public DistributionService.PortalApplication app(@PathVariable String applicationId, HttpSession session) {
		return service.portalApp(user(session), applicationId);
	}

	@GetMapping("/apps/{applicationId}/versions")
	public List<DistributionService.Version> versions(@PathVariable String applicationId, HttpSession session) {
		return service.portalVersions(user(session), applicationId);
	}

	@GetMapping("/apps/{applicationId}/versions/latest")
	public DistributionService.Version latest(@PathVariable String applicationId, HttpSession session) {
		AuthenticatedUser user = user(session);
		service.portalApp(user, applicationId);
		DistributionService.Version version = service.latestVersion(applicationId);
		if (version == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay versión publicada");
		return version;
	}

	@GetMapping("/downloads/{versionId}")
	public ResponseEntity<FileSystemResource> download(@PathVariable String versionId, HttpSession session) {
		return DistributionController.apk(service.portalDownload(user(session), versionId), versionId);
	}

	private AuthenticatedUser user(HttpSession session) { return service.activeUser(session); }
}
