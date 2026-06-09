package com.aims.assembly.service;

import com.aims.assembly.common.status.ErrorStatus;
import com.aims.assembly.dto.test.TestRequest;
import com.aims.assembly.dto.test.TestResponse;
import com.aims.assembly.exception.handler.ErrorHandler;
import com.aims.assembly.mapper.TestMapper;
import com.aims.assembly.repository.TestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestService {

    private final TestRepository testRepository;

    public TestResponse.TestUserDTO findUser(Long id) {
        TestRepository.TestUser user = testRepository.findById(id)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.NOT_FOUND, "테스트 사용자를 찾을 수 없습니다."));

        return TestMapper.toUserDTO(user);
    }

    @Transactional
    public TestResponse.TestUserDTO createSampleUser(TestRequest.TestCreateUserDTO request) {
        TestRepository.TestUser user = testRepository.save(request.getEmail());
        return TestMapper.toUserDTO(user);
    }

    public void throwBusinessError() {
        throw new ErrorHandler(ErrorStatus.BAD_REQUEST, "테스트 비즈니스 예외입니다.");
    }
}
