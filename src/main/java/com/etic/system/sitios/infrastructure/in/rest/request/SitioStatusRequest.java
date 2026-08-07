package com.etic.system.sitios.infrastructure.in.rest.request;

import jakarta.validation.constraints.NotBlank;

public record SitioStatusRequest(@NotBlank String status) {
}
