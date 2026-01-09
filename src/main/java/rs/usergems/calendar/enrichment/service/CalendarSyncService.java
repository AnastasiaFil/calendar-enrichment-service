package rs.usergems.calendar.enrichment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.usergems.calendar.enrichment.dto.CalendarEventDto;
import rs.usergems.calendar.enrichment.entity.EventAttendeeEntity;
import rs.usergems.calendar.enrichment.entity.EventEntity;
import rs.usergems.calendar.enrichment.entity.UserEntity;
import rs.usergems.calendar.enrichment.repository.EventAttendeeRepository;
import rs.usergems.calendar.enrichment.repository.EventRepository;
import rs.usergems.calendar.enrichment.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private final CalendarApiService calendarApiClient;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventAttendeeRepository eventAttendeeRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Full synchronization - fetches ALL pages from Calendar API.
     * Used on first user connection (initial load).
     *
     * @param userId the user ID
     */
    @Transactional
    public void syncEvents(Long userId) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", userId);
            return;
        }

        UserEntity user = userOpt.get();
        log.info("Starting FULL calendar sync for user: {}", user.getEmail());

        int page = 1;
        boolean hasMore = true;
        int totalProcessed = 0;

        while (hasMore) {
            log.debug("Fetching page {} for user {}", page, user.getEmail());

            // Fetch page from Calendar API
            var response = calendarApiClient.fetchEventsPage(user.getEmail(), page);

            if (response == null || response.getData() == null) {
                log.warn("No response or data for page {}", page);
                hasMore = false;
                break;
            }

            // Process all events from this page
            for (CalendarEventDto dto : response.getData()) {
                upsertEvent(userId, dto);
                totalProcessed++;
            }

            // Calculate total pages
            int totalPages = (int) Math.ceil((double) response.getTotal() / response.getPerPage());
            hasMore = page < totalPages;
            page++;
        }

        // Update last sync time
        user.setLastSyncAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("FULL sync completed for user: {}. Processed {} events across {} pages",
                user.getEmail(), totalProcessed, page - 1);
    }

    /**
     * Incremental sync - fetches only events with changed_at > last_sync_at.
     * Optimizes API calls by stopping when reaching already synced events.
     * <p>
     * How it works:
     * 1. Calendar API returns events sorted by changed_at DESC (newest changes first)
     * 2. We iterate through pages and compare changed_at with last_sync_at
     * 3. When we encounter event.changed_at < last_sync_at, we stop - all remaining events are already synced
     * 4. This saves API calls and database operations
     *
     * @param userId the user ID
     */
    @Transactional
    public void incrementalSync(Long userId) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", userId);
            return;
        }

        UserEntity user = userOpt.get();
        LocalDateTime lastSync = user.getLastSyncAt();

        if (lastSync == null) {
            log.info("No previous sync found. Falling back to full sync for user: {}", user.getEmail());
            syncEvents(userId);
            return;
        }

        log.info("Starting INCREMENTAL sync for user: {} since {}", user.getEmail(), lastSync);

        int page = 1;
        boolean hasMore = true;
        int totalProcessed = 0;
        int totalSkipped = 0;
        LocalDateTime syncStartTime = LocalDateTime.now();

        while (hasMore) {
            log.debug("Fetching page {} for incremental sync, user {}", page, user.getEmail());

            var response = calendarApiClient.fetchEventsPage(user.getEmail(), page);

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                log.debug("No more data to fetch on page {}", page);
                hasMore = false;
                break;
            }

            // Process events, stop if we reach already synced data
            boolean reachedSyncedEvents = false;
            for (CalendarEventDto dto : response.getData()) {
                LocalDateTime changedAt = parseDateTime(dto.getChanged());

                // Skip events with invalid changed_at
                if (changedAt == null) {
                    log.warn("Event {} has null changed_at, skipping", dto.getId());
                    totalSkipped++;
                    continue;
                }

                // Stop if this event was changed before our last sync
                // Since events are sorted by changed_at DESC, all following events are also old
                if (changedAt.isBefore(lastSync) || changedAt.isEqual(lastSync)) {
                    log.info("Reached already synced events. Stopping at event {} (changed: {}, last_sync: {})",
                            dto.getId(), changedAt, lastSync);
                    reachedSyncedEvents = true;
                    hasMore = false;
                    break;
                }

                // Process event that was changed after last sync
                log.debug("Processing event {} changed at {} (after last sync {})",
                        dto.getId(), changedAt, lastSync);
                upsertEvent(userId, dto);
                totalProcessed++;
            }

            // If we reached synced events, no need to fetch more pages
            if (reachedSyncedEvents) {
                break;
            }

            // Check if there are more pages
            int totalPages = (int) Math.ceil((double) response.getTotal() / response.getPerPage());
            if (page >= totalPages) {
                log.debug("Reached last page {}/{}", page, totalPages);
                hasMore = false;
            }
            page++;
        }

        // Update last sync time to when we started this sync
        // This prevents race conditions where events changed during sync
        user.setLastSyncAt(syncStartTime);
        userRepository.save(user);

        log.info("INCREMENTAL sync completed for user: {}. Processed: {}, Skipped: {}, Pages fetched: {}",
                user.getEmail(), totalProcessed, totalSkipped, page - 1);
    }

    /**
     * Returns today's events from local database.
     * Fast SELECT without calling Calendar API.
     *
     * @param userId the user ID
     * @return list of events for today
     */
    public List<EventEntity> getTodayEvents(Long userId) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        log.debug("Fetching today's events for user {} between {} and {}", userId, todayStart, todayEnd);

        return eventRepository.findByUserIdAndStartAtBetween(userId, todayStart, todayEnd);
    }

    /**
     * Upsert (insert or update) event from Calendar API DTO.
     * Handles both new events and updates to existing events.
     *
     * @param userId the user ID
     * @param dto    the event DTO from Calendar API
     */
    @Transactional
    public void upsertEvent(Long userId, CalendarEventDto dto) {
        // Find existing event by user_id + external_id
        Optional<EventEntity> existingEventOpt = eventRepository
                .findByUserIdAndExternalId(userId, dto.getId());

        EventEntity event;
        if (existingEventOpt.isPresent()) {
            event = existingEventOpt.get();
            log.debug("Updating existing event: {} (external_id: {})", event.getId(), dto.getId());
        } else {
            event = new EventEntity();
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            event.setUser(user);
            event.setExternalId(dto.getId());
            log.debug("Creating new event with external_id: {}", dto.getId());
        }

        // Update event fields
        event.setTitle(dto.getTitle());
        event.setStartAt(parseDateTime(dto.getStart()));
        event.setEndAt(parseDateTime(dto.getEnd()));
        event.setChangedAt(parseDateTime(dto.getChanged()));
        event.setSyncedAt(LocalDateTime.now());
        event.setDeleted(false);

        event = eventRepository.save(event);

        // Update attendees
        updateEventAttendees(event, dto);
    }

    /**
     * Updates attendees for an event
     */
    private void updateEventAttendees(EventEntity event, CalendarEventDto eventDto) {
        // Clear existing attendees
        event.getAttendees().clear();
        eventAttendeeRepository.flush();

        List<EventAttendeeEntity> attendees = new ArrayList<>();

        // Add accepted attendees
        if (eventDto.getAccepted() != null) {
            for (String email : eventDto.getAccepted()) {
                EventAttendeeEntity attendee = new EventAttendeeEntity();
                attendee.setEvent(event);
                attendee.setEmail(email);
                attendee.setStatus("accepted");
                attendees.add(attendee);
            }
        }

        // Add rejected attendees
        if (eventDto.getRejected() != null) {
            for (String email : eventDto.getRejected()) {
                EventAttendeeEntity attendee = new EventAttendeeEntity();
                attendee.setEvent(event);
                attendee.setEmail(email);
                attendee.setStatus("rejected");
                attendees.add(attendee);
            }
        }

        event.getAttendees().addAll(attendees);
    }

    /**
     * Parses ISO date-time string to LocalDateTime
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            log.error("Error parsing datetime: {}", dateTimeStr, e);
            return null;
        }
    }
}
