package com.aims.assembly.config.jwt;

public final class JwtConstants {

    public static final String HEADER_STRING = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String ROLE_CLAIM = "role";
    public static final String USER_ID_CLAIM = "userId";

    private JwtConstants() {
    }
}
