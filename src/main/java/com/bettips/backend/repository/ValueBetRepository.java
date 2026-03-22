package com.bettips.backend.repository;

import com.bettips.backend.entity.ValueBet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ValueBetRepository extends JpaRepository<ValueBet, String> {
    List<ValueBet> findByCategoryOrderByMatchNumberAsc(ValueBet.Category category);
    List<ValueBet> findByCategoryAndGameDate(ValueBet.Category category, LocalDate date);
    List<ValueBet> findBySentFalse();
}
