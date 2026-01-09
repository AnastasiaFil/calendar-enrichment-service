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
     * Incremental sync - fetches only events with changed > last_sync_at.
     * Saves API calls by stopping when reaching already synced events.
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

        while (hasMore) {
            log.debug("Fetching page {} for incremental sync, user {}", page, user.getEmail());
            
            var response = calendarApiClient.fetchEventsPage(user.getEmail(), page);
            
            if (response == null || response.getData() == null) {
                hasMore = false;
                break;
            }

            // Process events, stop if we reach already synced data
            for (CalendarEventDto dto : response.getData()) {
                LocalDateTime changedAt = parseDateTime(dto.getChanged());
                
                // Stop if this event was changed before our last sync
                if (changedAt != null && changedAt.isBefore(lastSync)) {
                    log.info("Reached already synced events. Stopping at event {} (changed: {})", 
                            dto.getId(), changedAt);
                    hasMore = false;
                    break;
                }
                
                upsertEvent(userId, dto);
                totalProcessed++;
            }

            // Check if there are more pages
            int totalPages = (int) Math.ceil((double) response.getTotal() / response.getPerPage());
            if (page >= totalPages) {
                hasMore = false;
            }
            page++;
        }

        // Update last sync time
        user.setLastSyncAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("INCREMENTAL sync completed for user: {}. Processed {} new/updated events", 
                user.getEmail(), totalProcessed);
    }

    /**
     * Synchronizes calendar events for a user
     *
     * @param userId the user ID
     * @deprecated Use syncEvents() for initial sync or incrementalSync() for updates
     */
    @Deprecated
    @Transactional
    public void syncUserEvents(Long userId) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", userId);
            return;
        }

        UserEntity user = userOpt.get();
        log.info("Starting calendar sync for user: {}", user.getEmail());

        List<CalendarEventDto> events;

        // If user has been synced before, fetch only changed events
        if (user.getLastSyncAt() != null) {
            String lastSyncTimestamp = user.getLastSyncAt().format(DATE_TIME_FORMATTER);
            events = calendarApiClient.fetchChangedEvents(user.getEmail(), lastSyncTimestamp);
            log.info("Fetched {} changed events since {}", events.size(), lastSyncTimestamp);
        } else {
            // First sync - fetch all events
            events = calendarApiClient.fetchAllEvents(user.getEmail());
            log.info("Fetched {} total events (first sync)", events.size());
        }

        // Process and save events
        for (CalendarEventDto eventDto : events) {
            saveOrUpdateEvent(user, eventDto);
        }

        // Update last sync time
        user.setLastSyncAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Calendar sync completed for user: {}", user.getEmail());
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
     * Saves or updates an event from the API
     */
    private void saveOrUpdateEvent(UserEntity user, CalendarEventDto eventDto) {
        Optional<EventEntity> existingEventOpt = eventRepository.findById(eventDto.getId());

        EventEntity event;
        if (existingEventOpt.isPresent()) {
            event = existingEventOpt.get();
            log.debug("Updating existing event: {}", eventDto.getId());
        } else {
            event = new EventEntity();
            event.setUser(user);
            event.setExternalId(eventDto.getId());
            log.debug("Creating new event: {}", eventDto.getId());
        }

        // Update event fields
        event.setTitle(eventDto.getTitle());
        event.setStartAt(parseDateTime(eventDto.getStart()));
        event.setEndAt(parseDateTime(eventDto.getEnd()));
        event.setChangedAt(parseDateTime(eventDto.getChanged()));
        event.setSyncedAt(LocalDateTime.now());
        event.setDeleted(false);

        event = eventRepository.save(event);

        // Update attendees
        updateEventAttendees(event, eventDto);
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
