package com.safari.tms.dto;

import com.safari.tms.domain.Park;
import com.safari.tms.domain.SafariPackage;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CatalogueDtos {

    public record ParkRequest(
            @NotBlank(message = "Park name is required")
            @Size(max = 150) String name,

            @NotBlank(message = "Location is required")
            @Size(max = 150) String location,

            @Size(max = 2000) String description,

            @NotNull(message = "Entry fee is required")
            @DecimalMin(value = "0.0", message = "Entry fee cannot be negative")
            BigDecimal entryFeePerPerson,

            @Size(max = 120) String permitAuthority,

            Boolean active) {
    }

    public record ParkView(
            Long id,
            String name,
            String location,
            String description,
            BigDecimal entryFeePerPerson,
            String permitAuthority,
            boolean active,
            long packageCount) {

        public static ParkView of(Park park, long packageCount) {
            return new ParkView(park.getId(), park.getName(), park.getLocation(), park.getDescription(),
                    park.getEntryFeePerPerson(), park.getPermitAuthority(), park.isActive(), packageCount);
        }
    }

    public record PackageRequest(
            @NotBlank(message = "Package name is required")
            @Size(max = 150) String name,

            @Size(max = 2000) String description,

            @NotNull(message = "Choose a park") Long parkId,

            @NotNull(message = "Duration is required")
            @Min(value = 1, message = "Duration must be at least 1 day")
            Integer durationDays,

            @NotNull(message = "Price is required")
            @DecimalMin(value = "0.01", message = "Price must be greater than zero")
            BigDecimal pricePerPerson,

            @NotNull(message = "Maximum group size is required")
            @Min(value = 1, message = "Group size must be at least 1")
            Integer maxGroupSize,

            @Size(max = 400) String imageUrl,

            @Size(max = 500) String highlights,

            Boolean active) {
    }

    public record PackageView(
            Long id,
            String name,
            String description,
            Long parkId,
            String parkName,
            String parkLocation,
            Integer durationDays,
            BigDecimal pricePerPerson,
            Integer maxGroupSize,
            String imageUrl,
            String highlights,
            boolean active) {

        public static PackageView of(SafariPackage p) {
            return new PackageView(p.getId(), p.getName(), p.getDescription(),
                    p.getPark().getId(), p.getPark().getName(), p.getPark().getLocation(),
                    p.getDurationDays(), p.getPricePerPerson(), p.getMaxGroupSize(),
                    p.getImageUrl(), p.getHighlights(), p.isActive());
        }
    }

    /** Live seat availability for one package on one departure date. */
    public record AvailabilityView(
            Long packageId,
            LocalDate tripDate,
            int maxGroupSize,
            int seatsTaken,
            int seatsRemaining,
            boolean soldOut) {
    }

    private CatalogueDtos() {
    }
}
