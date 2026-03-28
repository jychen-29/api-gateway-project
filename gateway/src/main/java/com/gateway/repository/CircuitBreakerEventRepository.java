package com.gateway.repository;

import com.gateway.model.CircuitBreakerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CircuitBreakerEventRepository extends JpaRepository<CircuitBreakerEvent, Long> {

    List<CircuitBreakerEvent> findTop50ByOrderByTimestampDesc();

    Optional<CircuitBreakerEvent> findFirstByServiceNameOrderByTimestampDesc(String serviceName);
}
