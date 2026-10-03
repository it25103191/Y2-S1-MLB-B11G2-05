package com.safari.tms.domain;

import com.safari.tms.domain.enums.GuideStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "guides", uniqueConstraints = @UniqueConstraint(name = "uk_guides_license", columnNames = "licenseNumber"))
@Getter
@Setter
@NoArgsConstructor
public class Guide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, length = 40)
    private String licenseNumber;

    @Column(length = 200)
    private String languages;

    @Column(length = 150)
    private String specialization;

    @Column(nullable = false)
    private Integer yearsExperience = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuideStatus status = GuideStatus.AVAILABLE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Guide(String fullName, String email, String phone, String licenseNumber,
                 String languages, String specialization, Integer yearsExperience, GuideStatus status) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.licenseNumber = licenseNumber;
        this.languages = languages;
        this.specialization = specialization;
        this.yearsExperience = yearsExperience;
        this.status = status;
    }
}
