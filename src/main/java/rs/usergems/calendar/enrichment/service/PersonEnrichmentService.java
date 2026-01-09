package rs.usergems.calendar.enrichment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.usergems.calendar.enrichment.client.PersonFeignClient;
import rs.usergems.calendar.enrichment.config.PersonApiProperties;
import rs.usergems.calendar.enrichment.dto.PersonDto;
import rs.usergems.calendar.enrichment.entity.PersonEntity;
import rs.usergems.calendar.enrichment.repository.PersonRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for enriching person data with 30-day caching logic.
 * 
 * Uses lazy refresh strategy:
 * - Check cache first
 * - If cache is fresh (< 30 days), return cached data
 * - If cache is stale or missing, fetch from Person API
 * - On API failure, return stale cache if available
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonEnrichmentService {

    private static final int CACHE_TTL_DAYS = 30;
    private static final String INTERNAL_DOMAIN = "@usergems.com";

    private final PersonFeignClient personFeignClient;
    private final PersonApiProperties personApiProperties;
    private final PersonRepository personRepository;

    /**
     * Main method to get enriched person data.
     * Implements caching with 30-day TTL and lazy refresh.
     *
     * @param email person's email address
     * @return enriched person data or null for internal users
     */
    public PersonEntity enrichPerson(String email) {
        if (email == null || email.isBlank()) {
            log.debug("Empty email provided for enrichment");
            return null;
        }

        // Skip internal users
        if (isInternalUser(email)) {
            log.debug("Skipping enrichment for internal user: {}", email);
            return null;
        }

        Optional<PersonEntity> cachedPerson = getFromCache(email);

        // Return fresh cache if available
        if (cachedPerson.isPresent() && !shouldRefresh(cachedPerson.get())) {
            log.debug("Using cached person data for {}", email);
            return cachedPerson.get();
        }

        // Cache miss or stale - fetch from API
        log.info("Fetching person data from API for {}", email);
        try {
            PersonDto dto = fetchFromApi(email);
            PersonEntity person = mapToEntity(email, dto);
            return personRepository.save(person);
        } catch (Exception e) {
            log.warn("Person API failed for {}: {}", email, e.getMessage());
            
            // Fallback to stale cache if available
            if (cachedPerson.isPresent()) {
                log.info("Using stale cache for {} due to API failure", email);
                return cachedPerson.get();
            }
            
            log.error("No cached data available for {} and API failed", email);
            return null;
        }
    }

    /**
     * Check if email belongs to internal user
     */
    private boolean isInternalUser(String email) {
        return email.toLowerCase().endsWith(INTERNAL_DOMAIN);
    }

    /**
     * Retrieve person data from cache (database)
     */
    private Optional<PersonEntity> getFromCache(String email) {
        return personRepository.findById(email);
    }

    /**
     * Check if cached person data should be refreshed
     * Returns true if data is older than 30 days
     */
    private boolean shouldRefresh(PersonEntity person) {
        if (person.getFetchedAt() == null) {
            return true;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(CACHE_TTL_DAYS);
        boolean isStale = person.getFetchedAt().isBefore(threshold);
        
        if (isStale) {
            log.debug("Cache is stale for {}, fetched at {}", person.getEmail(), person.getFetchedAt());
        }
        
        return isStale;
    }

    /**
     * Fetch person data from Person API
     */
    private PersonDto fetchFromApi(String email) {
        String authorization = "Bearer " + personApiProperties.getApiKey();
        return personFeignClient.getPersonByEmail(authorization, email);
    }

    /**
     * Map DTO from API to database entity
     */
    private PersonEntity mapToEntity(String email, PersonDto dto) {
        PersonEntity entity = new PersonEntity();
        entity.setEmail(email);
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setAvatarUrl(dto.getAvatar());
        entity.setTitle(dto.getTitle());
        entity.setLinkedinUrl(dto.getLinkedinUrl());
        
        // Map company data if available
        if (dto.getCompany() != null) {
            entity.setCompanyName(dto.getCompany().getName());
            entity.setCompanyLinkedin(dto.getCompany().getLinkedinUrl());
            entity.setCompanyEmployees(dto.getCompany().getEmployees());
        }
        
        entity.setFetchedAt(LocalDateTime.now());
        return entity;
    }
}
