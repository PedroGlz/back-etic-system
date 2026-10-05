package com.etic.system.licensing.infrastructure.in;

import com.etic.system.auth.domain.AuthenticatedUser;
import com.etic.system.licensing.application.LicensingService;
import com.etic.system.licensing.domain.*;
import com.etic.system.licensing.shared.LicensingAdminAuthorization;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

// Fase 2B: administración exclusiva de License Control; no registrar endpoints.
@RequestMapping("/api/licensing")
public class LicensingController {
	private final LicensingService service;
	private final LicensingAdminAuthorization authorization;
	public LicensingController(LicensingService service, LicensingAdminAuthorization authorization) { this.service=service; this.authorization=authorization; }

	@GetMapping("/applications") public List<LicensedApplication> applications(HttpSession s){admin(s);return service.applications();}
	@PostMapping("/applications") @ResponseStatus(HttpStatus.CREATED) public LicensedApplication createApplication(@Valid @RequestBody ApplicationRequest r,HttpSession s){return service.createApplication(r.name(),r.packageName(),r.licensingMode(),r.status(),admin(s).id());}
	@PutMapping("/applications/{id}") public LicensedApplication updateApplication(@PathVariable String id,@Valid @RequestBody ApplicationRequest r,HttpSession s){return service.updateApplication(id,r.name(),r.packageName(),r.licensingMode(),r.status(),admin(s).id());}

	@GetMapping("/devices") public List<LicensedDevice> devices(HttpSession s){admin(s);return service.devices();}
	@GetMapping("/devices/{id}") public LicensedDevice device(@PathVariable String id,HttpSession s){admin(s);return service.device(id);}
	@PutMapping("/devices/{id}") public LicensedDevice updateDevice(@PathVariable String id,@Valid @RequestBody DeviceRequest r,HttpSession s){return service.updateDevice(id,r.displayName(),r.manufacturer(),r.model(),r.androidVersion(),r.notes(),admin(s).id());}

	@GetMapping("/licenses") public List<License> licenses(HttpSession s){admin(s);return service.licenses();}
	@PostMapping("/licenses") @ResponseStatus(HttpStatus.CREATED) public License createLicense(@Valid @RequestBody LicenseRequest r,HttpSession s){return service.saveLicense(null,r.applicationId(),r.deviceId(),r.userId(),r.validFrom(),r.validUntil(),r.status(),admin(s).id());}
	@PutMapping("/licenses/{id}") public License updateLicense(@PathVariable String id,@Valid @RequestBody LicenseRequest r,HttpSession s){return service.saveLicense(id,r.applicationId(),r.deviceId(),r.userId(),r.validFrom(),r.validUntil(),r.status(),admin(s).id());}
	@PostMapping("/licenses/{id}/activate") public License activate(@PathVariable String id,HttpSession s){return service.changeStatus(id,LicenseStatus.ACTIVE,admin(s).id());}
	@PostMapping("/licenses/{id}/suspend") public License suspend(@PathVariable String id,HttpSession s){return service.changeStatus(id,LicenseStatus.SUSPENDED,admin(s).id());}
	@PostMapping("/licenses/{id}/revoke") public License revoke(@PathVariable String id,HttpSession s){return service.changeStatus(id,LicenseStatus.REVOKED,admin(s).id());}
	private AuthenticatedUser admin(HttpSession session){return authorization.requireAdministrator(session);}

	public record ApplicationRequest(@NotBlank @Size(max=150) String name,@NotBlank @Size(max=255) String packageName,@NotNull LicensingMode licensingMode,String status){}
	public record DeviceRequest(@Size(max=150) String displayName,@Size(max=100) String manufacturer,@Size(max=100) String model,@Size(max=50) String androidVersion,@Size(max=2000) String notes){}
	public record LicenseRequest(@NotBlank String applicationId,@NotBlank String deviceId,String userId,@NotNull LocalDate validFrom,@NotNull LocalDate validUntil,LicenseStatus status){}
}
