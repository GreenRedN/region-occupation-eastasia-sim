package com.green.regionoccupation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 점령 요청.
 */
public record OccupyRequest(
        @NotBlank String regionKey,
        @NotBlank String ownerId
) {
}

