package com.aims.assembly.dto.test;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TestRequest {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "테스트 사용자 생성 요청 정보")
    public static class TestCreateUserDTO {

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "이메일", example = "user@example.com")
        private String email;
    }
}
