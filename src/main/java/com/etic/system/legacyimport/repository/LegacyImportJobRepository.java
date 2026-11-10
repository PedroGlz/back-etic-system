package com.etic.system.legacyimport.repository;

import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
import com.etic.system.legacyimport.model.LegacyImportError;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;

@Repository
public class LegacyImportJobRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public LegacyImportJobRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@PostConstruct
	void ensureTable() {
		jdbc.getJdbcTemplate().execute("""
			CREATE TABLE IF NOT EXISTS legacy_import_jobs (
				id CHAR(36) NOT NULL PRIMARY KEY,
				filename VARCHAR(255) NOT NULL,
				status VARCHAR(32) NOT NULL,
				phase VARCHAR(64) NOT NULL,
				progress INT NOT NULL DEFAULT 0,
				started_at DATETIME NULL,
				finished_at DATETIME NULL,
				error_message TEXT NULL,
				created_by CHAR(38) NULL,
				created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
				INDEX idx_legacy_import_jobs_status (status)
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
			""");
		addColumnIfMissing("error_stage", "VARCHAR(64) NULL");
		addColumnIfMissing("error_inspection_id", "VARCHAR(64) NULL");
		addColumnIfMissing("error_inspection_number", "BIGINT NULL");
		addColumnIfMissing("error_type", "VARCHAR(255) NULL");
		addColumnIfMissing("error_root_cause", "TEXT NULL");
		addColumnIfMissing("error_causes", "LONGTEXT NULL");
		addColumnIfMissing("error_technical_detail", "LONGTEXT NULL");
		addColumnIfMissing("error_occurred_at", "DATETIME NULL");
	}

	private void addColumnIfMissing(String column,String definition){
		Integer count=jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='legacy_import_jobs' AND column_name=?",Integer.class,column);
		if(count!=null&&count==0)jdbc.getJdbcTemplate().execute("ALTER TABLE legacy_import_jobs ADD COLUMN "+column+" "+definition);
	}

	public void create(LegacyImportJob job) {
		jdbc.update("""
			INSERT INTO legacy_import_jobs
			(id, filename, status, phase, progress, started_at, finished_at, error_message, created_by)
			VALUES (:id, :filename, :status, :phase, :progress, :startedAt, :finishedAt, :errorMessage, :createdBy)
			""", parameters(job));
	}

	public void updateState(
		String id,
		LegacyImportStatus status,
		String phase,
		int progress,
		String errorMessage,
		LocalDateTime finishedAt
	) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("id", id)
			.addValue("status", status.name())
			.addValue("phase", phase)
			.addValue("progress", progress)
			.addValue("errorMessage", errorMessage)
			.addValue("finishedAt", finishedAt == null ? null : Timestamp.valueOf(finishedAt));
		jdbc.update("""
			UPDATE legacy_import_jobs
			SET status = :status, phase = :phase, progress = :progress,
			    error_message = :errorMessage, finished_at = :finishedAt
			WHERE id = :id
			""", parameters);
	}

	public void saveError(String id,String stage,String message,Throwable error){
		saveError(id,stage,null,null,message,error);
	}

	public void saveError(String id,String stage,String inspectionId,Long inspectionNumber,String message,Throwable error){
		List<String> causes=new java.util.ArrayList<>();Throwable current=error;String root=message;
		while(current!=null){String value=current.getMessage();if(value!=null&&!value.isBlank())causes.add(value);root=value==null||value.isBlank()?root:value;current=current.getCause();}
		java.io.StringWriter buffer=new java.io.StringWriter();if(error!=null)error.printStackTrace(new java.io.PrintWriter(buffer));
		String detail=sanitize(buffer.toString());
		MapSqlParameterSource parameters=new MapSqlParameterSource().addValue("id",id).addValue("stage",stage).addValue("inspectionId",inspectionId).addValue("inspectionNumber",inspectionNumber).addValue("message",sanitize(message))
			.addValue("type",error==null?null:error.getClass().getName()).addValue("root",sanitize(root)).addValue("causes",sanitize(String.join("\n",causes)))
			.addValue("detail",detail).addValue("occurredAt",Timestamp.valueOf(LocalDateTime.now()));
		jdbc.update("""
			UPDATE legacy_import_jobs SET error_message=:message,error_stage=:stage,error_inspection_id=:inspectionId,error_inspection_number=:inspectionNumber,error_type=:type,error_root_cause=:root,
				error_causes=:causes,error_technical_detail=:detail,error_occurred_at=:occurredAt WHERE id=:id
			""",parameters);
	}

	public Optional<LegacyImportError> findError(String id){
		return jdbc.query("""
			SELECT id,filename,status,error_message,error_stage,error_inspection_id,error_inspection_number,error_type,error_root_cause,error_causes,error_technical_detail,error_occurred_at
			FROM legacy_import_jobs WHERE id=:id AND error_message IS NOT NULL
			""",Map.of("id",id),(rs,rowNum)->new LegacyImportError(rs.getString("id"),rs.getString("filename"),rs.getString("error_inspection_id"),rs.getObject("error_inspection_number",Long.class),
			rs.getString("error_stage"),rs.getString("status"),rs.getString("error_message"),rs.getString("error_root_cause"),
			rs.getString("error_causes")==null?List.of():rs.getString("error_causes").lines().toList(),rs.getString("error_type"),
			rs.getString("error_technical_detail"),rs.getTimestamp("error_occurred_at")==null?null:rs.getTimestamp("error_occurred_at").toLocalDateTime())).stream().findFirst();
	}

	private String sanitize(String value){
		if(value==null)return null;
		return value.replaceAll("(?i)(password|passwd|token|authorization|jwt|secret|api[_-]?key|user(?:name)?)(\\s*[:=]\\s*)([^\\s,;]+)","$1$2[REDACTADO]")
			.replaceAll("(?i)jdbc:[^\\s]+","[CONEXIÓN REDACTADA]");
	}

	public Optional<LegacyImportJob> findById(String id) {
		return jdbc.query("""
			SELECT id, filename, status, phase, progress, started_at, finished_at, error_message, created_by
			FROM legacy_import_jobs WHERE id = :id
			""", Map.of("id", id), (rs, rowNum) -> new LegacyImportJob(
			rs.getString("id"),
			rs.getString("filename"),
			LegacyImportStatus.valueOf(rs.getString("status")),
			rs.getString("phase"),
			rs.getInt("progress"),
			rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
			rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime(),
			rs.getString("error_message"),
			rs.getString("created_by")
		)).stream().findFirst();
	}

	public Optional<LegacyImportJob> findActiveByUser(String createdBy) {
		return jdbc.query("""
			SELECT id, filename, status, phase, progress, started_at, finished_at, error_message, created_by
			FROM legacy_import_jobs
			WHERE created_by = :createdBy
			  AND status IN ('PROCESSING','VALIDATING_RESULT')
			ORDER BY created_at DESC LIMIT 1
			""",Map.of("createdBy",createdBy),(rs,rowNum)->new LegacyImportJob(
			rs.getString("id"),rs.getString("filename"),LegacyImportStatus.valueOf(rs.getString("status")),rs.getString("phase"),rs.getInt("progress"),
			rs.getTimestamp("started_at")==null?null:rs.getTimestamp("started_at").toLocalDateTime(),
			rs.getTimestamp("finished_at")==null?null:rs.getTimestamp("finished_at").toLocalDateTime(),rs.getString("error_message"),rs.getString("created_by")
		)).stream().findFirst();
	}

	public boolean claimForExecution(String id, String createdBy) {
		MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("id", id).addValue("createdBy", createdBy)
			.addValue("status", LegacyImportStatus.PROCESSING.name()).addValue("phase", "VALIDATION");
		return jdbc.update("""
			UPDATE legacy_import_jobs SET status=:status, phase=:phase, progress=1, finished_at=NULL, error_message=NULL
			WHERE id=:id AND created_by=:createdBy AND status IN ('READY','COMPLETED')
			""", parameters) == 1;
	}

	public List<LegacyImportJob> findFinishedBefore(LocalDateTime completedBefore, LocalDateTime failedBefore) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("completedBefore", Timestamp.valueOf(completedBefore)).addValue("failedBefore", Timestamp.valueOf(failedBefore));
		return jdbc.query("""
			SELECT id, filename, status, phase, progress, started_at, finished_at, error_message, created_by
			FROM legacy_import_jobs
			WHERE (status='COMPLETED' AND finished_at < :completedBefore)
			   OR (status='FAILED' AND finished_at < :failedBefore)
			""", parameters, (rs,rowNum)->new LegacyImportJob(rs.getString("id"),rs.getString("filename"),
			LegacyImportStatus.valueOf(rs.getString("status")),rs.getString("phase"),rs.getInt("progress"),
			rs.getTimestamp("started_at")==null?null:rs.getTimestamp("started_at").toLocalDateTime(),
			rs.getTimestamp("finished_at")==null?null:rs.getTimestamp("finished_at").toLocalDateTime(),rs.getString("error_message"),rs.getString("created_by")));
	}

	private MapSqlParameterSource parameters(LegacyImportJob job) {
		return new MapSqlParameterSource()
			.addValue("id", job.id())
			.addValue("filename", job.filename())
			.addValue("status", job.status().name())
			.addValue("phase", job.phase())
			.addValue("progress", job.progress())
			.addValue("startedAt", Timestamp.valueOf(job.startedAt()))
			.addValue("finishedAt", job.finishedAt() == null ? null : Timestamp.valueOf(job.finishedAt()))
			.addValue("errorMessage", job.errorMessage())
			.addValue("createdBy", job.createdBy());
	}
}
