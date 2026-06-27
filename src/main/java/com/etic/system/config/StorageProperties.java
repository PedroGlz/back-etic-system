package com.etic.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

	private String path = "storage";

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Path basePath() {
		return Path.of(path).toAbsolutePath().normalize();
	}
}
