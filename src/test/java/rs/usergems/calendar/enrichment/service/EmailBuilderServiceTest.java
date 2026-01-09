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
import java.util.ArrayList;
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(personRepository.findById("demi@algolia.com")).thenReturn(Optional.of(testPerson));
        when(eventRepository.countMeetingsBetween("stephan@usergems.com", "demi@algolia.com")).thenReturn(12);
        when(eventRepository.findColleagueMeetingCounts("stephan@usergems.com", "demi@algolia.com"))
                .thenReturn(Arrays.asList(
                        new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount("blaise@usergems.com", 4),
                        new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount("christian@usergems.com", 1)
                ));

        EmailContentJson result = emailBuilderService.buildJson(1L, Collections.singletonList(testEvent));

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
        when(eventRepository.countMeetingsBetween("stephan@usergems.com", "demi@algolia.com")).thenReturn(12);
        when(eventRepository.findColleagueMeetingCounts("stephan@usergems.com", "demi@algolia.com"))
                .thenReturn(Arrays.asList(
                        new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount("blaise@usergems.com", 4),
                        new rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount("christian@usergems.com", 1)
                ));

        MeetingHistory result = emailBuilderService.countMeetingHistory("stephan@usergems.com", "demi@algolia.com");

        assertNotNull(result);
        assertEquals(12, result.getTotalCount());
        assertEquals(2, result.getColleagueCounts().size());
        assertEquals(4, result.getColleagueCounts().get("blaise@usergems.com"));
        assertEquals(1, result.getColleagueCounts().get("christian@usergems.com"));
    }

    @Test
    void buildHtml_shouldGenerateValidHtml() {
        EmailContentJson emailContent = new EmailContentJson();
        emailContent.setRecipient("stephan@usergems.com");
        emailContent.setDate(java.time.LocalDate.now());
        emailContent.setMeetings(new ArrayList<>());

        String html = emailBuilderService.buildHtml(emailContent);

        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("USERGEMS"));
        assertTrue(html.contains("Your Morning Update"));
    }

    @Test
    void buildJson_shouldHandleEventWithoutEnrichedPerson() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(personRepository.findById("demi@algolia.com")).thenReturn(Optional.empty());
        when(eventRepository.countMeetingsBetween(anyString(), anyString())).thenReturn(0);
        when(eventRepository.findColleagueMeetingCounts(anyString(), anyString())).thenReturn(Collections.emptyList());

        EmailContentJson result = emailBuilderService.buildJson(1L, Collections.singletonList(testEvent));

        assertNotNull(result);
        assertEquals(1, result.getMeetings().size());
        var attendee = result.getMeetings().get(0).getExternalAttendees().get(0);
        assertEquals("demi@algolia.com", attendee.getEmail());
        assertNull(attendee.getName());
        assertNull(attendee.getTitle());
    }

    @Test
    void buildJson_shouldShowDeclinedAttendeesWithoutEnrichment() {
        PersonEntity acceptedPerson = new PersonEntity();
        acceptedPerson.setEmail("accepted@algolia.com");
        acceptedPerson.setFirstName("John");
        acceptedPerson.setLastName("Accepted");
        acceptedPerson.setTitle("CEO");

        PersonEntity declinedPerson = new PersonEntity();
        declinedPerson.setEmail("declined@algolia.com");
        declinedPerson.setFirstName("Jane");
        declinedPerson.setLastName("Declined");
        declinedPerson.setTitle("CTO");
        declinedPerson.setLinkedinUrl("https://linkedin.com/in/jane-declined");

        EventAttendeeEntity organizer = new EventAttendeeEntity();
        organizer.setEmail("stephan@usergems.com");
        organizer.setStatus("accepted");

        EventAttendeeEntity acceptedExternal = new EventAttendeeEntity();
        acceptedExternal.setEmail("accepted@algolia.com");
        acceptedExternal.setStatus("accepted");

        EventAttendeeEntity declinedExternal = new EventAttendeeEntity();
        declinedExternal.setEmail("declined@algolia.com");
        declinedExternal.setStatus("declined");

        EventAttendeeEntity declinedInternal = new EventAttendeeEntity();
        declinedInternal.setEmail("declined@usergems.com");
        declinedInternal.setStatus("declined");

        testEvent.setAttendees(Arrays.asList(organizer, acceptedExternal, declinedExternal, declinedInternal));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(personRepository.findById("accepted@algolia.com")).thenReturn(Optional.of(acceptedPerson));
        when(personRepository.findById("declined@algolia.com")).thenReturn(Optional.of(declinedPerson));
        when(eventRepository.countMeetingsBetween("stephan@usergems.com", "accepted@algolia.com")).thenReturn(5);
        when(eventRepository.findColleagueMeetingCounts("stephan@usergems.com", "accepted@algolia.com"))
                .thenReturn(Collections.emptyList());
        when(eventRepository.countMeetingsBetween("stephan@usergems.com", "declined@algolia.com")).thenReturn(3);
        when(eventRepository.findColleagueMeetingCounts("stephan@usergems.com", "declined@algolia.com"))
                .thenReturn(Collections.emptyList());

        EmailContentJson result = emailBuilderService.buildJson(1L, Collections.singletonList(testEvent));

        assertNotNull(result);
        assertEquals(1, result.getMeetings().size());
        var meeting = result.getMeetings().get(0);

        assertEquals(1, meeting.getInternalAttendees().size());
        assertTrue(meeting.getInternalAttendees().contains("declined@usergems.com"));

        assertEquals(2, meeting.getExternalAttendees().size());

        var acceptedAttendee = meeting.getExternalAttendees().stream()
                .filter(a -> a.getEmail().equals("accepted@algolia.com"))
                .findFirst().orElse(null);
        assertNotNull(acceptedAttendee);
        assertEquals("John Accepted", acceptedAttendee.getName());
        assertEquals("CEO", acceptedAttendee.getTitle());
        assertEquals(5, acceptedAttendee.getMeetingCount());

        var declinedAttendee = meeting.getExternalAttendees().stream()
                .filter(a -> a.getEmail().equals("declined@algolia.com"))
                .findFirst().orElse(null);
        assertNotNull(declinedAttendee);
        assertEquals("declined@algolia.com", declinedAttendee.getEmail());
        assertEquals("declined", declinedAttendee.getStatus());

        assertEquals("Jane Declined", declinedAttendee.getName());
        assertEquals("CTO", declinedAttendee.getTitle());
        assertEquals("https://linkedin.com/in/jane-declined", declinedAttendee.getLinkedin());
        assertEquals(3, declinedAttendee.getMeetingCount());
    }
}
