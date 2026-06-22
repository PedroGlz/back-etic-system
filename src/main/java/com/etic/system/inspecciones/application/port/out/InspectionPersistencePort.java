package com.etic.system.inspecciones.application.port.out;

import com.etic.system.inspecciones.application.command.UpsertInspectionCommand;
import com.etic.system.inspecciones.domain.model.InspectionLocation;
import com.etic.system.inspecciones.domain.model.InspectionSession;
import com.etic.system.inspecciones.domain.model.InspectionSummary;

import java.util.List;
import java.util.Optional;

public interface InspectionPersistencePort {

	List<InspectionSummary> findAll();

	Optional<InspectionSummary> findById(String inspectionId);

	Optional<InspectionSession> findSessionById(String inspectionId);

	boolean hasOpenInspectionOnSite(String siteId, String excludedInspectionId);

	Integer findMaxInspectionNumber();

	List<InspectionLocation> findActiveLocationsBySite(String siteId);

	String createInspection(
		UpsertInspectionCommand command,
		String statusId,
		int inspectionNumber,
		Integer previousInspectionNumber,
		int daysCount,
		String photosRoute,
		String userId
	);

	void createInspectionDetails(String inspectionId, String siteId, List<InspectionLocation> locations, String userId);

	void updateInspection(
		String inspectionId,
		UpsertInspectionCommand command,
		String statusId,
		int daysCount,
		String userId
	);

	void updateInspectionStatus(String inspectionId, String statusId, java.time.LocalDateTime endDate, int daysCount, String userId);

	void deactivateInspection(String inspectionId, String userId);
}
