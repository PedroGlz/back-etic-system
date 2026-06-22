package com.etic.system.inspecciones.domain.model;

import java.time.LocalDateTime;

public record InspectionSummary(
	String id,
	Integer inspectionNumber,
	Integer previousInspectionNumber,
	String clientId,
	String clientName,
	String siteGroupId,
	String siteGroupName,
	String siteId,
	String siteName,
	String statusId,
	String statusName,
	LocalDateTime startDate,
	LocalDateTime endDate,
	Integer daysCount,
	Integer detailCount,
	String temperatureUnit,
	String photosRoute,
	boolean latestForSite,
	boolean editable,
	boolean deletable,
	boolean exportable,
	boolean uploadable,
	boolean openable
) {
}
