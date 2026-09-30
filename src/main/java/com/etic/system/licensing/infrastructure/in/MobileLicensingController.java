package com.etic.system.licensing.infrastructure.in;

import com.etic.system.auth.domain.AuthenticatedUser;
import com.etic.system.licensing.domain.LicensedDevice;
import com.etic.system.licensing.security.DeviceSecurityService;
import com.etic.system.licensing.security.DeviceSecurityService.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile")
public class MobileLicensingController {
	private final DeviceSecurityService service;
	public MobileLicensingController(DeviceSecurityService service){this.service=service;}

	@PostMapping("/devices/register") @ResponseStatus(HttpStatus.CREATED)
	public LicensedDevice register(@Valid @RequestBody DeviceRegistrationRequest request,HttpServletRequest http){return service.registerAuto(request.identity(),http.getRemoteAddr());}
	@PostMapping("/devices/enroll")
	public LicensedDevice enroll(@Valid @RequestBody DeviceEnrollmentRequest request,HttpServletRequest http){return service.enroll(request.enrollmentCode(),request.identity(),http.getRemoteAddr());}
	@PostMapping("/devices/{deviceId}/challenge")
	public Challenge challenge(@PathVariable String deviceId,HttpServletRequest http){return service.createChallenge(deviceId,http.getRemoteAddr());}
	@PostMapping("/devices/{deviceId}/verify")
	public Verification verify(@PathVariable String deviceId,@Valid @RequestBody VerifyRequest request,HttpServletRequest http){return service.verifyChallenge(deviceId,request.challengeId(),request.signature(),http.getRemoteAddr());}
	@PostMapping("/licenses/validate")
	public OperationalLicense validate(@Valid @RequestBody LicenseValidationRequest request,HttpServletRequest http){return service.validateLicense(request.licenseId(),request.deviceId(),request.appVersion(),http.getRemoteAddr());}
	@PostMapping("/licenses/refresh")
	public OfflineCredential refresh(@Valid @RequestBody LicenseRefreshRequest request,HttpServletRequest http){return service.refreshCredential(request.licenseId(),request.deviceId(),request.challengeId(),request.appVersion(),http.getRemoteAddr());}
	@PostMapping("/licenses/session")
	public LicenseSession session(@Valid @RequestBody DeviceRegistrationRequest request,HttpServletRequest http,jakarta.servlet.http.HttpSession session){Object value=session.getAttribute("authenticatedUser");if(!(value instanceof AuthenticatedUser user))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"No hay una sesión activa");return service.openSession(request.identity(),user.id(),http.getRemoteAddr());}

	public record DeviceRegistrationRequest(@Size(max=150) String displayName,@NotBlank @Size(max=255) String packageName,@NotBlank String publicKey,@Size(max=255) String androidId,@Size(max=100) String manufacturer,@Size(max=100) String model,@Size(max=50) String androidVersion,@Size(max=80) String appVersion,String keySecurityLevel,boolean attestationAvailable){DeviceIdentity identity(){return new DeviceIdentity(displayName,packageName,publicKey,androidId,manufacturer,model,androidVersion,appVersion,keySecurityLevel,attestationAvailable);}}
	public record DeviceEnrollmentRequest(@NotBlank String enrollmentCode,@Size(max=150) String displayName,@NotBlank @Size(max=255) String packageName,@NotBlank String publicKey,@Size(max=255) String androidId,@Size(max=100) String manufacturer,@Size(max=100) String model,@Size(max=50) String androidVersion,@Size(max=80) String appVersion,String keySecurityLevel,boolean attestationAvailable){DeviceIdentity identity(){return new DeviceIdentity(displayName,packageName,publicKey,androidId,manufacturer,model,androidVersion,appVersion,keySecurityLevel,attestationAvailable);}}
	public record VerifyRequest(@NotBlank String challengeId,@NotBlank String signature){}
	public record LicenseValidationRequest(@NotBlank String licenseId,@NotBlank String deviceId,@Size(max=80) String appVersion){}
	public record LicenseRefreshRequest(@NotBlank String licenseId,@NotBlank String deviceId,@NotBlank String challengeId,@Size(max=80) String appVersion){}
}
