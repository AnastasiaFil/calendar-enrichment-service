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
     * Synchronizes calendar events for a user
     *
     * @param userId the user ID
     */
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
