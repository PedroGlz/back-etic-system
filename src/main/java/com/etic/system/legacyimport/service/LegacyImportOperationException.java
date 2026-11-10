package com.etic.system.legacyimport.service;

public class LegacyImportOperationException extends RuntimeException {
	private final String processId;
	private final String stage;
	public LegacyImportOperationException(String processId,String stage,String message,Throwable cause){super(message,cause);this.processId=processId;this.stage=stage;}
	public String processId(){return processId;}
	public String stage(){return stage;}
}
