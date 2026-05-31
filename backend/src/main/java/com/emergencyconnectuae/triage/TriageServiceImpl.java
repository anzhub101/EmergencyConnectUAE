// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.triage;

import com.emergencyconnectuae.triage.dto.TriageResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based AI triage (SRS 6.1). Pure function of the description text —
 * deterministic and side-effect free, so it can back both POST /triage/analyze
 * and incident creation.
 */
@Service
public class TriageServiceImpl implements TriageService {

    /** One keyword rule. The first rule with a matching keyword wins. */
    private record Rule(List<String> keywords, String criticality, List<String> units,
                        String hospitalTier, double confidence) {}

    // Ordered top-to-bottom exactly as the SRS table (first match wins).
    private static final List<Rule> RULES = List.of(
        new Rule(List.of("cardiac", "heart attack", "chest pain", "mi", "myocardial"),
                 "CRITICAL", List.of("AMBULANCE", "CARDIAC_UNIT"), "TERTIARY_CARDIAC_ICU", 0.95),
        new Rule(List.of("stroke", "cva", "facial droop", "slurred speech"),
                 "CRITICAL", List.of("AMBULANCE", "ADVANCED_PARAMEDIC"), "STROKE_TERTIARY", 0.92),
        new Rule(List.of("fire", "smoke", "blaze", "explosion", "burning building"),
                 "HIGH", List.of("FIRE", "AMBULANCE"), "NEAREST_AE_BURNS", 0.88),
        new Rule(List.of("accident", "crash", "collision", "rta", "road traffic", "road"),
                 "HIGH", List.of("AMBULANCE", "POLICE"), "NEAREST_TRAUMA", 0.85),
        new Rule(List.of("drowning", "water rescue", "flood"),
                 "HIGH", List.of("MARINE_RESCUE", "AMBULANCE"), "NEAREST_AE", 0.83),
        new Rule(List.of("fall", "fracture", "broken bone", "injury", "unconscious"),
                 "MEDIUM", List.of("AMBULANCE"), "NEAREST_GENERAL", 0.75),
        new Rule(List.of("missing person", "lost child", "welfare check"),
                 "LOW", List.of("POLICE"), "NONE", 0.70)
    );

    // Fallback when no keyword matches.
    private static final Rule DEFAULT_RULE =
        new Rule(List.of(), "MEDIUM", List.of("AMBULANCE"), "NEAREST_GENERAL", 0.40);

    @Override
    public TriageResponse analyze(String description) {
        String text = description == null ? "" : description.toLowerCase();

        for (Rule rule : RULES) {
            List<String> matched = new ArrayList<>();
            for (String keyword : rule.keywords()) {
                if (text.contains(keyword)) {
                    matched.add(keyword);
                }
            }
            if (!matched.isEmpty()) {
                return new TriageResponse(rule.criticality(), rule.confidence(), rule.units(),
                        rule.hospitalTier(), matched, rule.units().size());
            }
        }

        return new TriageResponse(DEFAULT_RULE.criticality(), DEFAULT_RULE.confidence(),
                DEFAULT_RULE.units(), DEFAULT_RULE.hospitalTier(), List.of(),
                DEFAULT_RULE.units().size());
    }
}
