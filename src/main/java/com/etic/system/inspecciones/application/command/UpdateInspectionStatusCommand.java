package com.etic.system.inspecciones.application.command;

import java.time.LocalDateTime;

public record UpdateInspectionStatusCommand(String statusId, LocalDateTime endDate) {
}
