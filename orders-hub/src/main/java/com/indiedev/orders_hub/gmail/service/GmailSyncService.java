package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import com.indiedev.orders_hub.order.service.GmailOrderImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailSyncService {

    private final OrderEmailCandidateFinder candidateFinder;
    private final GmailApiClient gmailApiClient;
    private final GmailOrderParser orderParser;
    private final GmailOrderImportService importService;

    public GmailSyncPreview sync(ConnectedAccount account, String accessToken) {
        OrderEmailCandidateFinder.CandidateBatch batch = candidateFinder.find(accessToken);
        List<GmailOrderPreview> importedOrders = new ArrayList<>();
        ImportCounts counts = importCandidates(account, accessToken, batch.gmailMessageIds(), importedOrders);

        return new GmailSyncPreview(
                batch.query(),
                batch.gmailMessageIds().size(),
                counts.saved,
                counts.skipped,
                counts.ignored,
                counts.failed,
                importedOrders
        );
    }

    private ImportCounts importCandidates(
            ConnectedAccount account,
            String accessToken,
            List<String> gmailMessageIds,
            List<GmailOrderPreview> importedOrders
    ) {
        ImportCounts counts = new ImportCounts();
        int parserVersion = orderParser.version();

        for (String gmailMessageId : gmailMessageIds) {
            if (!importService.shouldProcess(account.getId(), gmailMessageId, parserVersion)) {
                counts.skipped++;
                continue;
            }

            try {
                GmailOrderPreview candidate = orderParser.parse(
                        gmailApiClient.getFullMessage(accessToken, gmailMessageId)
                );
                count(importService.importOrder(account, candidate, parserVersion), candidate, counts, importedOrders);
            } catch (RuntimeException exception) {
                counts.failed++;
                recordFailure(account, gmailMessageId, parserVersion);
            }
        }
        return counts;
    }

    private void count(
            GmailOrderImportService.ImportResult result,
            GmailOrderPreview candidate,
            ImportCounts counts,
            List<GmailOrderPreview> importedOrders
    ) {
        switch (result.outcome()) {
            case SAVED -> {
                counts.saved++;
                importedOrders.add(candidate);
            }
            case SKIPPED -> counts.skipped++;
            case IGNORED -> counts.ignored++;
        }
    }

    private void recordFailure(ConnectedAccount account, String gmailMessageId, int parserVersion) {
        try {
            importService.recordFailure(account, gmailMessageId, parserVersion);
        } catch (RuntimeException exception) {
            log.warn("Unable to record Gmail import failure for connected account {}", account.getId());
        }
    }

    private static final class ImportCounts {
        private int saved;
        private int skipped;
        private int ignored;
        private int failed;
    }
}
