package com.etic.system.licensing.distribution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ApkStorageTest {
	@TempDir Path root;

	@Test void storesApkAndCalculatesRealShaAndSize() throws Exception {
		ApkStorage storage = new ApkStorage(root.toString(), 1024);
		byte[] bytes = "apk-content".getBytes();
		var file = new MockMultipartFile("file", "etic.apk", "application/octet-stream", bytes);
		var stored = storage.store("ETIC", 12, file);
		assertEquals(bytes.length, stored.size());
		assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), stored.sha256());
		assertArrayEquals(bytes, Files.readAllBytes(storage.resolve(stored.path())));
		assertThrows(ResponseStatusException.class, () -> storage.store("ETIC", 12, file));
	}

	@Test void rejectsInvalidFilesAndTraversal() {
		ApkStorage storage = new ApkStorage(root.toString(), 4);
		assertThrows(ResponseStatusException.class, () -> storage.store("ETIC", 1,
			new MockMultipartFile("file", "text.txt", "text/plain", "x".getBytes())));
		assertThrows(ResponseStatusException.class, () -> storage.store("ETIC", 1,
			new MockMultipartFile("file", "app.apk", "application/octet-stream", "12345".getBytes())));
		assertThrows(ResponseStatusException.class, () -> storage.store("ETIC", 1,
			new MockMultipartFile("file", "../app.apk", "application/octet-stream", "x".getBytes())));
		assertThrows(ResponseStatusException.class, () -> storage.store("../outside", 1,
			new MockMultipartFile("file", "app.apk", "application/octet-stream", "x".getBytes())));
		assertThrows(ResponseStatusException.class, () -> storage.resolve("../outside.apk"));
	}
}
