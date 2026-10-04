package com.safari.tms.service.crew;

import com.safari.tms.dto.ResourceDtos.CandidateView;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Concrete strategy (the default): spread the work. Recommends the free guide with the fewest
 * trip days booked over the next year, and the smallest free vehicle that seats everyone, so
 * bigger vehicles stay free for bigger groups.
 */
@Component
public class LeastBusyCrew implements CrewSelectionStrategy {

    public static final String KEY = "least-busy";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String label() {
        return "Least busy guide";
    }

    @Override
    public Long pickGuide(List<CandidateView> guides) {
        return guides.stream()
                .filter(CandidateView::available)
                .min(Comparator.comparingLong(CandidateView::workloadDays))
                .map(CandidateView::id)
                .orElse(null);
    }

    @Override
    public Long pickVehicle(List<CandidateView> vehicles, int travellers) {
        return smallestThatFits(vehicles);
    }

    @Override
    public String rationale(int travellers) {
        return "Picked the least-loaded free guide and the smallest free vehicle that seats " + travellers + ".";
    }

    /** Shared by the strategies: the smallest available vehicle, then the least used. */
    static Long smallestThatFits(List<CandidateView> vehicles) {
        return vehicles.stream()
                .filter(CandidateView::available)
                .min(Comparator.comparingInt((CandidateView c) -> c.capacity() == null ? 999 : c.capacity())
                        .thenComparingLong(CandidateView::workloadDays))
                .map(CandidateView::id)
                .orElse(null);
    }
}
