package com.jobtracker.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository
        extends JpaRepository<JobApplication, Long>, JpaSpecificationExecutor<JobApplication> {

    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    List<JobApplication> findByUserId(Long userId);

    @Query("""
            select a from JobApplication a join fetch a.user
            where a.status in :statuses
              and a.followUpDate is not null
              and a.followUpDate <= :today
              and a.reminderSentAt is null
            """)
    List<JobApplication> findDueForReminder(
            @Param("statuses") Collection<ApplicationStatus> statuses, @Param("today") LocalDate today);
}
