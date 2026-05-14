package be.rubikub.expenseline.extraction.internal;

import be.rubikub.expenseline.extraction.ReceiptExtractionServicePublic;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ReceiptExtractionService implements ReceiptExtractionServicePublic {

    final Log log = LogFactory.getLog(ReceiptExtractionService.class);
    final ExtractionAgent agent;

    public ReceiptExtractionService(ExtractionAgent agent) {
        this.agent = agent;
    }

    @Override
    public Optional<String> process(String text) {
        if (!agent.isReceipt(text)) {
            log.warn("Rejected non-receipt document");
            return Optional.empty();
        }
        return Optional.of(agent.extract(text));
    }
}
