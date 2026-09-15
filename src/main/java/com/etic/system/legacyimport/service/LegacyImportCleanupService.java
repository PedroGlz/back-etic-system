package com.etic.system.legacyimport.service;

import com.etic.system.config.StorageProperties;
import com.etic.system.legacyimport.config.LegacyImportProperties;
import com.etic.system.legacyimport.repository.LegacyImportJobRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
public class LegacyImportCleanupService {
	private final LegacyImportJobRepository jobs;
	private final LegacyImportProperties properties;
	private final Path directory;
	public LegacyImportCleanupService(LegacyImportJobRepository jobs,LegacyImportProperties properties,StorageProperties storage){
		this.jobs=jobs;this.properties=properties;this.directory=storage.basePath().resolve("legacy-imports").normalize();
	}
	public void cleanExpired(){
		LocalDateTime now=LocalDateTime.now();
		jobs.findFinishedBefore(now.minusHours(properties.getCompletedRetentionHours()),now.minusHours(properties.getFailedRetentionHours()))
			.forEach(job->{delete(job.id()+".sql");delete(job.id()+"-report.json");delete(job.id()+"-report.md");deleteDirectory(job.id()+"-datasets");});
	}
	private void delete(String filename){Path target=directory.resolve(filename).normalize();if(!target.startsWith(directory))return;try{Files.deleteIfExists(target);}catch(IOException ignored){}}
	private void deleteDirectory(String filename){Path target=directory.resolve(filename).normalize();if(!target.startsWith(directory)||!Files.exists(target))return;try(var paths=Files.walk(target)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
