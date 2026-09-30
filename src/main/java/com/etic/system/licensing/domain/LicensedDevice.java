package com.etic.system.licensing.domain;

import java.time.LocalDateTime;

public record LicensedDevice(String id, String deviceUuid, String displayName, String manufacturer,
	String model, String androidVersion, String status, String origin, String androidId,
	String packageName, String appVersion, String publicKeyFingerprint, String publicKeyAlgorithm,
	String keySecurityLevel, boolean attestationAvailable, boolean attestationVerified, String notes,
	LocalDateTime registeredAt, LocalDateTime enrolledAt, LocalDateTime keyRotatedAt,
	LocalDateTime lastValidationAt, LocalDateTime updatedAt, String enrollmentStatus,
	LocalDateTime enrollmentExpiresAt) {}
