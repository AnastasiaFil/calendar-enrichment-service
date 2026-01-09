package rs.usergems.calendar.enrichment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.usergems.calendar.enrichment.client.PersonFeignClient;
import rs.usergems.calendar.enrichment.config.PersonApiProperties;
import rs.usergems.calendar.enrichment.dto.CompanyDto;
import rs.usergems.calendar.enrichment.dto.PersonDto;
import rs.usergems.calendar.enrichment.entity.PersonEntity;
import rs.usergems.calendar.enrichment.repository.PersonRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonEnrichmentServiceTest {

    @Mock
    private PersonFeignClient personFeignClient;

    @Mock
    private PersonApiProperties personApiProperties;

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private PersonEnrichmentService personEnrichmentService;

    private static final String EXTERNAL_EMAIL = "john.doe@example.com";
    private static final String INTERNAL_EMAIL = "employee@usergems.com";
    private static final String TEST_API_KEY = "test-api-key-123";
    private static final String BEARER_TOKEN = "Bearer " + TEST_API_KEY;

    @BeforeEach
    void setUp() {
        lenient().when(personApiProperties.getApiKey()).thenReturn(TEST_API_KEY);
    }

    @Test
    void enrichPerson_NullEmail_ReturnsNull() {
        PersonEntity result = personEnrichmentService.enrichPerson(null);

        assertNull(result);
        verify(personRepository, never()).findById(anyString());
        verify(personFeignClient, never()).getPersonByEmail(anyString(), anyString());
    }

    @Test
    void enrichPerson_EmptyEmail_ReturnsNull() {
        PersonEntity result = personEnrichmentService.enrichPerson("");

        assertNull(result);
        verify(personRepository, never()).findById(anyString());
        verify(personFeignClient, never()).getPersonByEmail(anyString(), anyString());
    }

    @Test
    void enrichPerson_InternalUser_ReturnsNull() {
        PersonEntity result = personEnrichmentService.enrichPerson(INTERNAL_EMAIL);

        assertNull(result);
        verify(personRepository, never()).findById(anyString());
        verify(personFeignClient, never()).getPersonByEmail(anyString(), anyString());
    }

    @Test
    void enrichPerson_FreshCacheHit_ReturnsCachedData() {
        PersonEntity cachedPerson = createCachedPerson(EXTERNAL_EMAIL, LocalDateTime.now().minusDays(10));
        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.of(cachedPerson));

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNotNull(result);
        assertEquals(EXTERNAL_EMAIL, result.getEmail());
        verify(personRepository, times(1)).findById(EXTERNAL_EMAIL);
        verify(personFeignClient, never()).getPersonByEmail(anyString(), anyString());
        verify(personRepository, never()).save(any());
    }

    @Test
    void enrichPerson_StaleCacheHit_RefreshesFromApi() {
        PersonEntity stalePerson = createCachedPerson(EXTERNAL_EMAIL, LocalDateTime.now().minusDays(35));
        PersonDto apiResponse = createPersonDto();
        PersonEntity savedPerson = createCachedPerson(EXTERNAL_EMAIL, LocalDateTime.now());

        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.of(stalePerson));
        when(personFeignClient.getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL)).thenReturn(apiResponse);
        when(personRepository.save(any(PersonEntity.class))).thenReturn(savedPerson);

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNotNull(result);
        assertEquals(EXTERNAL_EMAIL, result.getEmail());
        verify(personRepository, times(1)).findById(EXTERNAL_EMAIL);
        verify(personFeignClient, times(1)).getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL);
        verify(personRepository, times(1)).save(any(PersonEntity.class));
    }

    @Test
    void enrichPerson_CacheMiss_FetchesFromApi() {
        PersonDto apiResponse = createPersonDto();
        PersonEntity savedPerson = createCachedPerson(EXTERNAL_EMAIL, LocalDateTime.now());

        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.empty());
        when(personFeignClient.getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL)).thenReturn(apiResponse);
        when(personRepository.save(any(PersonEntity.class))).thenReturn(savedPerson);

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNotNull(result);
        assertEquals(EXTERNAL_EMAIL, result.getEmail());
        verify(personRepository, times(1)).findById(EXTERNAL_EMAIL);
        verify(personFeignClient, times(1)).getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL);
        verify(personRepository, times(1)).save(any(PersonEntity.class));
    }

    @Test
    void enrichPerson_ApiFailsWithStaleCache_ReturnsStaleCache() {
        PersonEntity stalePerson = createCachedPerson(EXTERNAL_EMAIL, LocalDateTime.now().minusDays(35));

        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.of(stalePerson));
        when(personFeignClient.getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL))
                .thenThrow(new RuntimeException("API error"));

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNotNull(result);
        assertEquals(EXTERNAL_EMAIL, result.getEmail());
        assertEquals(stalePerson, result);
        verify(personRepository, times(1)).findById(EXTERNAL_EMAIL);
        verify(personFeignClient, times(1)).getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL);
        verify(personRepository, never()).save(any());
    }

    @Test
    void enrichPerson_ApiFailsWithoutCache_ReturnsNull() {
        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.empty());
        when(personFeignClient.getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL))
                .thenThrow(new RuntimeException("API error"));

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNull(result);
        verify(personRepository, times(1)).findById(EXTERNAL_EMAIL);
        verify(personFeignClient, times(1)).getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL);
        verify(personRepository, never()).save(any());
    }

    @Test
    void enrichPerson_CacheWithNullFetchedAt_RefreshesFromApi() {
        PersonEntity cachedPerson = createCachedPerson(EXTERNAL_EMAIL, null);
        PersonDto apiResponse = createPersonDto();
        PersonEntity savedPerson = createCachedPerson(EXTERNAL_EMAIL, LocalDateTime.now());

        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.of(cachedPerson));
        when(personFeignClient.getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL)).thenReturn(apiResponse);
        when(personRepository.save(any(PersonEntity.class))).thenReturn(savedPerson);

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNotNull(result);
        verify(personFeignClient, times(1)).getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL);
        verify(personRepository, times(1)).save(any(PersonEntity.class));
    }

    @Test
    void enrichPerson_Exactly30DaysOld_RefreshesFromApi() {
        PersonEntity cachedPerson = createCachedPerson(EXTERNAL_EMAIL, LocalDateTime.now().minusDays(30));
        PersonDto apiResponse = createPersonDto();
        PersonEntity savedPerson = createCachedPerson(EXTERNAL_EMAIL, LocalDateTime.now());

        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.of(cachedPerson));
        when(personFeignClient.getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL)).thenReturn(apiResponse);
        when(personRepository.save(any(PersonEntity.class))).thenReturn(savedPerson);

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNotNull(result);
        verify(personFeignClient, times(1)).getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL);
        verify(personRepository, times(1)).save(any(PersonEntity.class));
    }

    @Test
    void enrichPerson_MapsAllFieldsCorrectly() {
        PersonDto apiResponse = createPersonDto();
        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.empty());
        when(personFeignClient.getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL)).thenReturn(apiResponse);
        when(personRepository.save(any(PersonEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNotNull(result);
        assertEquals(EXTERNAL_EMAIL, result.getEmail());
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals("https://example.com/avatar.jpg", result.getAvatarUrl());
        assertEquals("Software Engineer", result.getTitle());
        assertEquals("https://linkedin.com/in/johndoe", result.getLinkedinUrl());
        assertEquals("Acme Corp", result.getCompanyName());
        assertEquals("https://linkedin.com/company/acme", result.getCompanyLinkedin());
        assertEquals(500, result.getCompanyEmployees());
        assertNotNull(result.getFetchedAt());
    }

    @Test
    void enrichPerson_ApiResponseWithNullCompany_MapsCorrectly() {
        PersonDto apiResponse = createPersonDto();
        apiResponse.setCompany(null);

        when(personRepository.findById(EXTERNAL_EMAIL)).thenReturn(Optional.empty());
        when(personFeignClient.getPersonByEmail(BEARER_TOKEN, EXTERNAL_EMAIL)).thenReturn(apiResponse);
        when(personRepository.save(any(PersonEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersonEntity result = personEnrichmentService.enrichPerson(EXTERNAL_EMAIL);

        assertNotNull(result);
        assertEquals(EXTERNAL_EMAIL, result.getEmail());
        assertNull(result.getCompanyName());
        assertNull(result.getCompanyLinkedin());
        assertNull(result.getCompanyEmployees());
    }

    @Test
    void enrichPerson_CaseInsensitiveInternalDomain() {
        String mixedCaseEmail = "Employee@UserGems.Com";

        PersonEntity result = personEnrichmentService.enrichPerson(mixedCaseEmail);

        assertNull(result);
        verify(personRepository, never()).findById(anyString());
        verify(personFeignClient, never()).getPersonByEmail(anyString(), anyString());
    }

    private PersonEntity createCachedPerson(String email, LocalDateTime fetchedAt) {
        PersonEntity person = new PersonEntity();
        person.setEmail(email);
        person.setFirstName("John");
        person.setLastName("Doe");
        person.setAvatarUrl("https://example.com/avatar.jpg");
        person.setTitle("Software Engineer");
        person.setLinkedinUrl("https://linkedin.com/in/johndoe");
        person.setCompanyName("Acme Corp");
        person.setCompanyLinkedin("https://linkedin.com/company/acme");
        person.setCompanyEmployees(500);
        person.setFetchedAt(fetchedAt);
        return person;
    }

    private PersonDto createPersonDto() {
        PersonDto dto = new PersonDto();
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setAvatar("https://example.com/avatar.jpg");
        dto.setTitle("Software Engineer");
        dto.setLinkedinUrl("https://linkedin.com/in/johndoe");

        CompanyDto company = new CompanyDto();
        company.setName("Acme Corp");
        company.setLinkedinUrl("https://linkedin.com/company/acme");
        company.setEmployees(500);
        dto.setCompany(company);

        return dto;
    }
}
