package com.bettips.backend.repository;

import com.bettips.backend.entity.SentTip;
import com.bettips.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SentTipRepository extends JpaRepository<SentTip, String> {
    boolean existsByUserAndTipId(User user, String tipId);

    // Has this subscription already been used to send any tip?
    boolean existsBySubscriptionId(String subscriptionId);
}
