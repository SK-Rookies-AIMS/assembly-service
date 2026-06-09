package com.aims.assembly.mapper;

import com.aims.assembly.dto.auth.TokenResponse;

public final class TokenMapper {

    public static TokenResponse.TokenDTO toTokenDTO(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType
    ) {
        return TokenResponse.TokenDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .tokenType(tokenType)
                .build();
    }
}
