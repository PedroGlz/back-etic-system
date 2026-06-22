package com.etic.system.inspecciones.domain.model;

public record InspectionSession(
	String id,
	Integer inspectionNumber,
	String clientId,
	String clientName,
	String siteId,
	String siteName,
	String statusId,
	String statusName
) {
}
