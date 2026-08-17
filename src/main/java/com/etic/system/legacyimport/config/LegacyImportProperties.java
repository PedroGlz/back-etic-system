package com.etic.system.legacyimport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "app.legacy-import")
public class LegacyImportProperties {

	private DataSize maxFileSize = DataSize.ofMegabytes(512);
	private long maxRecords = 5_000_000;
	private int batchSize = 500;
	private int completedRetentionHours = 24;
	private int failedRetentionHours = 72;

	public DataSize getMaxFileSize() {
		return maxFileSize;
	}

	public void setMaxFileSize(DataSize maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	public long getMaxRecords() {
		return maxRecords;
	}

	public void setMaxRecords(long maxRecords) {
		this.maxRecords = maxRecords;
	}

	public int getBatchSize() { return batchSize; }
	public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
	public int getCompletedRetentionHours(){return completedRetentionHours;}
	public void setCompletedRetentionHours(int value){this.completedRetentionHours=value;}
	public int getFailedRetentionHours(){return failedRetentionHours;}
	public void setFailedRetentionHours(int value){this.failedRetentionHours=value;}
}
