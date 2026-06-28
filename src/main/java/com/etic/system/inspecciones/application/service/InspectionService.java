package com.etic.system.inspecciones.application.service;

import com.etic.system.inspecciones.application.command.UpdateInspectionStatusCommand;
import com.etic.system.inspecciones.application.command.UpsertInspectionCommand;
import com.etic.system.inspecciones.application.port.out.InspectionPersistencePort;
import com.etic.system.inspecciones.domain.model.InspectionLocation;
import com.etic.system.inspecciones.domain.model.InspectionSession;
import com.etic.system.inspecciones.domain.model.InspectionStatusIds;
import com.etic.system.inspecciones.domain.model.InspectionSummary;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InspectionService {

	private static final String SESSION_KEY = "selectedInspection";

	private final InspectionPersistencePort persistencePort;

	public InspectionService(InspectionPersistencePort persistencePort) {
		this.persistencePort = persistencePort;
	}

	public List<InspectionSummary> findAll() {
		return persistencePort.findAll();
	}

	public InspectionSummary findById(String inspectionId) {
		return persistencePort.findById(inspectionId)
			.orElseThrow(() -> new ResourceNotFoundException("No se encontró la inspección solicitada"));
	}

	public InspectionSession currentSelection(HttpSession session) {
		Object selected = session.getAttribute(SESSION_KEY);
		if (selected instanceof InspectionSession inspectionSession) {
			return inspectionSession;
		}
		throw new ResourceNotFoundException("No hay una inspección abierta en la sesión actual");
	}

	public void clearSelection(HttpSession session) {
		session.removeAttribute(SESSION_KEY);
		session.removeAttribute("inspeccion");
		session.removeAttribute("nombreSitio");
		session.removeAttribute("Id_Inspeccion");
		session.removeAttribute("Id_Status_Inspeccion");
		session.removeAttribute("Id_Sitio");
	}

	@Transactional
	public InspectionSummary create(UpsertInspectionCommand command, String userId) {
		validateRequiredFields(command, false);

		String statusId = normalizeStatus(command.statusId());
		if (InspectionStatusIds.IN_PROGRESS.equals(statusId)
			&& persistencePort.hasOpenInspectionOnSite(command.siteId(), null)) {
			throw new BusinessValidationException("Ya existe una inspección en progreso para el sitio seleccionado");
		}

		List<InspectionLocation> locations = persistencePort.findActiveLocationsBySite(command.siteId());
		if (locations.isEmpty()) {
			throw new BusinessValidationException("El sitio seleccionado no tiene ubicaciones activas para generar el detalle");
		}

		Integer maxInspectionNumber = persistencePort.findMaxInspectionNumber();
		int nextInspectionNumber = maxInspectionNumber == null ? 1 : maxInspectionNumber + 1;
		int daysCount = calculateDays(command.startDate(), command.endDate());
		String photosRoute = "public/Archivos_ETIC/inspecciones/" + nextInspectionNumber;

		String inspectionId = persistencePort.createInspection(
			command,
			statusId,
			nextInspectionNumber,
			maxInspectionNumber,
			daysCount,
			photosRoute,
			userId
		);
		persistencePort.createInspectionDetails(inspectionId, command.siteId(), locations, userId);

		return findById(inspectionId);
	}

	@Transactional
	public InspectionSummary update(String inspectionId, UpsertInspectionCommand command, String userId) {
		InspectionSummary current = findById(inspectionId);
		validateRequiredFields(command, true);

		if (!current.latestForSite() || InspectionStatusIds.CLOSED.equals(current.statusId())) {
			throw new BusinessValidationException("Solo la inspección más reciente y no cerrada puede editarse");
		}

		if (!current.siteId().equals(command.siteId())) {
			throw new BusinessValidationException("El sitio no puede cambiarse después de crear la inspección");
		}

		String statusId = normalizeStatus(command.statusId());
		if (InspectionStatusIds.IN_PROGRESS.equals(statusId)
			&& persistencePort.hasOpenInspectionOnSite(command.siteId(), inspectionId)) {
			throw new BusinessValidationException("Ya existe otra inspección en progreso para el sitio seleccionado");
		}

		int daysCount = calculateDays(command.startDate(), command.endDate());
		persistencePort.updateInspection(inspectionId, command, statusId, daysCount, userId);
		return findById(inspectionId);
	}

	@Transactional
	public void deactivate(String inspectionId, String userId, HttpSession session) {
		InspectionSummary current = findById(inspectionId);
		if (!current.deletable()) {
			throw new BusinessValidationException("Solo la inspección más reciente y no cerrada puede desactivarse");
		}
		persistencePort.deactivateInspection(inspectionId, userId);

		Object selected = session.getAttribute(SESSION_KEY);
		if (selected instanceof InspectionSession inspectionSession && inspectionSession.id().equals(inspectionId)) {
			clearSelection(session);
		}
	}

	@Transactional
	public InspectionSummary updateStatus(
		String inspectionId,
		UpdateInspectionStatusCommand command,
		String userId,
		HttpSession session
	) {
		InspectionSummary current = findById(inspectionId);
		if (!current.latestForSite()) {
			throw new BusinessValidationException("Solo la inspección más reciente del sitio puede cambiar de estatus");
		}

		String statusId = normalizeStatus(command.statusId());
		LocalDateTime endDate = command.endDate();
		if (InspectionStatusIds.CLOSED.equals(statusId) && endDate == null) {
			endDate = LocalDateTime.now();
		}
		if (!InspectionStatusIds.CLOSED.equals(statusId)) {
			endDate = null;
		}

		int daysCount = calculateDays(current.startDate(), endDate);
		persistencePort.updateInspectionStatus(inspectionId, statusId, endDate, daysCount, userId);

		InspectionSummary updated = findById(inspectionId);
		Object selected = session.getAttribute(SESSION_KEY);
		if (selected instanceof InspectionSession inspectionSession && inspectionSession.id().equals(inspectionId)) {
			session.setAttribute(SESSION_KEY, toSession(updated));
			session.setAttribute("Id_Status_Inspeccion", updated.statusId());
		}
		return updated;
	}

	public InspectionSession open(String inspectionId, HttpSession session) {
		InspectionSession inspectionSession = persistencePort.findSessionById(inspectionId)
			.orElseThrow(() -> new ResourceNotFoundException("No se encontró la inspección solicitada"));
		session.setAttribute(SESSION_KEY, inspectionSession);
		session.setAttribute("inspeccion", inspectionSession.inspectionNumber());
		session.setAttribute("nombreSitio", inspectionSession.siteName());
		session.setAttribute("Id_Inspeccion", inspectionSession.id());
		session.setAttribute("Id_Status_Inspeccion", inspectionSession.statusId());
		session.setAttribute("Id_Sitio", inspectionSession.siteId());
		return inspectionSession;
	}

	private void validateRequiredFields(UpsertInspectionCommand command, boolean update) {
		if (isBlank(command.clientId())) {
			throw new BusinessValidationException("El cliente es obligatorio");
		}
		if (isBlank(command.siteGroupId())) {
			throw new BusinessValidationException("El grupo de sitios es obligatorio");
		}
		if (isBlank(command.siteId())) {
			throw new BusinessValidationException("El sitio es obligatorio");
		}
		if (command.startDate() != null && command.endDate() != null && command.endDate().isBefore(command.startDate())) {
			throw new BusinessValidationException("La fecha final no puede ser anterior a la fecha inicial");
		}
		if (!isBlank(command.temperatureUnit()) && !"C".equalsIgnoreCase(command.temperatureUnit()) && !"F".equalsIgnoreCase(command.temperatureUnit())) {
			throw new BusinessValidationException("La unidad de temperatura debe ser C o F");
		}
		if (!update && isBlank(command.statusId())) {
			return;
		}
		if (InspectionStatusIds.CLOSED.equals(normalizeStatus(command.statusId())) && command.endDate() == null) {
			throw new BusinessValidationException("La fecha final es obligatoria cuando la inspección se guarda como cerrada");
		}
	}

	private String normalizeStatus(String statusId) {
		return isBlank(statusId) ? InspectionStatusIds.IN_PROGRESS : statusId;
	}

	private int calculateDays(LocalDateTime startDate, LocalDateTime endDate) {
		if (startDate == null || endDate == null) {
			return 1;
		}
		return (int) Math.abs(Duration.between(startDate, endDate).toDays());
	}

	private InspectionSession toSession(InspectionSummary summary) {
		return new InspectionSession(
			summary.id(),
			summary.inspectionNumber(),
			summary.clientId(),
			summary.clientName(),
			summary.siteId(),
			summary.siteName(),
			summary.statusId(),
			summary.statusName()
		);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
