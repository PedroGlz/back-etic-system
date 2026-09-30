package com.etic.system.licensing.distribution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ApkStorage {
	private final Path root;
	private final long maxBytes;

	public ApkStorage(@Value("${app.storage.applications-root}") String root,
		@Value("${app.storage.applications-max-bytes}") long maxBytes) {
		this.root = Path.of(root).toAbsolutePath().normalize();
		this.maxBytes = maxBytes;
	}

	public StoredApk store(String code, long versionCode, MultipartFile file) {
		String original = file.getOriginalFilename();
		if (file.isEmpty() || file.getSize() > maxBytes || original == null ||
			!original.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") ||
			original.contains("/") || original.indexOf(92) >= 0 || original.contains("..")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo APK inválido o demasiado grande");
		}
		if (!code.matches("[A-Za-z0-9_-]+") || versionCode <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de aplicación inválida");
		}
		Path target = root.resolve(code).resolve(Long.toString(versionCode)).resolve("application.apk").normalize();
		if (!target.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		Path temp = null;
		try {
			Files.createDirectories(target.getParent());
			if (!target.getParent().toRealPath().startsWith(root.toRealPath()))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta APK inválida");
			if (Files.exists(target)) throw new ResponseStatusException(HttpStatus.CONFLICT, "La versión ya tiene un APK");
			temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			long size = 0;
			try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
				byte[] buffer = new byte[8192];
				int count;
				try (var output = Files.newOutputStream(temp)) {
					while ((count = input.read(buffer)) != -1) {
						size += count;
						if (size > maxBytes) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "APK demasiado grande");
						output.write(buffer, 0, count);
					}
				}
			}
			if (size == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "APK vacío");
			original = original.replaceAll("[^A-Za-z0-9._-]", "_");
			if (original.length() > 255) original = original.substring(original.length() - 255);
			Files.move(temp, target);
			return new StoredApk(root.relativize(target).toString().replace('\\', '/'), original,
				HexFormat.of().formatHex(digest.digest()), size);
		} catch (FileAlreadyExistsException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La versión ya tiene un APK", e);
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo almacenar el APK", e);
		} finally {
			if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
		}
	}

	public Path resolve(String relative) {
		if (relative == null || relative.indexOf(92) >= 0 || relative.startsWith("/"))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		Path file = root.resolve(relative).normalize();
		if (!file.startsWith(root) || !Files.isRegularFile(file))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "APK no disponible");
		try {
			if (!file.toRealPath().startsWith(root.toRealPath()))
				throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "APK no disponible", e);
		}
		return file;
	}

	public void discard(String relative) {
		Path file = root.resolve(relative).normalize();
		if (file.startsWith(root)) try { Files.deleteIfExists(file); } catch (IOException ignored) { }
	}

	public record StoredApk(String path, String originalName, String sha256, long size) {}
}
