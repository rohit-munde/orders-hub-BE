package com.indiedev.orders_hub.gmail.service;

import java.util.List;

public interface OrderEmailCandidateFinder {

    CandidateBatch find(String accessToken);

    record CandidateBatch(String query, List<String> gmailMessageIds) {
        public CandidateBatch {
            gmailMessageIds = List.copyOf(gmailMessageIds);
        }
    }
}
