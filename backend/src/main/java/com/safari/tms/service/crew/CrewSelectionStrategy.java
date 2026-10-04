package com.safari.tms.service.crew;

import com.safari.tms.dto.ResourceDtos.CandidateView;

import java.util.List;

/**
 * Strategy pattern: a rule for recommending the guide and vehicle for a trip.
 *
 * <p>Each rule is its own class. {@link com.safari.tms.service.AssignmentService} (the context)
 * works out which guides and vehicles are free, then asks the strategy the coordinator chose to pick
 * from them. The coordinator can switch rules in the assignment dialog without any change to the
 * service.
 */
public interface CrewSelectionStrategy {

    /** Short id used in the API, e.g. {@code least-busy}. */
    String key();

    /** Name shown to the coordinator. */
    String label();

    /** Picks a guide from the candidates, or {@code null} when none is available. */
    Long pickGuide(List<CandidateView> guides);

    /** Picks a vehicle that seats {@code travellers}, or {@code null} when none is available. */
    Long pickVehicle(List<CandidateView> vehicles, int travellers);

    /** Explains the recommendation once both a guide and a vehicle were found. */
    String rationale(int travellers);
}
