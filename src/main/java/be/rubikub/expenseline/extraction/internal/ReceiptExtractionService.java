package be.rubikub.expenseline.extraction.internal;

import be.rubikub.expenseline.costumEvents.TextExtractedEvent;
import be.rubikub.expenseline.extraction.ReceiptExtractionServicePublic;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ReceiptExtractionService implements ReceiptExtractionServicePublic {

    final Log log = LogFactory.getLog(ReceiptExtractionService.class);
    final ExtractionAgent smallAgent;
    final ExtractionAgent largeAgent;

    public ReceiptExtractionService(
            @Qualifier("smallAgent") ExtractionAgent smallAgent,
            @Qualifier("largeAgent") ExtractionAgent largeAgent) {
        this.smallAgent = smallAgent;
        this.largeAgent = largeAgent;
    }

    @EventListener
    void onTextExtracted(TextExtractedEvent event) {
        log.info("Processing extracted text with small model first");

        // Try small model first
        ClassificationResult classification = smallAgent.classifyDocument(event.text());

        switch (classification) {
            case OK -> process(event.text(), false);
            case NOK -> log.warn("Rejected non-receipt document");
            case NEEDS_REVIEW -> {
                log.warn("Small model uncertain - escalating to large model");
                // Fallback to large model
                ClassificationResult largeModelResult = largeAgent.classifyDocument(event.text());

                if (largeModelResult == ClassificationResult.OK) {
                    log.info("Large model confirmed: processing document");
                    process(event.text(), true);
                } else {
                    log.warn("Large model also uncertain/rejected - flagging for human review");
                }
            }
        }
    }

    @Override
    public Optional<String> process(String text) {
        return process(text, false);
    }

    private Optional<String> process(String text, boolean useLargeModel) {
        ExtractionAgent agentToUse = useLargeModel ? largeAgent : smallAgent;
        String modelType = useLargeModel ? "large model" : "small model";

        log.info("Extracting data using " + modelType);
        String extracted = agentToUse.extract(text);

        // If using small model, ask it to self-assess
        if (!useLargeModel) {
            log.info("Small model self-assessing extraction quality");
            ExtractionConfidence confidence = smallAgent.assessExtraction(text, extracted);

            if (confidence == ExtractionConfidence.INCOMPLETE) {
                log.warn("Small model reports incomplete extraction - retrying with large model");
                // Retry with large model
                return process(text, true);
            }

            log.info("Small model confident in extraction");
        }

        IO.println("Extracted with " + modelType + ":");
        IO.println(extracted);

        return Optional.of(extracted);
    }
}
