package rs.usergems.calendar.enrichment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.usergems.calendar.enrichment.client.calendar.CalendarFeignClient;
import rs.usergems.calendar.enrichment.config.CalendarApiProperties;
import rs.usergems.calendar.enrichment.dto.CalendarApiResponse;
import rs.usergems.calendar.enrichment.dto.CalendarEventDto;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarApiService {

    private final CalendarFeignClient feignClient;
    private final CalendarApiProperties properties;

    /**
     * Fetches all calendar events for a user with pagination handling
     *
     * @param userEmail the user's email
     * @return list of all calendar events across all pages
     */
    public List<CalendarEventDto> fetchAllEvents(String userEmail) {
        List<CalendarEventDto> allEvents = new ArrayList<>();
        int currentPage = 1;
        boolean hasMorePages = true;

        while (hasMorePages) {
            log.info("Fetching calendar events for user {} - page {}", userEmail, currentPage);

            CalendarApiResponse response = fetchEventsPage(userEmail, currentPage);

            if (response != null && response.getData() != null) {
                allEvents.addAll(response.getData());

                // Check if there are more pages
                int totalPages = (int) Math.ceil((double) response.getTotal() / response.getPerPage());
                hasMorePages = currentPage < totalPages;
                currentPage++;
            } else {
                hasMorePages = false;
            }
        }

        log.info("Fetched total {} events for user {}", allEvents.size(), userEmail);
        return allEvents;
    }

    /**
     * Fetches a single page of calendar events
     *
     * @param userEmail the user's email
     * @param page      the page number (1-based)
     * @return calendar API response for the specified page
     */
    public CalendarApiResponse fetchEventsPage(String userEmail, int page) {
        String apiKey = properties.getApiKeyForEmail(userEmail);

        if (apiKey == null) {
            log.warn("No API key found for user: {}", userEmail);
            return null;
        }

        try {
            return feignClient.getEvents("Bearer " + apiKey, page, userEmail);
        } catch (Exception e) {
            log.error("Error fetching calendar events for user {} on page {}: {}",
                    userEmail, page, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches only events that changed after a specific timestamp
     *
     * @param userEmail    the user's email
     * @param changedAfter ISO timestamp to filter events
     * @return list of events changed after the specified time
     */
    public List<CalendarEventDto> fetchChangedEvents(String userEmail, String changedAfter) {
        List<CalendarEventDto> allEvents = fetchAllEvents(userEmail);

        // Events are already sorted by changed date from the API
        // Filter events that were changed after the specified timestamp
        return allEvents.stream()
                .filter(event -> event.getChanged() != null && event.getChanged().compareTo(changedAfter) > 0)
                .toList();
    }
}
