package com.etic.system.licensing.application.port;

import com.etic.system.licensing.domain.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LicensingPersistencePort {
	List<LicensedApplication> findApplications();
	Optional<LicensedApplication> findApplication(String id);
	LicensedApplication createApplication(String id, String code, String name, String packageName, LicensingMode mode, String status, String actorId);
	LicensedApplication updateApplication(String id, String name, String packageName, LicensingMode mode, String status, String actorId);
	boolean applicationCodeExists(String code, String excludedId);
	boolean packageNameExists(String packageName, String excludedId);
	List<LicensedDevice> findDevices();
	Optional<LicensedDevice> findDevice(String id);
	LicensedDevice updateDevice(String id, String displayName, String manufacturer, String model, String androidVersion, String notes, String actorId);
	List<License> findLicenses();
	Optional<License> findLicense(String id);
	License saveLicense(String id, String applicationId, String deviceId, String userId, LocalDate validFrom, LocalDate validUntil, LicenseStatus status, String actorId);
	void updateLicenseStatus(String id, LicenseStatus status, String actorId);
	void expireElapsedLicenses();
	boolean activeEquivalentExists(String applicationId, String deviceId, String userId, String excludedId);
	boolean activeUserExists(String userId);
}
