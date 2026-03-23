package com.bettips.backend.repository;

import com.bettips.backend.entity.Subscription;
import com.bettips.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    Optional<Subscription> findTopByUserAndActiveTrueOrderByEndDateDesc(User user);
    List<Subscription> findByUser(User user);

    @Query("SELECT s FROM Subscription s WHERE s.active = true AND s.endDate < :now")
    List<Subscription> findExpiredSubscriptions(LocalDateTime now);

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.active = true")
    long countActiveSubscriptions();

    // SubscriptionRepository.java — add this query
    @Query("SELECT s FROM Subscription s WHERE s.active = true AND s.endDate > :now")
    List<Subscription> findAllActiveSubscriptions(@Param("now") LocalDateTime now);
}
