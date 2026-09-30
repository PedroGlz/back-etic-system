package com.etic.system.licensing.application;

import com.etic.system.licensing.application.port.LicensingPersistencePort;
import com.etic.system.licensing.domain.*;
import com.etic.system.shared.domain.exception.BusinessValidationException;
import com.etic.system.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LicensingService {
	private final LicensingPersistencePort persistence;
	public LicensingService(LicensingPersistencePort persistence) { this.persistence = persistence; }

	public List<LicensedApplication> applications() { return persistence.findApplications(); }
	public List<LicensedDevice> devices() { return persistence.findDevices(); }
	public LicensedDevice device(String id) { return persistence.findDevice(id).orElseThrow(() -> notFound("Dispositivo")); }
	@Transactional("licensingTransactionManager")
	public List<License> licenses() { persistence.expireElapsedLicenses(); return persistence.findLicenses().stream().map(this::withEffectiveStatus).toList(); }

	@Transactional("licensingTransactionManager")
	public LicensedApplication createApplication(String name, String packageName, LicensingMode mode, String status, String actorId) {
		validateApplication(name, packageName, mode);
		String normalizedStatus = normalizeRecordStatus(status);
		if (persistence.packageNameExists(packageName.trim(), null)) throw new BusinessValidationException("Ya existe una aplicación con ese package name");
		String code = nextAvailableApplicationCode(generateApplicationCode(name));
		return persistence.createApplication(uuid(), code, name.trim(), packageName.trim(), mode, normalizedStatus, actorId);
	}

	@Transactional("licensingTransactionManager")
	public LicensedApplication updateApplication(String id, String name, String packageName, LicensingMode mode, String status, String actorId) {
		validateApplication(name, packageName, mode);
		persistence.findApplication(id).orElseThrow(() -> notFound("Aplicación"));
		if (persistence.packageNameExists(packageName.trim(), id)) throw new BusinessValidationException("Ya existe una aplicación con ese package name");
		return persistence.updateApplication(id, name.trim(), packageName.trim(), mode, normalizeRecordStatus(status), actorId);
	}

	@Transactional("licensingTransactionManager")
	public LicensedDevice updateDevice(String id, String displayName, String manufacturer, String model, String androidVersion, String notes, String actorId) {
		device(id);
		return persistence.updateDevice(id, trim(displayName), trim(manufacturer), trim(model), trim(androidVersion), trim(notes), actorId);
	}

	@Transactional("licensingTransactionManager")
	public License saveLicense(String id, String applicationId, String deviceId, String userId, LocalDate from, LocalDate until, LicenseStatus status, String actorId) {
		LicensedApplication application = persistence.findApplication(applicationId).orElseThrow(() -> notFound("Aplicación"));
		if (persistence.findDevice(deviceId).isEmpty()) throw notFound("Dispositivo");
		if (from == null || until == null || until.isBefore(from)) throw new BusinessValidationException("La vigencia de la licencia no es válida");
		String normalizedUser = trim(userId);
		if (application.licensingMode() == LicensingMode.USER_DEVICE) {
			if (normalizedUser == null) throw new BusinessValidationException("El usuario es obligatorio para USER_DEVICE");
			if (!persistence.activeUserExists(normalizedUser)) throw new BusinessValidationException("El usuario no existe o está inactivo");
		} else if (normalizedUser != null) {
			throw new BusinessValidationException("DEVICE_ONLY no admite usuario");
		}
		LicenseStatus requested = status == null ? LicenseStatus.ACTIVE : status;
		if (until.isBefore(LocalDate.now())) requested = LicenseStatus.EXPIRED;
		persistence.expireElapsedLicenses();
		if (requested == LicenseStatus.ACTIVE && persistence.activeEquivalentExists(applicationId, deviceId, normalizedUser, id)) {
			throw new BusinessValidationException("Ya existe una licencia activa equivalente");
		}
		return withEffectiveStatus(persistence.saveLicense(id == null ? uuid() : id, applicationId, deviceId, normalizedUser, from, until, requested, actorId));
	}

	@Transactional("licensingTransactionManager")
	public License changeStatus(String id, LicenseStatus status, String actorId) {
		License license = persistence.findLicense(id).orElseThrow(() -> notFound("Licencia"));
		persistence.expireElapsedLicenses();
		if (status == LicenseStatus.ACTIVE) {
			if (license.validUntil().isBefore(LocalDate.now())) throw new BusinessValidationException("No se puede activar una licencia vencida");
			if (persistence.activeEquivalentExists(license.applicationId(), license.deviceId(), license.userId(), id)) throw new BusinessValidationException("Ya existe una licencia activa equivalente");
		}
		persistence.updateLicenseStatus(id, status, actorId);
		return withEffectiveStatus(persistence.findLicense(id).orElseThrow(() -> notFound("Licencia")));
	}

	private License withEffectiveStatus(License value) {
		if (value.status() == LicenseStatus.ACTIVE && value.validUntil().isBefore(LocalDate.now())) {
			return new License(value.id(), value.applicationId(), value.applicationCode(), value.applicationName(), value.licensingMode(), value.deviceId(), value.deviceUuid(), value.deviceName(), value.userId(), value.username(), value.validFrom(), value.validUntil(), LicenseStatus.EXPIRED, value.deviceStatus(), "EXPIRED", value.createdAt(), value.updatedAt());
		}
		return value;
	}
	private String normalizeRecordStatus(String value) { String status = value == null ? "ACTIVE" : value.trim().toUpperCase(); if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) throw new BusinessValidationException("Estado no permitido"); return status; }
	private void validateApplication(String name, String packageName, LicensingMode mode) { required(name, "El nombre es obligatorio"); required(packageName, "El package name es obligatorio"); if (mode == null) throw new BusinessValidationException("La modalidad es obligatoria"); }
	private String generateApplicationCode(String name) {
		required(name, "El nombre es obligatorio");
		String code = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "")
			.toUpperCase(Locale.ROOT)
			.replaceAll("[^A-Z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
		if (code.isEmpty()) throw new BusinessValidationException("El nombre no permite generar un código técnico válido");
		return code.length() <= 80 ? code : code.substring(0, 80).replaceAll("_+$", "");
	}
	private String nextAvailableApplicationCode(String base) {
		if (!persistence.applicationCodeExists(base, null)) return base;
		for (int suffix = 2; ; suffix++) {
			String ending = "_" + suffix;
			String prefix = base.substring(0, Math.min(base.length(), 80 - ending.length())).replaceAll("_+$", "");
			String candidate = prefix + ending;
			if (!persistence.applicationCodeExists(candidate, null)) return candidate;
		}
	}
	private void required(String value, String message) { if (value == null || value.isBlank()) throw new BusinessValidationException(message); }
	private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private String uuid() { return UUID.randomUUID().toString().toUpperCase(); }
	private ResourceNotFoundException notFound(String entity) { return new ResourceNotFoundException(entity + " no encontrado"); }
}
