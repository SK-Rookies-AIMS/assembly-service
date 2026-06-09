package com.aims.assembly.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TokenResponse {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "JWT 토큰 응답 정보")
    public static class TokenDTO {

        @Schema(description = "Access Token")
        private String accessToken;

        @Schema(description = "Refresh Token")
        private String refreshToken;

        @Schema(description = "토큰 타입", example = "Bearer")
        private String tokenType;

        @Schema(description = "Access Token 만료 시간(초)", example = "3600")
        private long expiresIn;
    }
}
