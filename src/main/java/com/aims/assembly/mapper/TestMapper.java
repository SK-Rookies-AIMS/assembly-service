package com.aims.assembly.mapper;

import com.aims.assembly.dto.test.TestResponse;
import com.aims.assembly.repository.TestRepository;

public final class TestMapper {

    private TestMapper() {
    }

    public static TestResponse.TestUserDTO toUserDTO(TestRepository.TestUser user) {
        return TestResponse.TestUserDTO.builder()
                .id(user.id())
                .email(user.email())
                .build();
    }
}
