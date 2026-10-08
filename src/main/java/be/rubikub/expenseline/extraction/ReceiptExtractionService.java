package be.rubikub.expenseline.extraction;

import be.rubikub.expenseline.costumEvents.TextIntakeEvent;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.atmosphere.inject.annotation.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class ReceiptExtractionService {

    private static final Logger log = Logger.getLogger(ReceiptExtractionService.class);
    final ExtractionAgent smallAgent;
    final ExtractionAgent largeAgent;

    @Inject
    public ReceiptExtractionService(
            @Named("smallAgent") ExtractionAgent smallAgent,
            @Named("largeAgent") ExtractionAgent largeAgent) {
        this.smallAgent = smallAgent;
        this.largeAgent = largeAgent;
    }

    void onTextExtracted(@Observes TextIntakeEvent event) {
        ModelStrategy strategy = new ModelStrategy(event);
        log.info("Processing extracted text with small model first");
        ClassificationResult classification = null;
        // Try small model first
        if (strategy.getStrategy() == ModelStrategy.ModelPicker.BIG) {
            System.out.println("Using large model");
            classification = largeAgent.classifyDocument(event.text());

        } else {
            System.out.println("Using small model");
            classification = smallAgent.classifyDocument(event.text());
        }

        switch (classification) {
            case OK -> process(event.text(), false);
            case NOK -> log.warn("Rejected non-receipt document");
            case NEEDS_REVIEW -> {
                if (strategy.getStrategy() == ModelStrategy.ModelPicker.BIG) {
                    log.warn("Large model  uncertain/rejected - flagging for human review");
                } else {
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

        System.out.println("Extracted with " + modelType + ":");
        System.out.println(extracted);

        return Optional.of(extracted);
    }
}
