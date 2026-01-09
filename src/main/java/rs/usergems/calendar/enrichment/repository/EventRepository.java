package rs.usergems.calendar.enrichment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount;
import rs.usergems.calendar.enrichment.entity.EventEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    List<EventEntity> findByUserIdAndStartAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    Optional<EventEntity> findByUserIdAndExternalId(Long userId, Long externalId);

    @Query("""
        SELECT COUNT(DISTINCT e.id)
        FROM EventEntity e
        JOIN e.attendees a1
        JOIN e.attendees a2
        WHERE a1.email = :userEmail
        AND a2.email = :contactEmail
        AND e.deleted = false
        """)
    int countMeetingsBetween(@Param("userEmail") String userEmail, @Param("contactEmail") String contactEmail);

    /**
     * Find colleague emails and meeting counts with a specific contact
     */
    @Query("""
        SELECT new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount(a.email, CAST(COUNT(DISTINCT e.id) AS int))
        FROM EventEntity e
        JOIN e.attendees a
        JOIN e.attendees contact
        WHERE contact.email = :contactEmail
        AND a.email <> :userEmail
        AND a.email <> :contactEmail
        AND a.email LIKE '%@usergems.com'
        AND e.deleted = false
        AND e.id IN (
            SELECT e2.id 
            FROM EventEntity e2 
            JOIN e2.attendees userAttendee 
            WHERE userAttendee.email = :userEmail
        )
        GROUP BY a.email
        """)
    List<ColleagueMeetingCount> findColleagueMeetingCounts(@Param("userEmail") String userEmail, @Param("contactEmail") String contactEmail);
}
