package com.etic.system.licensing.infrastructure.in;

import com.etic.system.auth.domain.AuthenticatedUser;
import com.etic.system.licensing.domain.LicensedDevice;
import com.etic.system.licensing.security.DeviceSecurityService;
import com.etic.system.licensing.security.DeviceSecurityService.*;
import com.etic.system.licensing.shared.LicensingAdminAuthorization;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// Fase 2B: administración exclusiva de License Control; no registrar endpoints.
@RequestMapping("/api/licensing/devices")
public class LicensingDeviceAdminController {
	private final DeviceSecurityService service;
	private final LicensingAdminAuthorization authorization;
	public LicensingDeviceAdminController(DeviceSecurityService service,LicensingAdminAuthorization authorization){this.service=service;this.authorization=authorization;}

	@PostMapping @ResponseStatus(HttpStatus.CREATED)
	public ManualDeviceCreated create(@Valid @RequestBody ManualDeviceRequest request,HttpSession session){return service.createManual(request.displayName(),request.manufacturer(),request.model(),request.androidVersion(),request.initialStatus(),request.notes(),admin(session).id());}
	@PostMapping("/{id}/activate") public LicensedDevice activate(@PathVariable String id,HttpSession session){return service.changeStatus(id,"ACTIVE",admin(session).id());}
	@PostMapping("/{id}/suspend") public LicensedDevice suspend(@PathVariable String id,HttpSession session){return service.changeStatus(id,"SUSPENDED",admin(session).id());}
	@PostMapping("/{id}/revoke") public LicensedDevice revoke(@PathVariable String id,HttpSession session){return service.changeStatus(id,"REVOKED",admin(session).id());}
	@PostMapping("/{id}/enrollment-code") public EnrollmentCode enrollmentCode(@PathVariable String id,HttpSession session){return service.regenerateEnrollmentCode(id,admin(session).id());}
	private AuthenticatedUser admin(HttpSession session){return authorization.requireAdministrator(session);}
	public record ManualDeviceRequest(@NotBlank @Size(max=150) String displayName,@Size(max=100) String manufacturer,@Size(max=100) String model,@Size(max=50) String androidVersion,String initialStatus,@Size(max=2000) String notes){}
}
