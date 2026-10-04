package com.safari.tms.service.crew;

import com.safari.tms.dto.ResourceDtos.CandidateView;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Concrete strategy: put the most experienced free guide on the trip (for first-time travellers,
 * VIPs or difficult parks). Ties go to the guide with less work booked. The vehicle rule is the
 * same as the default: the smallest free vehicle that seats everyone.
 */
@Component
public class MostExperiencedCrew implements CrewSelectionStrategy {

    @Override
    public String key() {
        return "most-experienced";
    }

    @Override
    public String label() {
        return "Most experienced guide";
    }

    @Override
    public Long pickGuide(List<CandidateView> guides) {
        return guides.stream()
                .filter(CandidateView::available)
                .max(Comparator.comparingInt((CandidateView c) -> c.yearsExperience() == null ? 0 : c.yearsExperience())
                        .thenComparing(Comparator.comparingLong(CandidateView::workloadDays).reversed()))
                .map(CandidateView::id)
                .orElse(null);
    }

    @Override
    public Long pickVehicle(List<CandidateView> vehicles, int travellers) {
        return LeastBusyCrew.smallestThatFits(vehicles);
    }

    @Override
    public String rationale(int travellers) {
        return "Picked the most experienced free guide and the smallest free vehicle that seats " + travellers + ".";
    }
}
