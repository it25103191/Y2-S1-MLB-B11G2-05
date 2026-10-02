package com.safari.tms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "parks", uniqueConstraints = @UniqueConstraint(name = "uk_parks_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
public class Park {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String location;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal entryFeePerPerson = BigDecimal.ZERO;

    @Column(length = 120)
    private String permitAuthority;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Park(String name, String location, String description, BigDecimal entryFeePerPerson, String permitAuthority) {
        this.name = name;
        this.location = location;
        this.description = description;
        this.entryFeePerPerson = entryFeePerPerson;
        this.permitAuthority = permitAuthority;
    }
}
