package com.jobtracker.application;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByApplicationIdOrderByChangedAtAsc(Long applicationId);

    @Query("""
            select h from StatusHistory h join fetch h.application a
            where a.user.id = :userId
            order by h.changedAt
            """)
    List<StatusHistory> findAllForUser(@Param("userId") Long userId);
}
