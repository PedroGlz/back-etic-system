package com.etic.system.legacyimport.repository;

import com.etic.system.legacyimport.model.LegacyImportJob;
import com.etic.system.legacyimport.model.LegacyImportStatus;
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
			  AND status IN ('UPLOADED','VALIDATING','READY','PROCESSING','VALIDATING_RESULT')
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
