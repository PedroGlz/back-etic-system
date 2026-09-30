package com.etic.system.licensing.domain;

import java.time.LocalDateTime;

public record LicensedApplication(String id, String code, String name, String packageName,
	LicensingMode licensingMode, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {}
