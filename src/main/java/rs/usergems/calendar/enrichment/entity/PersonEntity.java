package rs.usergems.calendar.enrichment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "persons")
public class PersonEntity {

    @Id
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "company_linkedin", length = 500)
    private String companyLinkedin;

    @Column(name = "company_employees")
    private Integer companyEmployees;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        fetchedAt = LocalDateTime.now();
    }
}
