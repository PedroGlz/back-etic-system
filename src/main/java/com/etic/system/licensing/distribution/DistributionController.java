package com.etic.system.licensing.distribution;

import com.etic.system.auth.domain.AuthenticatedUser;
import com.etic.system.licensing.shared.LicensingAdminAuthorization;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

// Fase 2B: sin endpoints administrativos; apk() permanece para PortalController.
@RequestMapping("/api/licensing")
public class DistributionController {
	private final DistributionService service;
	private final LicensingAdminAuthorization authorization;
	public DistributionController(DistributionService service, LicensingAdminAuthorization authorization) {
		this.service = service;
		this.authorization = authorization;
	}
	private AuthenticatedUser admin(HttpSession session) {
		service.activeUser(session);
		return authorization.requireAdministrator(session);
	}

	@GetMapping("/versions")
	public List<DistributionService.Version> versions(HttpSession session) {
		admin(session);
		return service.adminVersions();
	}

	@PostMapping(path = "/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DistributionService.Version upload(
		@RequestParam String applicationId, @RequestParam String versionName,
		@RequestParam(required = false) Long versionCode, @RequestParam(required = false) String minimumAndroid,
		@RequestParam(required = false) String releaseNotes,
		@RequestParam(defaultValue = "false") boolean mandatory,
		@RequestParam(defaultValue = "false") boolean published,
		@RequestPart("file") MultipartFile file, HttpSession session) {
		return service.upload(applicationId, versionName, versionCode, minimumAndroid,
			releaseNotes, mandatory, published, file, admin(session).id());
	}

	@PutMapping("/versions/{id}/publication")
	public DistributionService.Version publication(@PathVariable String id,
		@Valid @RequestBody PublicationRequest request, HttpSession session) {
		return service.publish(id, request.published(), admin(session).id());
	}

	@GetMapping("/versions/{id}/download")
	public ResponseEntity<FileSystemResource> adminDownload(@PathVariable String id, HttpSession session) {
		admin(session);
		return apk(service.adminDownload(id), id);
	}

	@GetMapping("/access")
	public List<DistributionService.Access> access(HttpSession session) {
		admin(session);
		return service.adminAccess();
	}

	@PostMapping("/access")
	@ResponseStatus(HttpStatus.CREATED)
	public DistributionService.Access createAccess(@Valid @RequestBody AccessRequest request, HttpSession session) {
		return saveAccess(null, request, session);
	}

	@PutMapping("/access/{id}")
	public DistributionService.Access updateAccess(@PathVariable String id,
		@Valid @RequestBody AccessRequest request, HttpSession session) {
		return saveAccess(id, request, session);
	}

	private DistributionService.Access saveAccess(String id, AccessRequest request, HttpSession session) {
		return service.saveAccess(id, request.userId(), request.applicationId(),
			request.status(), request.validFrom(), request.validUntil(), request.maxDevices(), admin(session).id());
	}

	static ResponseEntity<FileSystemResource> apk(Path path, String id) {
		try {
			return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("application-" + id + ".apk").build().toString())
				.contentLength(Files.size(path))
				.body(new FileSystemResource(path));
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "APK no disponible", e);
		}
	}
	public record PublicationRequest(@NotNull Boolean published) {}
	public record AccessRequest(@NotBlank String userId, @NotBlank String applicationId,
		@NotBlank String status, @NotNull LocalDate validFrom, LocalDate validUntil,
		@Min(1) int maxDevices) {}
}
