package rs.usergems.calendar.enrichment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.usergems.calendar.enrichment.dto.EmailContentJson;
import rs.usergems.calendar.enrichment.dto.MeetingHistory;
import rs.usergems.calendar.enrichment.entity.EventAttendeeEntity;
import rs.usergems.calendar.enrichment.entity.EventEntity;
import rs.usergems.calendar.enrichment.entity.PersonEntity;
import rs.usergems.calendar.enrichment.entity.UserEntity;
import rs.usergems.calendar.enrichment.repository.EventRepository;
import rs.usergems.calendar.enrichment.repository.PersonRepository;
import rs.usergems.calendar.enrichment.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailBuilderServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EmailBuilderService emailBuilderService;

    private UserEntity testUser;
    private EventEntity testEvent;
    private PersonEntity testPerson;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setId(1L);
        testUser.setEmail("stephan@usergems.com");
        testUser.setTimezone("America/Los_Angeles");

        testEvent = new EventEntity();
        testEvent.setId(1L);
        testEvent.setTitle("UserGems x Algolia");
        testEvent.setStartAt(LocalDateTime.of(2022, 7, 1, 9, 30));
        testEvent.setEndAt(LocalDateTime.of(2022, 7, 1, 10, 0));
        testEvent.setDeleted(false);

        EventAttendeeEntity organizer = new EventAttendeeEntity();
        organizer.setEmail("stephan@usergems.com");
        organizer.setStatus("accepted");

        EventAttendeeEntity colleague = new EventAttendeeEntity();
        colleague.setEmail("joss@usergems.com");
        colleague.setStatus("accepted");

        EventAttendeeEntity external = new EventAttendeeEntity();
        external.setEmail("demi@algolia.com");
        external.setStatus("accepted");

        testEvent.setAttendees(Arrays.asList(organizer, colleague, external));

        testPerson = new PersonEntity();
        testPerson.setEmail("demi@algolia.com");
        testPerson.setFirstName("Demi");
        testPerson.setLastName("Malnar");
        testPerson.setTitle("GTM Chief of Staff");
        testPerson.setLinkedinUrl("https://linkedin.com/in/demimalnar");
        testPerson.setAvatarUrl("https://example.com/avatar.jpg");
        testPerson.setCompanyName("Algolia");
        testPerson.setCompanyLinkedin("https://linkedin.com/company/algolia");
        testPerson.setCompanyEmployees(700);
    }

    @Test
    void buildJson_shouldCreateEmailContentWithEnrichedData() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(personRepository.findById("demi@algolia.com")).thenReturn(Optional.of(testPerson));
        when(eventRepository.countMeetingsBetween("stephan@usergems.com", "demi@algolia.com")).thenReturn(12);
        when(eventRepository.findColleagueMeetingCounts("stephan@usergems.com", "demi@algolia.com"))
                .thenReturn(Arrays.asList(
                        new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount("blaise@usergems.com", 4),
                        new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount("christian@usergems.com", 1)
                ));

        // When
        EmailContentJson result = emailBuilderService.buildJson(1L, Collections.singletonList(testEvent));

        // Then
        assertNotNull(result);
        assertEquals("stephan@usergems.com", result.getRecipient());
        assertEquals(1, result.getMeetings().size());

        var meeting = result.getMeetings().get(0);
        assertEquals("UserGems x Algolia", meeting.getTitle());
        assertEquals("09:30", meeting.getStart());
        assertEquals("10:00", meeting.getEnd());
        assertEquals(30, meeting.getDurationMin());

        assertEquals(1, meeting.getInternalAttendees().size());
        assertTrue(meeting.getInternalAttendees().contains("joss@usergems.com"));

        assertEquals(1, meeting.getExternalAttendees().size());
        var attendee = meeting.getExternalAttendees().get(0);
        assertEquals("demi@algolia.com", attendee.getEmail());
        assertEquals("Demi Malnar", attendee.getName());
        assertEquals("GTM Chief of Staff", attendee.getTitle());
        assertEquals(12, attendee.getMeetingCount());
        assertEquals(2, attendee.getMetWithColleagues().size());

        assertNotNull(meeting.getCompany());
        assertEquals("Algolia", meeting.getCompany().getName());
        assertEquals(700, meeting.getCompany().getEmployees());
    }

    @Test
    void countMeetingHistory_shouldReturnCorrectCounts() {
        // Given
        when(eventRepository.countMeetingsBetween("stephan@usergems.com", "demi@algolia.com")).thenReturn(12);
        when(eventRepository.findColleagueMeetingCounts("stephan@usergems.com", "demi@algolia.com"))
                .thenReturn(Arrays.asList(
                        new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount("blaise@usergems.com", 4),
                        new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount("christian@usergems.com", 1)
                ));

        // When
        MeetingHistory result = emailBuilderService.countMeetingHistory("stephan@usergems.com", "demi@algolia.com");

        // Then
        assertNotNull(result);
        assertEquals(12, result.getTotalCount());
        assertEquals(2, result.getColleagueCounts().size());
        assertEquals(4, result.getColleagueCounts().get("blaise@usergems.com"));
        assertEquals(1, result.getColleagueCounts().get("christian@usergems.com"));
    }

    @Test
    void buildJson_shouldHandleEventWithoutEnrichedPerson() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(personRepository.findById("demi@algolia.com")).thenReturn(Optional.empty());
        when(eventRepository.countMeetingsBetween(anyString(), anyString())).thenReturn(0);
        when(eventRepository.findColleagueMeetingCounts(anyString(), anyString())).thenReturn(Collections.emptyList());

        // When
        EmailContentJson result = emailBuilderService.buildJson(1L, Collections.singletonList(testEvent));

        // Then
        assertNotNull(result);
        assertEquals(1, result.getMeetings().size());
        var attendee = result.getMeetings().get(0).getExternalAttendees().get(0);
        assertEquals("demi@algolia.com", attendee.getEmail());
        assertNull(attendee.getName());
        assertNull(attendee.getTitle());
    }
}
