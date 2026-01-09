package rs.usergems.calendar.enrichment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import rs.usergems.calendar.enrichment.dto.EmailContentJson;
import rs.usergems.calendar.enrichment.entity.EmailEntity;
import rs.usergems.calendar.enrichment.entity.EventAttendeeEntity;
import rs.usergems.calendar.enrichment.entity.EventEntity;
import rs.usergems.calendar.enrichment.entity.UserEntity;
import rs.usergems.calendar.enrichment.repository.EmailRepository;
import rs.usergems.calendar.enrichment.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Morning Email Orchestrator Service
 * 
 * Coordinates the entire flow of generating morning emails for all users:
 * 1. Syncs calendar data (incremental)
 * 2. Fetches today's events
 * 3. Enriches external attendees with Person API (only accepted, not declined)
 * 4. Builds email content (JSON and HTML)
 * 5. Saves email to database
 * 
 * Scheduled to run every day at 8:00 AM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MorningEmailService {

    private final UserRepository userRepository;
    private final CalendarSyncService calendarSyncService;
    private final PersonEnrichmentService personEnrichmentService;
    private final EmailBuilderService emailBuilderService;
    private final EmailRepository emailRepository;
    private final ObjectMapper objectMapper;

    private static final String USERGEMS_DOMAIN = "@usergems.com";

    @Scheduled(cron = "0 0 8 * * *")
    public void generateEmailsForAllUsers() {
        log.info("Starting morning email generation for all users at {}", LocalDate.now());

        List<UserEntity> users = userRepository.findAll();
        log.info("Found {} users to process", users.size());

        int successCount = 0;
        int failureCount = 0;
        int emptyCount = 0;

        for (UserEntity user : users) {
            try {
                boolean generated = generateEmailForUser(user.getId());
                if (generated) {
                    successCount++;
                } else {
                    emptyCount++;
                }
            } catch (Exception e) {
                log.error("Failed to generate email for user {}: {}", user.getEmail(), e.getMessage(), e);
                failureCount++;
            }
        }

        log.info("Morning email generation completed. Success: {}, Empty: {}, Failed: {}",
                successCount, emptyCount, failureCount);
    }

    /**
     * Generates morning email for a single user
     * 
     * Flow:
     * 1. Incremental calendar sync
     * 2. Get today's events
     * 3. Enrich external accepted attendees with Person API (skip declined)
     * 4. Build email JSON and HTML
     * 5. Save to database
     * 
     * @param userId user ID
     * @return true if email was generated, false if no events today
     */
    public boolean generateEmailForUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        log.info("Generating morning email for user: {}", user.getEmail());

        try {
            // Step 1: Sync calendar (incremental)
            log.debug("Step 1: Syncing calendar for user {}", user.getEmail());
            calendarSyncService.incrementalSync(userId);

            // Step 2: Get today's events
            log.debug("Step 2: Fetching today's events for user {}", user.getEmail());
            List<EventEntity> events = calendarSyncService.getTodayEvents(userId);

            if (events.isEmpty()) {
                log.info("No events today for user {}. Skipping email generation.", user.getEmail());
                return false;
            }

            log.info("Found {} events for user {}", events.size(), user.getEmail());

            // Step 3: Enrich external attendees (only accepted, skip declined and internal)
            log.debug("Step 3: Enriching attendees for user {}", user.getEmail());
            enrichAttendees(events, user.getEmail());

            // Step 4: Build email content
            log.debug("Step 4: Building email content for user {}", user.getEmail());
            EmailContentJson jsonContent = emailBuilderService.buildJson(userId, events);
            String htmlContent = emailBuilderService.buildHtml(jsonContent);

            // Step 5: Save email to database
            log.debug("Step 5: Saving email to database for user {}", user.getEmail());
            EmailEntity email = new EmailEntity();
            email.setUser(user);
            email.setEmailDate(LocalDate.now());
            email.setContentJson(objectMapper.writeValueAsString(jsonContent));
            email.setContentHtml(htmlContent);
            emailRepository.save(email);

            log.info("Successfully generated morning email for user {}", user.getEmail());
            return true;

        } catch (Exception e) {
            log.error("Error generating email for user {}: {}", user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate email for user " + userId, e);
        }
    }

    /**
     * Enriches external attendees with Person API data
     * 
     * Rules:
     * - Skip internal users (@usergems.com)
     * - Skip declined attendees (use stale data from DB)
     * - Only enrich accepted/tentative attendees
     * 
     * @param events list of events
     * @param userEmail current user's email (to exclude from enrichment)
     */
    private void enrichAttendees(List<EventEntity> events, String userEmail) {
        int enrichedCount = 0;
        int skippedDeclined = 0;
        int skippedInternal = 0;

        for (EventEntity event : events) {
            for (EventAttendeeEntity attendee : event.getAttendees()) {
                String email = attendee.getEmail();

                // Skip current user
                if (email.equals(userEmail)) {
                    continue;
                }

                // Skip internal users
                if (isInternalUser(email)) {
                    skippedInternal++;
                    continue;
                }

                // Skip declined attendees - use stale data from DB
                if ("declined".equalsIgnoreCase(attendee.getStatus())) {
                    log.debug("Skipping enrichment for declined attendee: {}", email);
                    skippedDeclined++;
                    continue;
                }

                // Enrich accepted/tentative attendees
                try {
                    log.debug("Enriching attendee: {} (status: {})", email, attendee.getStatus());
                    personEnrichmentService.enrichPerson(email);
                    enrichedCount++;
                } catch (Exception e) {
                    log.warn("Failed to enrich attendee {}: {}. Will use cached data if available.",
                            email, e.getMessage());
                }
            }
        }

        log.info("Attendee enrichment completed. Enriched: {}, Skipped declined: {}, Skipped internal: {}",
                enrichedCount, skippedDeclined, skippedInternal);
    }

    /**
     * Check if email belongs to internal user
     */
    private boolean isInternalUser(String email) {
        return email != null && email.toLowerCase().endsWith(USERGEMS_DOMAIN);
    }
}
