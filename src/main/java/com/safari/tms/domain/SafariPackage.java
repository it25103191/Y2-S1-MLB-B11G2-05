package com.safari.tms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "safari_packages")
@Getter
@Setter
@NoArgsConstructor
public class SafariPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "park_id", nullable = false)
    private Park park;

    @Column(nullable = false)
    private Integer durationDays;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerPerson;

    @Column(nullable = false)
    private Integer maxGroupSize;

    @Column(length = 400)
    private String imageUrl;

    @Column(length = 500)
    private String highlights;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public SafariPackage(String name, String description, Park park, Integer durationDays,
                         BigDecimal pricePerPerson, Integer maxGroupSize, String imageUrl, String highlights) {
        this.name = name;
        this.description = description;
        this.park = park;
        this.durationDays = durationDays;
        this.pricePerPerson = pricePerPerson;
        this.maxGroupSize = maxGroupSize;
        this.imageUrl = imageUrl;
        this.highlights = highlights;
    }
}
