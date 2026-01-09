package rs.usergems.calendar.enrichment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.usergems.calendar.enrichment.client.calendar.CalendarFeignClient;
import rs.usergems.calendar.enrichment.config.CalendarApiProperties;
import rs.usergems.calendar.enrichment.dto.CalendarApiResponse;
import rs.usergems.calendar.enrichment.dto.CalendarEventDto;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarApiServiceTest {

    @Mock
    private CalendarFeignClient feignClient;

    @Mock
    private CalendarApiProperties properties;

    @InjectMocks
    private CalendarApiService calendarApiService;

    private static final String TEST_EMAIL = "test@usergems.com";
    private static final String TEST_API_KEY = "test-api-key-123";
    private static final String BEARER_TOKEN = "Bearer " + TEST_API_KEY;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getApiKeyForEmail(TEST_EMAIL)).thenReturn(TEST_API_KEY);
    }

    @Test
    void fetchEventsPage_Success() {
        CalendarApiResponse mockResponse = createMockResponse(1, 10, 1, 2);
        when(feignClient.getEvents(BEARER_TOKEN, 1, TEST_EMAIL)).thenReturn(mockResponse);

        CalendarApiResponse result = calendarApiService.fetchEventsPage(TEST_EMAIL, 1);

        assertNotNull(result);
        assertEquals(10, result.getTotal());
        assertEquals(1, result.getData().size());
        verify(feignClient, times(1)).getEvents(BEARER_TOKEN, 1, TEST_EMAIL);
    }

    @Test
    void fetchEventsPage_NoApiKey_ReturnsNull() {
        String unknownEmail = "unknown@example.com";
        when(properties.getApiKeyForEmail(unknownEmail)).thenReturn(null);

        CalendarApiResponse result = calendarApiService.fetchEventsPage(unknownEmail, 1);

        assertNull(result);
        verify(feignClient, never()).getEvents(anyString(), anyInt(), anyString());
    }

    @Test
    void fetchAllEvents_SinglePage() {
        CalendarApiResponse mockResponse = createMockResponse(2, 2, 1, 10);
        when(feignClient.getEvents(BEARER_TOKEN, 1, TEST_EMAIL)).thenReturn(mockResponse);

        List<CalendarEventDto> result = calendarApiService.fetchAllEvents(TEST_EMAIL);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(feignClient, times(1)).getEvents(anyString(), anyInt(), anyString());
    }

    @Test
    void fetchAllEvents_MultiplePages() {
        CalendarApiResponse page1 = createMockResponse(3, 15, 1, 10);
        CalendarApiResponse page2 = createMockResponse(2, 15, 2, 10);

        when(feignClient.getEvents(BEARER_TOKEN, 1, TEST_EMAIL)).thenReturn(page1);
        when(feignClient.getEvents(BEARER_TOKEN, 2, TEST_EMAIL)).thenReturn(page2);

        List<CalendarEventDto> result = calendarApiService.fetchAllEvents(TEST_EMAIL);

        assertNotNull(result);
        assertEquals(5, result.size());
        verify(feignClient, times(2)).getEvents(anyString(), anyInt(), anyString());
    }

    private CalendarApiResponse createMockResponse(int dataSize, int total, int currentPage, int perPage) {
        CalendarApiResponse response = new CalendarApiResponse();
        response.setTotal(total);
        response.setPerPage(perPage);
        response.setCurrentPage(currentPage);

        List<CalendarEventDto> events = new ArrayList<>();
        for (int i = 0; i < dataSize; i++) {
            events.add(createEvent((long) i, "2022-06-23 12:32:12"));
        }
        response.setData(events);

        return response;
    }

    private CalendarEventDto createEvent(Long id, String changed) {
        CalendarEventDto event = new CalendarEventDto();
        event.setId(id);
        event.setChanged(changed);
        event.setStart("2022-06-25 10:00:00");
        event.setEnd("2022-06-25 12:00:00");
        event.setTitle("Test Event");
        event.setAccepted(List.of("test@example.com"));
        event.setRejected(List.of());
        return event;
    }
}
