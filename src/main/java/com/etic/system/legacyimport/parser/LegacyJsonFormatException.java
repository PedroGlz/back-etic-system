package com.etic.system.legacyimport.parser;

public class LegacyJsonFormatException extends RuntimeException {

	public LegacyJsonFormatException(String message) {
		super(message);
	}

	public LegacyJsonFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
