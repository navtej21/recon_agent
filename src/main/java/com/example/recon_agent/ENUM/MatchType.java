package com.example.recon_agent.ENUM;

public enum MatchType {


    /**
     * Confidence tier assigned by the matching engine.
     * EXACT       - identical key + identical amount across sources (confidence 1.0)
     * FUZZY_HIGH  - tolerance-window match, score >= 0.85
     * FUZZY_LOW   - tolerance-window match, score >= 0.60 and < 0.85
     * UNMATCHED   - no candidate cleared the fuzzy threshold; falls through to exceptions
     */

    EXACT,
    FUZZY_HIGH,
    FUZZY_LOW,
    UNMATCHED
}
