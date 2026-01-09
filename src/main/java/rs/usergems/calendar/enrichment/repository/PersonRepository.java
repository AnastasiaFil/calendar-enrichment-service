package rs.usergems.calendar.enrichment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.usergems.calendar.enrichment.entity.PersonEntity;

@Repository
public interface PersonRepository extends JpaRepository<PersonEntity, String> {
}
