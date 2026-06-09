package com.aims.assembly.controller;

import com.aims.assembly.common.response.ApiResponse;
import com.aims.assembly.dto.test.TestRequest;
import com.aims.assembly.dto.test.TestResponse;
import com.aims.assembly.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test")
public class TestController {

    private final TestService testService;

    @Operation(summary = "테스트 조회")
    @GetMapping()
    public ApiResponse<Void> testGet() {
        log.info("테스트 조회 요청");
        return ApiResponse.success("태스트 조회 성공입니다.");
    }

    @Operation(summary = "테스트 예외 발생")
    @GetMapping("/error")
    public ApiResponse<Void> error() {
        testService.throwBusinessError();
        return ApiResponse.success("도달하지 않는 응답입니다.");
    }

//    @Operation(summary = "테스트 사용자 조회")
//    @GetMapping("/users/{id}")
//    public ApiResponse<TestResponse.UserDTO> findUser(@PathVariable @Positive Long id) {
//        TestResponse.UserDTO response = testService.findUser(id);
//        return ApiResponse.success(response, "테스트 사용자 조회 성공");
//    }
//
//    @Operation(summary = "테스트 사용자 생성")
//    @PostMapping("/users")
//    public ApiResponse<TestResponse.UserDTO> createSampleUser(
//            @Valid @RequestBody TestRequest.CreateUserDTO request
//    ) {
//        TestResponse.UserDTO response = testService.createSampleUser(request);
//        return ApiResponse.success(response, "테스트 사용자 생성 성공");
//    }
}
