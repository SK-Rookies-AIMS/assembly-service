package com.aims.assembly.repository;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class TestRepository {

    private final AtomicLong sequence = new AtomicLong(1L);
    private final Map<Long, TestUser> users = new ConcurrentHashMap<>();

    public TestRepository() {
        TestUser user = new TestUser(1L, "user@example.com");
        users.put(user.id(), user);
    }

    public Optional<TestUser> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    public TestUser save(String email) {
        Long id = sequence.updateAndGet(current -> Math.max(current + 1, 2L));
        TestUser user = new TestUser(id, email);
        users.put(id, user);
        return user;
    }

    public record TestUser(Long id, String email) {
    }
}
