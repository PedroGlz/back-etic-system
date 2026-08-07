package com.etic.system.grupossitios.infrastructure.in.rest.request;

import jakarta.validation.constraints.NotBlank;

public record GrupoSitiosStatusRequest(@NotBlank String status) {
}
