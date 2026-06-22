package com.etic.system.auth.domain;

public record AuthenticatedUser(
	String id,
	String username,
	String name,
	String email,
	String groupId,
	String groupName,
	String title,
	String certificationLevel
) {
}
