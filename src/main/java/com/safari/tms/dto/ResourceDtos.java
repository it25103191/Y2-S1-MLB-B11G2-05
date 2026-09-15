package com.safari.tms.dto;

import com.safari.tms.domain.Assignment;
import com.safari.tms.domain.Guide;
import com.safari.tms.domain.Vehicle;
import com.safari.tms.domain.enums.AssignmentStatus;
import com.safari.tms.domain.enums.GuideStatus;
import com.safari.tms.domain.enums.VehicleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ResourceDtos {

    /* ----------------------------------------------------------- Vehicles */

    public record VehicleRequest(
            @NotBlank(message = "Registration number is required")
            @Size(max = 40) String registrationNumber,

            @NotBlank(message = "Model is required")
            @Size(max = 120) String model,

            @NotBlank(message = "Vehicle type is required")
            @Size(max = 60) String type,

            @NotNull(message = "Capacity is required")
            @Min(value = 1, message = "Capacity must be at least 1")
            Integer capacity,

            VehicleStatus status,

            LocalDate lastServiceDate,

            @Size(max = 500) String notes) {
    }

    public record VehicleView(
            Long id,
            String registrationNumber,
            String model,
            String type,
            Integer capacity,
            VehicleStatus status,
            LocalDate lastServiceDate,
            String notes,
            long upcomingAssignments) {

        public static VehicleView of(Vehicle v, long upcoming) {
            return new VehicleView(v.getId(), v.getRegistrationNumber(), v.getModel(), v.getType(),
                    v.getCapacity(), v.getStatus(), v.getLastServiceDate(), v.getNotes(), upcoming);
        }
    }

    /* ------------------------------------------------------------- Guides */

    public record GuideRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 120) String fullName,

            @Size(max = 180) String email,

            @Size(max = 40) String phone,

            @NotBlank(message = "Licence number is required")
            @Size(max = 40) String licenseNumber,

            @Size(max = 200) String languages,

            @Size(max = 150) String specialization,

            @Min(value = 0, message = "Experience cannot be negative")
            Integer yearsExperience,

            GuideStatus status) {
    }

    public record GuideView(
            Long id,
            String fullName,
            String email,
            String phone,
            String licenseNumber,
            String languages,
            String specialization,
            Integer yearsExperience,
            GuideStatus status,
            long upcomingAssignments) {

        public static GuideView of(Guide g, long upcoming) {
            return new GuideView(g.getId(), g.getFullName(), g.getEmail(), g.getPhone(),
                    g.getLicenseNumber(), g.getLanguages(), g.getSpecialization(),
                    g.getYearsExperience(), g.getStatus(), upcoming);
        }
    }

    /* -------------------------------------------------------- Assignments */

    public record AssignmentRequest(
            @NotNull(message = "Choose a booking") Long bookingId,
            @NotNull(message = "Choose a guide") Long guideId,
            @NotNull(message = "Choose a vehicle") Long vehicleId,
            @Size(max = 1000) String notes,

            /** Set true to commit despite a detected clash; requires a reason. */
            boolean override,
            @Size(max = 500) String overrideReason) {
    }

    public record AssignmentStatusRequest(@NotNull AssignmentStatus status) {
    }

    public record AssignmentView(
            Long id,
            Long bookingId,
            String bookingReference,
            String customerName,
            String packageName,
            String parkName,
            Integer participants,
            LocalDate assignmentDate,
            LocalDate endDate,
            Integer durationDays,
            Long guideId,
            String guideName,
            Long vehicleId,
            String vehicleRegistration,
            String vehicleModel,
            Integer vehicleCapacity,
            AssignmentStatus status,
            String notes,
            boolean manualOverride,
            String overrideReason,
            String assignedByName,
            Instant createdAt) {

        public static AssignmentView of(Assignment a) {
            return new AssignmentView(
                    a.getId(),
                    a.getBooking().getId(),
                    a.getBooking().getBookingReference(),
                    a.getBooking().getCustomer().getFullName(),
                    a.getBooking().getSafariPackage().getName(),
                    a.getBooking().getSafariPackage().getPark().getName(),
                    a.getBooking().getParticipants(),
                    a.getAssignmentDate(),
                    a.getEndDate(),
                    a.getDurationDays(),
                    a.getGuide().getId(),
                    a.getGuide().getFullName(),
                    a.getVehicle().getId(),
                    a.getVehicle().getRegistrationNumber(),
                    a.getVehicle().getModel(),
                    a.getVehicle().getCapacity(),
                    a.getStatus(),
                    a.getNotes(),
                    a.isManualOverride(),
                    a.getOverrideReason(),
                    a.getAssignedBy() == null ? null : a.getAssignedBy().getFullName(),
                    a.getCreatedAt());
        }
    }

    /** One clashing assignment, described well enough to show the user what is in the way. */
    public record ClashView(
            String resourceType,
            Long resourceId,
            String resourceName,
            Long assignmentId,
            String bookingReference,
            LocalDate from,
            LocalDate to) {
    }

    /** A candidate resource plus whether it is free for the window in question. */
    public record CandidateView(
            Long id,
            String name,
            String detail,
            String status,
            boolean available,
            String unavailableReason,
            Integer capacity,
            long workloadDays) {
    }

    public record SuggestionView(
            Long bookingId,
            String bookingReference,
            LocalDate from,
            LocalDate to,
            Integer participants,
            List<CandidateView> guides,
            List<CandidateView> vehicles,
            Long recommendedGuideId,
            Long recommendedVehicleId,
            String rationale) {
    }

    private ResourceDtos() {
    }
}
