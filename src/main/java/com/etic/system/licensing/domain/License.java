package com.etic.system.licensing.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record License(String id, String applicationId, String applicationCode, String applicationName,
	LicensingMode licensingMode, String deviceId, String deviceUuid, String deviceName,
	String userId, String username, LocalDate validFrom, LocalDate validUntil,
	LicenseStatus status, String deviceStatus, String operationalStatus,
	LocalDateTime createdAt, LocalDateTime updatedAt) {}
