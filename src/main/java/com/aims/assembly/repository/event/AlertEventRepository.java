package com.aims.assembly.repository.event;

import com.aims.assembly.domain.alert.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, String> {
    Optional<AlertEvent> findByEventId(String eventId);
    List<AlertEvent> findByEventIdIn(Collection<String> eventIds);

    default Optional<String> findLogNoByEventId(String eventId) {
        return findByEventId(eventId).map(AlertEvent::getLogNo);
    }
}
