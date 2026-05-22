package com.aegis.service;

import com.aegis.exception.MutationsDisabledException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CompetitorMutationPolicy {

    private final boolean mutationsEnabled;

    public CompetitorMutationPolicy(
            @Value("${aegis.competitors.mutations-enabled:true}") boolean mutationsEnabled) {
        this.mutationsEnabled = mutationsEnabled;
    }

    public void assertMutationsAllowed() {
        if (!mutationsEnabled) {
            throw new MutationsDisabledException(
                    "Competitor list changes are disabled on this deployment.");
        }
    }
}
