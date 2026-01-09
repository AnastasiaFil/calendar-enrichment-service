package rs.usergems.calendar.enrichment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.usergems.calendar.enrichment.dto.EmailContentJson;
import rs.usergems.calendar.enrichment.entity.EmailEntity;
import rs.usergems.calendar.enrichment.entity.EventAttendeeEntity;
import rs.usergems.calendar.enrichment.entity.EventEntity;
import rs.usergems.calendar.enrichment.entity.UserEntity;
import rs.usergems.calendar.enrichment.repository.EmailRepository;
import rs.usergems.calendar.enrichment.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MorningEmailServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CalendarSyncService calendarSyncService;

    @Mock
    private PersonEnrichmentService personEnrichmentService;

    @Mock
    private EmailBuilderService emailBuilderService;

    @Mock
    private EmailRepository emailRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private MorningEmailService morningEmailService;

    private UserEntity testUser;
    private EventEntity testEvent;
    private EmailContentJson testEmailContent;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setId(1L);
        testUser.setEmail("stephan@usergems.com");

        testEvent = new EventEntity();
        testEvent.setId(1L);
        testEvent.setTitle("Team Sync");
        testEvent.setStartAt(LocalDateTime.now().withHour(10).withMinute(0));
        testEvent.setEndAt(LocalDateTime.now().withHour(11).withMinute(0));
        testEvent.setAttendees(new ArrayList<>());

        testEmailContent = new EmailContentJson();
        testEmailContent.setRecipient("stephan@usergems.com");
        testEmailContent.setDate(LocalDate.now());
    }

    @Test
    void generateEmailForUser_WithEvents_ShouldGenerateEmail() throws Exception {
        EventAttendeeEntity acceptedExternal = new EventAttendeeEntity();
        acceptedExternal.setEmail("john@algolia.com");
        acceptedExternal.setStatus("accepted");

        EventAttendeeEntity declinedExternal = new EventAttendeeEntity();
        declinedExternal.setEmail("jane@algolia.com");
        declinedExternal.setStatus("declined");

        testEvent.setAttendees(Arrays.asList(acceptedExternal, declinedExternal));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(calendarSyncService.getTodayEvents(1L)).thenReturn(Collections.singletonList(testEvent));
        when(emailBuilderService.buildJson(eq(1L), anyList())).thenReturn(testEmailContent);
        when(emailBuilderService.buildHtml(testEmailContent)).thenReturn("<html>Test</html>");

        boolean result = morningEmailService.generateEmailForUser(1L);

        assertTrue(result);
        verify(calendarSyncService).incrementalSync(1L);
        verify(calendarSyncService).getTodayEvents(1L);
        verify(personEnrichmentService).enrichPerson("john@algolia.com");
        verify(personEnrichmentService, never()).enrichPerson("jane@algolia.com");
        verify(emailBuilderService).buildJson(eq(1L), anyList());
        verify(emailBuilderService).buildHtml(testEmailContent);

        ArgumentCaptor<EmailEntity> emailCaptor = ArgumentCaptor.forClass(EmailEntity.class);
        verify(emailRepository).save(emailCaptor.capture());

        EmailEntity savedEmail = emailCaptor.getValue();
        assertEquals(testUser, savedEmail.getUser());
        assertEquals(LocalDate.now(), savedEmail.getEmailDate());
        assertNotNull(savedEmail.getContentJson());
        assertEquals("<html>Test</html>", savedEmail.getContentHtml());
    }

    @Test
    void generateEmailForUser_WithNoEvents_ShouldReturnFalse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(calendarSyncService.getTodayEvents(1L)).thenReturn(Collections.emptyList());

        boolean result = morningEmailService.generateEmailForUser(1L);

        assertFalse(result);
        verify(calendarSyncService).incrementalSync(1L);
        verify(calendarSyncService).getTodayEvents(1L);
        verify(personEnrichmentService, never()).enrichPerson(anyString());
        verify(emailBuilderService, never()).buildJson(anyLong(), anyList());
        verify(emailRepository, never()).save(any());
    }

    @Test
    void generateEmailForUser_WithInternalAttendeesOnly_ShouldNotEnrich() throws Exception {
        EventAttendeeEntity internal1 = new EventAttendeeEntity();
        internal1.setEmail("alice@usergems.com");
        internal1.setStatus("accepted");

        EventAttendeeEntity internal2 = new EventAttendeeEntity();
        internal2.setEmail("bob@usergems.com");
        internal2.setStatus("accepted");

        testEvent.setAttendees(Arrays.asList(internal1, internal2));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(calendarSyncService.getTodayEvents(1L)).thenReturn(Collections.singletonList(testEvent));
        when(emailBuilderService.buildJson(eq(1L), anyList())).thenReturn(testEmailContent);
        when(emailBuilderService.buildHtml(testEmailContent)).thenReturn("<html>Test</html>");

        boolean result = morningEmailService.generateEmailForUser(1L);

        assertTrue(result);
        verify(personEnrichmentService, never()).enrichPerson(anyString());
    }

    @Test
    void generateEmailForUser_WithMixedAttendees_ShouldEnrichOnlyAcceptedExternal() throws Exception {
        EventAttendeeEntity currentUser = new EventAttendeeEntity();
        currentUser.setEmail("stephan@usergems.com");
        currentUser.setStatus("accepted");

        EventAttendeeEntity internalAttendee = new EventAttendeeEntity();
        internalAttendee.setEmail("alice@usergems.com");
        internalAttendee.setStatus("accepted");

        EventAttendeeEntity acceptedExternal = new EventAttendeeEntity();
        acceptedExternal.setEmail("john@algolia.com");
        acceptedExternal.setStatus("accepted");

        EventAttendeeEntity tentativeExternal = new EventAttendeeEntity();
        tentativeExternal.setEmail("sarah@algolia.com");
        tentativeExternal.setStatus("tentative");

        EventAttendeeEntity declinedExternal = new EventAttendeeEntity();
        declinedExternal.setEmail("jane@algolia.com");
        declinedExternal.setStatus("declined");

        testEvent.setAttendees(Arrays.asList(
                currentUser, internalAttendee, acceptedExternal, tentativeExternal, declinedExternal
        ));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(calendarSyncService.getTodayEvents(1L)).thenReturn(Collections.singletonList(testEvent));
        when(emailBuilderService.buildJson(eq(1L), anyList())).thenReturn(testEmailContent);
        when(emailBuilderService.buildHtml(testEmailContent)).thenReturn("<html>Test</html>");

        boolean result = morningEmailService.generateEmailForUser(1L);

        assertTrue(result);
        verify(personEnrichmentService).enrichPerson("john@algolia.com");
        verify(personEnrichmentService).enrichPerson("sarah@algolia.com");
        verify(personEnrichmentService, never()).enrichPerson("stephan@usergems.com");
        verify(personEnrichmentService, never()).enrichPerson("alice@usergems.com");
        verify(personEnrichmentService, never()).enrichPerson("jane@algolia.com");
    }

    @Test
    void generateEmailForUser_WhenEnrichmentFails_ShouldContinueAndUseCache() throws Exception {
        EventAttendeeEntity external = new EventAttendeeEntity();
        external.setEmail("john@algolia.com");
        external.setStatus("accepted");

        testEvent.setAttendees(Collections.singletonList(external));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(calendarSyncService.getTodayEvents(1L)).thenReturn(Collections.singletonList(testEvent));
        when(personEnrichmentService.enrichPerson("john@algolia.com"))
                .thenThrow(new RuntimeException("API error"));
        when(emailBuilderService.buildJson(eq(1L), anyList())).thenReturn(testEmailContent);
        when(emailBuilderService.buildHtml(testEmailContent)).thenReturn("<html>Test</html>");

        boolean result = morningEmailService.generateEmailForUser(1L);

        assertTrue(result);
        verify(personEnrichmentService).enrichPerson("john@algolia.com");
        verify(emailBuilderService).buildJson(eq(1L), anyList());
        verify(emailRepository).save(any());
    }

    @Test
    void generateEmailForUser_WithUserNotFound_ShouldThrowException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            morningEmailService.generateEmailForUser(999L);
        });

        verify(calendarSyncService, never()).incrementalSync(anyLong());
        verify(emailRepository, never()).save(any());
    }

    @Test
    void generateEmailsForAllUsers_ShouldProcessAllUsers() {
        UserEntity user1 = new UserEntity();
        user1.setId(1L);
        user1.setEmail("user1@usergems.com");

        UserEntity user2 = new UserEntity();
        user2.setId(2L);
        user2.setEmail("user2@usergems.com");

        UserEntity user3 = new UserEntity();
        user3.setId(3L);
        user3.setEmail("user3@usergems.com");

        when(userRepository.findAll()).thenReturn(Arrays.asList(user1, user2, user3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user3));

        when(calendarSyncService.getTodayEvents(1L)).thenReturn(Collections.singletonList(testEvent));
        when(calendarSyncService.getTodayEvents(2L)).thenReturn(Collections.emptyList());
        when(calendarSyncService.getTodayEvents(3L)).thenThrow(new RuntimeException("Sync error"));

        when(emailBuilderService.buildJson(eq(1L), anyList())).thenReturn(testEmailContent);
        when(emailBuilderService.buildHtml(any())).thenReturn("<html>Test</html>");

        morningEmailService.generateEmailsForAllUsers();

        verify(calendarSyncService).incrementalSync(1L);
        verify(calendarSyncService).incrementalSync(2L);
        verify(calendarSyncService).incrementalSync(3L);
        verify(emailRepository, times(1)).save(any());
    }

    @Test
    void enrichAttendees_ShouldSkipDeclinedStatus() throws Exception {
        EventAttendeeEntity accepted = new EventAttendeeEntity();
        accepted.setEmail("accepted@example.com");
        accepted.setStatus("accepted");

        EventAttendeeEntity declined = new EventAttendeeEntity();
        declined.setEmail("declined@example.com");
        declined.setStatus("declined");

        EventAttendeeEntity tentative = new EventAttendeeEntity();
        tentative.setEmail("tentative@example.com");
        tentative.setStatus("tentative");

        testEvent.setAttendees(Arrays.asList(accepted, declined, tentative));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(calendarSyncService.getTodayEvents(1L)).thenReturn(Collections.singletonList(testEvent));
        when(emailBuilderService.buildJson(eq(1L), anyList())).thenReturn(testEmailContent);
        when(emailBuilderService.buildHtml(testEmailContent)).thenReturn("<html>Test</html>");

        morningEmailService.generateEmailForUser(1L);

        verify(personEnrichmentService).enrichPerson("accepted@example.com");
        verify(personEnrichmentService).enrichPerson("tentative@example.com");
        verify(personEnrichmentService, never()).enrichPerson("declined@example.com");
    }
}
