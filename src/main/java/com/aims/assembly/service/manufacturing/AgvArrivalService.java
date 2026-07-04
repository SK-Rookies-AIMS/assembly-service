package com.aims.assembly.service.manufacturing;

import com.aims.assembly.repository.event.ManufacturingEventJsonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgvArrivalService {

    private final ManufacturingEventJsonRepository repository;

    @Transactional
    public void handleArrival(String eventId) {

        int updated =
                repository.releaseNextProcessByEventId(eventId);

        if (updated == 0) {

            log.warn(
                    "[AGV ARRIVAL] 다음 공정을 READY로 변경하지 못했습니다. eventId={}",
                    eventId
            );

            return;
        }


        log.info(
                "[AGV ARRIVAL] 다음 공정을 READY로 변경했습니다. eventId={}, updated={}",
                eventId,
                updated
        );
    }
}