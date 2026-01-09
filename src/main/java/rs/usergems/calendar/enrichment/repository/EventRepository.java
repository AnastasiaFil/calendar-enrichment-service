package rs.usergems.calendar.enrichment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.usergems.calendar.enrichment.entity.EventEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    List<EventEntity> findByUserIdAndStartAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    Optional<EventEntity> findByUserIdAndExternalId(Long userId, Long externalId);
}
