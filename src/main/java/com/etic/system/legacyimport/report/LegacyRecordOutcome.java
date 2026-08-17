package com.etic.system.legacyimport.report;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

public record LegacyRecordOutcome(
	String table,
	String id,
	String action,
	String reason,
	@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") LocalDateTime sourceDate,
	@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") LocalDateTime destinationDate
) {}
