package com.aims.assembly.controller.internal;

import com.aims.assembly.dto.manufacturing.AgvArrivalRequest;
import com.aims.assembly.service.manufacturing.AgvArrivalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/agv-arrivals")
@RequiredArgsConstructor
public class AgvArrivalController {

    private final AgvArrivalService agvArrivalService;

    @PostMapping
    public ResponseEntity<Void> arrived(
            @RequestBody AgvArrivalRequest request
    ) {

        agvArrivalService.handleArrival(
                request.getEventId()
        );

        return ResponseEntity.ok().build();
    }
}