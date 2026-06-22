package com.etic.system.inspecciones.application.command;

import java.time.LocalDateTime;

public record UpsertInspectionCommand(
	String clientId,
	String siteGroupId,
	String siteId,
	String statusId,
	String temperatureUnit,
	LocalDateTime startDate,
	LocalDateTime endDate
) {
}
