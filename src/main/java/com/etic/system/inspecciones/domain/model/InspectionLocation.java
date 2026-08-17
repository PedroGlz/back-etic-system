package com.etic.system.inspecciones.domain.model;

public record InspectionLocation(String id, String status, Integer mta) {
	public InspectionLocation(String id, String status) {
		this(id, status, null);
	}
}
