package com.safari.tms.domain;

import com.safari.tms.domain.enums.VehicleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "vehicles", uniqueConstraints = @UniqueConstraint(name = "uk_vehicles_reg", columnNames = "registrationNumber"))
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String registrationNumber;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    private LocalDate lastServiceDate;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Vehicle(String registrationNumber, String model, String type, Integer capacity,
                   VehicleStatus status, LocalDate lastServiceDate, String notes) {
        this.registrationNumber = registrationNumber;
        this.model = model;
        this.type = type;
        this.capacity = capacity;
        this.status = status;
        this.lastServiceDate = lastServiceDate;
        this.notes = notes;
    }
}
