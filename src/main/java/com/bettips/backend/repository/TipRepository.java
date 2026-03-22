package com.bettips.backend.repository;

import com.bettips.backend.entity.Tip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TipRepository extends JpaRepository<Tip, String> {
    List<Tip> findByGameDate(LocalDate date);
    List<Tip> findByGameDateAndLevel(LocalDate date, Tip.TipLevel level);
    List<Tip> findByLevel(Tip.TipLevel level);
    List<Tip> findByGameDateAndSentFalse(LocalDate date);
}
