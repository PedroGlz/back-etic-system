package com.etic.system.inspecciones.application.service;

import com.etic.system.inspecciones.application.command.UpsertInspectionCommand;
import com.etic.system.inspecciones.application.port.out.InspectionPersistencePort;
import com.etic.system.inspecciones.domain.model.InspectionLocation;
import com.etic.system.inspecciones.domain.model.InspectionSession;
import com.etic.system.inspecciones.domain.model.InspectionStatusIds;
import com.etic.system.inspecciones.domain.model.InspectionSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InspectionServiceTest {

	@Test
	void shouldAllowCreateWithNullStartDate() {
		StubInspectionPersistencePort persistencePort = new StubInspectionPersistencePort();
		InspectionService service = new InspectionService(persistencePort);
		UpsertInspectionCommand command = new UpsertInspectionCommand(
			"client-1",
			"group-1",
			"site-1",
			null,
			"C",
			null,
			null
		);

		service.create(command, "user-1");

		assertNull(persistencePort.createdCommand.startDate());
		assertNull(persistencePort.createdCommand.endDate());
	}

	@Test
	void shouldAllowCreateWithValidStartDate() {
		StubInspectionPersistencePort persistencePort = new StubInspectionPersistencePort();
		InspectionService service = new InspectionService(persistencePort);
		LocalDateTime startDate = LocalDateTime.of(2026, 6, 28, 10, 15);
		UpsertInspectionCommand command = new UpsertInspectionCommand(
			"client-1",
			"group-1",
			"site-1",
			null,
			"C",
			startDate,
			null
		);

		service.create(command, "user-1");

		assertEquals(startDate, persistencePort.createdCommand.startDate());
		assertNull(persistencePort.createdCommand.endDate());
	}

	private static final class StubInspectionPersistencePort implements InspectionPersistencePort {

		private UpsertInspectionCommand createdCommand;

		@Override
		public List<InspectionSummary> findAll() {
			return List.of();
		}

		@Override
		public Optional<InspectionSummary> findById(String inspectionId) {
			return Optional.of(summary(inspectionId));
		}

		@Override
		public Optional<InspectionSession> findSessionById(String inspectionId) {
			return Optional.empty();
		}

		@Override
		public boolean hasOpenInspectionOnSite(String siteId, String excludedInspectionId) {
			return false;
		}

		@Override
		public Integer findMaxInspectionNumber() {
			return null;
		}

		@Override
		public List<InspectionLocation> findActiveLocationsBySite(String siteId) {
			return List.of(new InspectionLocation("location-1", "Activo"));
		}

		@Override
		public String createInspection(
			UpsertInspectionCommand command,
			String statusId,
			int inspectionNumber,
			Integer previousInspectionNumber,
			int daysCount,
			String photosRoute,
			String userId
		) {
			this.createdCommand = command;
			return "inspection-1";
		}

		@Override
		public void createInspectionDetails(String inspectionId, String siteId, List<InspectionLocation> locations, String userId) {
		}

		@Override
		public void updateInspection(String inspectionId, UpsertInspectionCommand command, String statusId, int daysCount, String userId) {
		}

		@Override
		public void updateInspectionStatus(String inspectionId, String statusId, LocalDateTime endDate, int daysCount, String userId) {
		}

		@Override
		public void deactivateInspection(String inspectionId, String userId) {
		}

		private InspectionSummary summary(String inspectionId) {
			return new InspectionSummary(
				inspectionId,
				1,
				null,
				"client-1",
				"Cliente",
				"group-1",
				"Grupo",
				"site-1",
				"Sitio",
				InspectionStatusIds.IN_PROGRESS,
				"En progreso",
				createdCommand.startDate(),
				createdCommand.endDate(),
				1,
				1,
				"C",
				"public/Archivos_ETIC/inspecciones/1",
				true,
				true,
				true,
				true,
				true,
				true
			);
		}
	}
}
