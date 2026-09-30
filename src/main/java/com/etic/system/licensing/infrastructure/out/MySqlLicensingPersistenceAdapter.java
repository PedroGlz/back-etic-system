package com.etic.system.licensing.infrastructure.out;

import com.etic.system.licensing.application.port.LicensingPersistencePort;
import com.etic.system.licensing.domain.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class MySqlLicensingPersistenceAdapter implements LicensingPersistencePort {
	private final NamedParameterJdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate eticJdbc;
	public MySqlLicensingPersistenceAdapter(
		@Qualifier("licensingJdbc") NamedParameterJdbcTemplate jdbc,
		@Qualifier("namedParameterJdbcTemplate") NamedParameterJdbcTemplate eticJdbc
	) { this.jdbc = jdbc; this.eticJdbc = eticJdbc; }

	public List<LicensedApplication> findApplications() { return jdbc.query("SELECT * FROM licensed_applications ORDER BY Name", Map.of(), this::application); }
	public Optional<LicensedApplication> findApplication(String id) { return jdbc.query("SELECT * FROM licensed_applications WHERE Id_Application=:id", Map.of("id", id), this::application).stream().findFirst(); }
	public LicensedApplication createApplication(String id, String code, String name, String packageName, LicensingMode mode, String status, String actorId) {
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", id).addValue("code", code).addValue("name", name).addValue("packageName", packageName).addValue("mode", mode.name()).addValue("status", status).addValue("actor", actorId).addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("INSERT INTO licensed_applications(Id_Application,Code,Name,Package_Name,Licensing_Mode,Status,Created_At,Created_By) VALUES(:id,:code,:name,:packageName,:mode,:status,:now,:actor)", p);
		return findApplication(id).orElseThrow();
	}
	public LicensedApplication updateApplication(String id, String name, String packageName, LicensingMode mode, String status, String actorId) {
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", id).addValue("name", name).addValue("packageName", packageName).addValue("mode", mode.name()).addValue("status", status).addValue("actor", actorId).addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("UPDATE licensed_applications SET Name=:name,Package_Name=:packageName,Licensing_Mode=:mode,Status=:status,Updated_At=:now,Updated_By=:actor WHERE Id_Application=:id", p);
		return findApplication(id).orElseThrow();
	}
	public boolean applicationCodeExists(String value, String excludedId) { return exists("licensed_applications", "Code", value, "Id_Application", excludedId); }
	public boolean packageNameExists(String value, String excludedId) { return exists("licensed_applications", "Package_Name", value, "Id_Application", excludedId); }

	public List<LicensedDevice> findDevices() { return jdbc.query(deviceSelect() + " ORDER BY d.Registered_At DESC", Map.of(), this::device); }
	public Optional<LicensedDevice> findDevice(String id) { return jdbc.query(deviceSelect() + " WHERE d.Id_Device=:id", Map.of("id", id), this::device).stream().findFirst(); }
	public LicensedDevice updateDevice(String id, String displayName, String manufacturer, String model, String androidVersion, String notes, String actorId) {
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", id).addValue("name", displayName).addValue("manufacturer", manufacturer).addValue("model", model).addValue("android", androidVersion).addValue("notes", notes).addValue("actor", actorId).addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("UPDATE licensed_devices SET Display_Name=:name,Manufacturer=:manufacturer,Model=:model,Android_Version=:android,Notes=:notes,Updated_At=:now,Updated_By=:actor WHERE Id_Device=:id", p);
		return findDevice(id).orElseThrow();
	}

	public List<License> findLicenses() { return jdbc.query(licenseSelect() + " ORDER BY l.Created_At DESC", Map.of(), this::license); }
	public Optional<License> findLicense(String id) { return jdbc.query(licenseSelect() + " WHERE l.Id_License=:id", Map.of("id", id), this::license).stream().findFirst(); }
	public License saveLicense(String id, String applicationId, String deviceId, String userId, LocalDate from, LocalDate until, LicenseStatus status, String actorId) {
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", id).addValue("applicationId", applicationId).addValue("deviceId", deviceId).addValue("userId", userId).addValue("from", java.sql.Date.valueOf(from)).addValue("until", java.sql.Date.valueOf(until)).addValue("status", status.name()).addValue("actor", actorId).addValue("now", Timestamp.valueOf(LocalDateTime.now()));
		if (findLicense(id).isPresent()) jdbc.update("UPDATE licenses SET Id_Application=:applicationId,Id_Device=:deviceId,Id_Usuario=:userId,Valid_From=:from,Valid_Until=:until,Status=:status,Updated_At=:now,Updated_By=:actor WHERE Id_License=:id", p);
		else jdbc.update("INSERT INTO licenses(Id_License,Id_Application,Id_Device,Id_Usuario,Valid_From,Valid_Until,Status,Created_At,Created_By) VALUES(:id,:applicationId,:deviceId,:userId,:from,:until,:status,:now,:actor)", p);
		return findLicense(id).orElseThrow();
	}
	public void updateLicenseStatus(String id, LicenseStatus status, String actorId) { jdbc.update("UPDATE licenses SET Status=:status,Updated_At=:now,Updated_By=:actor WHERE Id_License=:id", new MapSqlParameterSource().addValue("id", id).addValue("status", status.name()).addValue("actor", actorId).addValue("now", Timestamp.valueOf(LocalDateTime.now()))); }
	public void expireElapsedLicenses() { jdbc.update("UPDATE licenses SET Status='EXPIRED',Updated_At=:now WHERE Status='ACTIVE' AND Valid_Until<CURRENT_DATE", Map.of("now", Timestamp.valueOf(LocalDateTime.now()))); }
	public boolean activeEquivalentExists(String applicationId, String deviceId, String userId, String excludedId) {
		String sql = "SELECT COUNT(*) FROM licenses WHERE Id_Application=:app AND Id_Device=:device AND ((Id_Usuario IS NULL AND :userId IS NULL) OR Id_Usuario=:userId) AND Status='ACTIVE'" + (excludedId == null ? "" : " AND Id_License<>:excluded");
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("app", applicationId).addValue("device", deviceId).addValue("userId", userId); if (excludedId != null) p.addValue("excluded", excludedId);
		Integer count = jdbc.queryForObject(sql, p, Integer.class); return count != null && count > 0;
	}
	public boolean activeUserExists(String userId) { Integer count = eticJdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE Id_Usuario=:id AND Estatus='Activo'", Map.of("id", userId), Integer.class); return count != null && count > 0; }

	private boolean exists(String table, String column, String value, String idColumn, String excludedId) { String sql="SELECT COUNT(*) FROM "+table+" WHERE "+column+"=:value"+(excludedId==null?"":" AND "+idColumn+"<>:id"); MapSqlParameterSource p=new MapSqlParameterSource("value",value); if(excludedId!=null)p.addValue("id",excludedId); Integer count=jdbc.queryForObject(sql,p,Integer.class); return count!=null&&count>0; }
	private String deviceSelect() { return "SELECT d.*,(SELECT CASE WHEN e.Used_At IS NOT NULL THEN 'USED' WHEN e.Revoked_At IS NOT NULL THEN 'REVOKED' WHEN e.Expires_At<CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE 'ACTIVE' END FROM device_enrollment_codes e WHERE e.Id_Device=d.Id_Device ORDER BY e.Created_At DESC LIMIT 1) Enrollment_Status,(SELECT e.Expires_At FROM device_enrollment_codes e WHERE e.Id_Device=d.Id_Device ORDER BY e.Created_At DESC LIMIT 1) Enrollment_Expires_At FROM licensed_devices d"; }
	private String licenseSelect() { return "SELECT l.*,a.Code Application_Code,a.Name Application_Name,a.Licensing_Mode,d.Device_UUID,d.Display_Name,d.Status Device_Status FROM licenses l JOIN licensed_applications a ON a.Id_Application=l.Id_Application JOIN licensed_devices d ON d.Id_Device=l.Id_Device"; }
	private String username(String userId) {
		if (userId == null) return null;
		List<String> names = eticJdbc.query("SELECT Usuario FROM usuarios WHERE Id_Usuario=:id",
			Map.of("id", userId), (rs, row) -> rs.getString(1));
		return names.isEmpty() ? null : names.getFirst();
	}
	private LicensedApplication application(ResultSet r,int n)throws SQLException{return new LicensedApplication(r.getString("Id_Application"),r.getString("Code"),r.getString("Name"),r.getString("Package_Name"),LicensingMode.valueOf(r.getString("Licensing_Mode")),r.getString("Status"),time(r.getTimestamp("Created_At")),time(r.getTimestamp("Updated_At")));}
	private LicensedDevice device(ResultSet r,int n)throws SQLException{return new LicensedDevice(r.getString("Id_Device"),r.getString("Device_UUID"),r.getString("Display_Name"),r.getString("Manufacturer"),r.getString("Model"),r.getString("Android_Version"),r.getString("Status"),r.getString("Origin"),r.getString("Android_ID"),r.getString("Package_Name"),r.getString("App_Version"),r.getString("Public_Key_Fingerprint"),r.getString("Public_Key_Algorithm"),r.getString("Key_Security_Level"),r.getBoolean("Attestation_Available"),r.getBoolean("Attestation_Verified"),r.getString("Notes"),time(r.getTimestamp("Registered_At")),time(r.getTimestamp("Enrolled_At")),time(r.getTimestamp("Key_Rotated_At")),time(r.getTimestamp("Last_Validation_At")),time(r.getTimestamp("Updated_At")),r.getString("Enrollment_Status"),time(r.getTimestamp("Enrollment_Expires_At")));}
	private License license(ResultSet r,int n)throws SQLException{LicenseStatus status=LicenseStatus.valueOf(r.getString("Status"));String deviceStatus=r.getString("Device_Status");String operational=status==LicenseStatus.ACTIVE?(deviceStatus.equals("ACTIVE")||deviceStatus.equals("ENROLLED")?"READY":"PENDING_ENROLLMENT"):status.name();return new License(r.getString("Id_License"),r.getString("Id_Application"),r.getString("Application_Code"),r.getString("Application_Name"),LicensingMode.valueOf(r.getString("Licensing_Mode")),r.getString("Id_Device"),r.getString("Device_UUID"),r.getString("Display_Name"),r.getString("Id_Usuario"),username(r.getString("Id_Usuario")),r.getDate("Valid_From").toLocalDate(),r.getDate("Valid_Until").toLocalDate(),status,deviceStatus,operational,time(r.getTimestamp("Created_At")),time(r.getTimestamp("Updated_At")));}
	private LocalDateTime time(Timestamp value){return value==null?null:value.toLocalDateTime();}
}
