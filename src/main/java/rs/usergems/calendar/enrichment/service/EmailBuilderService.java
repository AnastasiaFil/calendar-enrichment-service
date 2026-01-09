package rs.usergems.calendar.enrichment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.usergems.calendar.enrichment.dto.AttendeeDto;
import rs.usergems.calendar.enrichment.dto.ColleagueMeetingCount;
import rs.usergems.calendar.enrichment.dto.CompanyDto;
import rs.usergems.calendar.enrichment.dto.EmailContentJson;
import rs.usergems.calendar.enrichment.dto.MeetingDto;
import rs.usergems.calendar.enrichment.dto.MeetingHistory;
import rs.usergems.calendar.enrichment.entity.EventAttendeeEntity;
import rs.usergems.calendar.enrichment.entity.EventEntity;
import rs.usergems.calendar.enrichment.entity.PersonEntity;
import rs.usergems.calendar.enrichment.entity.UserEntity;
import rs.usergems.calendar.enrichment.repository.EventRepository;
import rs.usergems.calendar.enrichment.repository.PersonRepository;
import rs.usergems.calendar.enrichment.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailBuilderService {

    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final EventRepository eventRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String USERGEMS_DOMAIN = "@usergems.com";

    /**
     * Builds JSON email content with enriched meeting data
     *
     * @param userId user ID for whom to build email
     * @param events list of events to include in email
     * @return structured JSON email content
     */
    @Transactional(readOnly = true)
    public EmailContentJson buildJson(Long userId, List<EventEntity> events) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<MeetingDto> meetings = new ArrayList<>();

        for (EventEntity event : events) {
            MeetingDto meeting = new MeetingDto();
            meeting.setTitle(event.getTitle());
            meeting.setStart(event.getStartAt().format(TIME_FORMATTER));
            meeting.setEnd(event.getEndAt().format(TIME_FORMATTER));
            meeting.setDurationMin(calculateDuration(event));

            // Separate internal and external attendees
            List<String> internalAttendees = new ArrayList<>();
            List<EventAttendeeEntity> externalAttendees = new ArrayList<>();

            for (EventAttendeeEntity attendee : event.getAttendees()) {
                if (attendee.getEmail().endsWith(USERGEMS_DOMAIN) && !attendee.getEmail().equals(user.getEmail())) {
                    internalAttendees.add(attendee.getEmail());
                } else if (!attendee.getEmail().equals(user.getEmail())) {
                    externalAttendees.add(attendee);
                }
            }

            meeting.setInternalAttendees(internalAttendees);

            // Enrich external attendees
            List<AttendeeDto> enrichedAttendees = new ArrayList<>();
            String companyName = null;
            String companyLinkedin = null;
            Integer companyEmployees = null;

            for (EventAttendeeEntity attendee : externalAttendees) {
                PersonEntity person = personRepository.findById(attendee.getEmail()).orElse(null);

                AttendeeDto dto = new AttendeeDto();
                dto.setEmail(attendee.getEmail());
                dto.setStatus(attendee.getStatus());

                if (person != null) {
                    dto.setName(buildFullName(person.getFirstName(), person.getLastName()));
                    dto.setTitle(person.getTitle());
                    dto.setLinkedin(person.getLinkedinUrl());
                    dto.setAvatar(person.getAvatarUrl());

                    // Use first person's company info for the meeting
                    if (companyName == null && person.getCompanyName() != null) {
                        companyName = person.getCompanyName();
                        companyLinkedin = person.getCompanyLinkedin();
                        companyEmployees = person.getCompanyEmployees();
                    }
                }

                // Add meeting history
                MeetingHistory history = countMeetingHistory(user.getEmail(), attendee.getEmail());
                dto.setMeetingCount(history.getTotalCount());
                dto.setMetWithColleagues(history.getColleagueCounts());

                enrichedAttendees.add(dto);
            }

            meeting.setExternalAttendees(enrichedAttendees);
            meeting.setCompany(buildCompanyInfo(companyName, companyLinkedin, companyEmployees));
            meetings.add(meeting);
        }

        return new EmailContentJson(user.getEmail(), LocalDate.now(), meetings);
    }

    /**
     * Counts meeting history between user and contact
     *
     * @param userEmail    user's email
     * @param contactEmail contact's email
     * @return meeting history with total count and colleague counts
     */
    @Transactional(readOnly = true)
    public MeetingHistory countMeetingHistory(String userEmail, String contactEmail) {
        // Count total meetings with this contact
        int totalCount = eventRepository.countMeetingsBetween(userEmail, contactEmail);

        // Get colleague meeting counts
        List<ColleagueMeetingCount> results = eventRepository.findColleagueMeetingCounts(userEmail, contactEmail);

        Map<String, Integer> colleagueCounts = new HashMap<>();
        for (ColleagueMeetingCount result : results) {
            colleagueCounts.put(result.getColleagueEmail(), result.getMeetingCount());
        }

        return new MeetingHistory(totalCount, colleagueCounts);
    }

    private int calculateDuration(EventEntity event) {
        return (int) Duration.between(event.getStartAt(), event.getEndAt()).toMinutes();
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private CompanyDto buildCompanyInfo(String name, String linkedinUrl, Integer employees) {
        if (name == null) {
            return null;
        }

        CompanyDto company = new CompanyDto();
        company.setName(name);
        company.setLinkedinUrl(linkedinUrl);
        company.setEmployees(employees);
        return company;
    }
}
