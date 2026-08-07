package com.etic.system.clientes.infrastructure.in.rest.request;

import jakarta.validation.constraints.NotBlank;

public record ClienteStatusRequest(@NotBlank String status) {
}
