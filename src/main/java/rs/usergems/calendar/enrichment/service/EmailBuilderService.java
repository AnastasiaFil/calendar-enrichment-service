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
import java.util.stream.Collectors;

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
                AttendeeDto dto = new AttendeeDto();
                dto.setEmail(attendee.getEmail());
                dto.setStatus(attendee.getStatus());

                // For all attendees, get existing data from DB
                PersonEntity person = personRepository.findById(attendee.getEmail()).orElse(null);

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
     * Renders HTML from email JSON content
     */
    public String buildHtml(EmailContentJson emailJson) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px; }\n");
        html.append(".container { max-width: 800px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 8px; }\n");
        html.append(".header { text-align: center; margin-bottom: 30px; }\n");
        html.append(".logo { color: #00D4AA; font-size: 24px; font-weight: bold; }\n");
        html.append(".subtitle { color: #00D4AA; font-size: 18px; margin-top: 10px; }\n");
        html.append(".meeting { border-left: 4px solid #00D4AA; padding-left: 20px; margin-bottom: 30px; }\n");
        html.append(".meeting-title { font-size: 18px; font-weight: bold; margin-bottom: 5px; }\n");
        html.append(".meeting-time { color: #00D4AA; margin-bottom: 10px; }\n");
        html.append(".attendee { display: flex; align-items: center; margin-bottom: 15px; padding: 10px; background-color: #f9f9f9; border-radius: 5px; }\n");
        html.append(".attendee-avatar { width: 50px; height: 50px; border-radius: 50%; margin-right: 15px; }\n");
        html.append(".attendee-info { flex: 1; }\n");
        html.append(".attendee-name { font-weight: bold; color: #00D4AA; }\n");
        html.append(".attendee-title { color: #666; font-size: 14px; }\n");
        html.append(".attendee-meta { color: #999; font-size: 12px; margin-top: 5px; }\n");
        html.append(".company { background-color: #f0f0f0; padding: 10px; border-radius: 5px; margin-top: 10px; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<div class='container'>\n");

        html.append("<div class='header'>\n");
        html.append("<div class='logo'>◆ USERGEMS</div>\n");
        html.append("<div class='subtitle'>Your Morning Update</div>\n");
        html.append("</div>\n");

        for (MeetingDto meeting : emailJson.getMeetings()) {
            html.append("<div class='meeting'>\n");
            html.append("<div class='meeting-title'>").append(escapeHtml(meeting.getTitle())).append("</div>\n");
            html.append("<div class='meeting-time'>")
                    .append(meeting.getStart()).append(" - ").append(meeting.getEnd())
                    .append(" | ").append(meeting.getDurationMin()).append(" min</div>\n");

            if (meeting.getInternalAttendees() != null && !meeting.getInternalAttendees().isEmpty()) {
                html.append("<div style='margin: 10px 0; color: #666;'>Joining from UserGems: ")
                        .append(String.join(", ", meeting.getInternalAttendees().stream()
                                .map(email -> email.split("@")[0])
                                .collect(Collectors.toList())))
                        .append("</div>\n");
            }

            if (meeting.getExternalAttendees() != null) {
                for (AttendeeDto attendee : meeting.getExternalAttendees()) {
                    html.append("<div class='attendee'>\n");

                    if (attendee.getAvatar() != null) {
                        html.append("<img class='attendee-avatar' src='")
                                .append(escapeHtml(attendee.getAvatar())).append("' alt='Avatar'>\n");
                    } else {
                        html.append("<div class='attendee-avatar' style='background-color: #ddd;'></div>\n");
                    }

                    html.append("<div class='attendee-info'>\n");

                    if (attendee.getName() != null) {
                        html.append("<div class='attendee-name'>").append(escapeHtml(attendee.getName()));
                        if (attendee.getLinkedin() != null) {
                            html.append(" <a href='").append(escapeHtml(attendee.getLinkedin())).append("'>in</a>");
                        }
                        html.append("</div>\n");
                    } else {
                        html.append("<div class='attendee-name'>").append(escapeHtml(attendee.getEmail())).append("</div>\n");
                    }

                    if (attendee.getTitle() != null) {
                        html.append("<div class='attendee-title'>").append(escapeHtml(attendee.getTitle())).append("</div>\n");
                    }

                    if (attendee.getMeetingCount() != null && attendee.getMeetingCount() > 0) {
                        html.append("<div class='attendee-meta'>");
                        html.append(getOrdinal(attendee.getMeetingCount())).append(" Meeting");

                        if (attendee.getMetWithColleagues() != null && !attendee.getMetWithColleagues().isEmpty()) {
                            html.append(" | Met with ");
                            List<String> colleagues = attendee.getMetWithColleagues().entrySet().stream()
                                    .map(e -> e.getKey().split("@")[0] + " (" + e.getValue() + "x)")
                                    .collect(Collectors.toList());
                            html.append(String.join(", ", colleagues));
                        }

                        html.append("</div>\n");
                    }

                    html.append("</div>\n");
                    html.append("</div>\n");
                }
            }

            if (meeting.getCompany() != null && meeting.getCompany().getName() != null) {
                html.append("<div class='company'>\n");
                html.append("<strong>").append(escapeHtml(meeting.getCompany().getName())).append("</strong>");
                if (meeting.getCompany().getEmployees() != null) {
                    html.append(" | ").append(meeting.getCompany().getEmployees()).append(" employees");
                }
                if (meeting.getCompany().getLinkedinUrl() != null) {
                    html.append(" | <a href='").append(escapeHtml(meeting.getCompany().getLinkedinUrl())).append("'>LinkedIn</a>");
                }
                html.append("\n</div>\n");
            }

            html.append("</div>\n");
        }

        html.append("</div>\n");
        html.append("</body>\n</html>");

        return html.toString();
    }

    /**
     * Counts meeting history between user and contact
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

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String getOrdinal(int number) {
        if (number % 100 >= 11 && number % 100 <= 13) {
            return number + "th";
        }
        return switch (number % 10) {
            case 1 -> number + "st";
            case 2 -> number + "nd";
            case 3 -> number + "rd";
            default -> number + "th";
        };
    }
}
