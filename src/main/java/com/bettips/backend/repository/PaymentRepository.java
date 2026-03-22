package com.bettips.backend.repository;

import com.bettips.backend.entity.Payment;
import com.bettips.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    List<Payment> findByUserOrderByCreatedAtDesc(User user);
    Optional<Payment> findByCheckoutRequestId(String checkoutRequestId);
    Optional<Payment> findByMpesaRef(String mpesaRef);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESS'")
    Long totalRevenue();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCESS'")
    long countSuccessful();
}
