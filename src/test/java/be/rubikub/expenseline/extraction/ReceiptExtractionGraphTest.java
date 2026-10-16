package be.rubikub.expenseline.extraction;

import be.rubikub.expenseline.costumEvents.models.Receipt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptExtractionGraphTest {

    private static final Receipt COFFEE = receipt("Coffee Shop", "16/10/2026", "4.50");
    private static final Receipt COFFEE_LARGE = receipt("Coffee Shop", "16/10/2026 09:15", "4.50");
    private static final Receipt BAKERY = receipt("Bakery", "16/10/2026", "12.00");
    private static final Receipt SUPERSTORE = receipt("Superstore Wholesale", "16/10/2026", "1450.00");

    @Test
    @DisplayName("Short text -> Small Model Classifies OK -> Small Model Extracts -> Quality COMPLETE -> SUCCESS")
    void testSmallModelSuccessComplete() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> COFFEE,
                (orig, ext) -> ExtractionConfidence.COMPLETE
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> fail("Large agent classify should not be called"),
                text -> fail("Large agent extract should not be called"),
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph("Coffee Receipt Total: 4.50 EUR");

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("small", state.activeModel().orElse(null));
        assertEquals(COFFEE, state.extractedData().orElse(null));
    }

    @Test
    @DisplayName("Short text -> Small Model Classifies OK -> Small Model Incomplete -> Retries with Large Model -> SUCCESS")
    void testSmallModelIncompleteRetriesWithLarge() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> receipt("Coffe Shop", "16/10/2026", "4.50"),
                (orig, ext) -> ExtractionConfidence.INCOMPLETE
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> fail("Large agent classify should not be called"),
                text -> COFFEE_LARGE,
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph("Coffee Receipt Total: 4.50 EUR");

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("large", state.activeModel().orElse(null));
        assertEquals(COFFEE_LARGE, state.extractedData().orElse(null));
    }

    @Test
    @DisplayName("Short text -> Small Model Uncertain (NEEDS_REVIEW) -> Escalates to Large Model (OK) -> Large Extracts -> SUCCESS")
    void testSmallModelNeedsReviewEscalatesToLargeSuccess() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.NEEDS_REVIEW,
                text -> fail("Small agent extract should not be called"),
                (orig, ext) -> fail("Small agent assess should not be called")
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> BAKERY,
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph("Uncertain Receipt Format");

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("large", state.activeModel().orElse(null));
        assertEquals(BAKERY, state.extractedData().orElse(null));
    }

    @Test
    @DisplayName("Short text -> Small Model Uncertain (NEEDS_REVIEW) -> Large Model also Uncertain -> FLAGGED_FOR_HUMAN_REVIEW")
    void testSmallModelNeedsReviewLargeAlsoNeedsReview() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.NEEDS_REVIEW,
                text -> fail("Small agent extract should not be called"),
                (orig, ext) -> fail("Small agent assess should not be called")
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> ClassificationResult.NEEDS_REVIEW,
                text -> fail("Large agent extract should not be called"),
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph("Random strange document text");

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals("FLAGGED_FOR_HUMAN_REVIEW", state.status().orElse(null));
        assertTrue(state.extractedData().isEmpty());
    }

    @Test
    @DisplayName("Short text -> Small Model Rejects Document (NOK) -> REJECTED_NON_RECEIPT")
    void testSmallModelRejected() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.NOK,
                text -> fail("Small agent extract should not be called"),
                (orig, ext) -> fail("Small agent assess should not be called")
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> fail("Large agent should not be called"),
                text -> fail("Large agent should not be called"),
                (orig, ext) -> fail("Large agent should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph("This is a novel about a dragon.");

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals("REJECTED_NON_RECEIPT", state.status().orElse(null));
        assertTrue(state.extractedData().isEmpty());
    }

    @Test
    @DisplayName("Long text (> 2500 chars) -> Strategy BIG -> Large Model Direct Extraction -> SUCCESS")
    void testLargeModelDirectSuccess() throws Exception {
        String longText = "RECEIPT ".repeat(400); // 3200 characters

        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> fail("Small agent should not be called for large documents"),
                text -> fail("Small agent should not be called"),
                (orig, ext) -> fail("Small agent should not be called")
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> SUPERSTORE,
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph(longText);

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals(ModelStrategy.ModelPicker.BIG, state.strategy().orElse(null));
        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("large", state.activeModel().orElse(null));
        assertEquals(SUPERSTORE, state.extractedData().orElse(null));
    }

    @Test
    @DisplayName("Small Model output cannot be parsed -> Retries with Large Model -> SUCCESS")
    void testSmallModelParseFailureRetriesWithLarge() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> { throw new RuntimeException("Cannot deserialize value of type BigDecimal from \"12,50\""); },
                (orig, ext) -> fail("Small agent assess should not be called after a parse failure")
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> fail("Large agent classify should not be called"),
                text -> BAKERY,
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        ExtractionGraphBuilder.State state = service.executeGraph("Bakery Total: 12,50 EUR").orElseThrow();

        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("large", state.activeModel().orElse(null));
        assertEquals(BAKERY, state.extractedData().orElse(null));
    }

    @Test
    @DisplayName("Small Model Receipt fails Java validation -> skips self-assessment -> Retries with Large Model -> SUCCESS")
    void testSmallModelInvalidRetriesWithLarge() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> receipt(null, "16/10/2026", "4.50"),
                (orig, ext) -> fail("Small agent assess should not be called for an invalid receipt")
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> fail("Large agent classify should not be called"),
                text -> COFFEE,
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        ExtractionGraphBuilder.State state = service.executeGraph("Coffee Receipt Total: 4.50 EUR").orElseThrow();

        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("large", state.activeModel().orElse(null));
        assertEquals(COFFEE, state.extractedData().orElse(null));
    }

    @Test
    @DisplayName("Small Model self-assessment receives the Receipt serialized as JSON")
    void testSelfAssessmentReceivesJson() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> COFFEE,
                (orig, ext) -> {
                    assertTrue(ext.contains("\"Vendor\":\"Coffee Shop\""), ext);
                    assertTrue(ext.contains("\"TotalamountInclVat\":4.50"), ext);
                    return ExtractionConfidence.COMPLETE;
                }
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> fail("Large agent should not be called"),
                text -> fail("Large agent should not be called"),
                (orig, ext) -> fail("Large agent should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        ExtractionGraphBuilder.State state = service.executeGraph("Coffee Receipt Total: 4.50 EUR").orElseThrow();

        assertEquals("SUCCESS", state.status().orElse(null));
    }

    @Test
    @DisplayName("Large Model Receipt fails Java validation -> FLAGGED_FOR_HUMAN_REVIEW (Receipt kept for reviewer)")
    void testLargeModelInvalidFlagsForReview() throws Exception {
        Receipt wrongTotal = new Receipt("16/10/2026", "Bakery", null, null, null, null, null, null,
                new BigDecimal("10.00"), new BigDecimal("12.00"),
                List.of(new be.rubikub.expenseline.costumEvents.models.ReceiptItem(
                        BigDecimal.ONE, new BigDecimal("0.60"), 6, null, null, "Bread",
                        new BigDecimal("10.00"), new BigDecimal("10.60"))));
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.NEEDS_REVIEW,
                text -> fail("Small agent extract should not be called"),
                (orig, ext) -> fail("Small agent assess should not be called")
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> wrongTotal,
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        ExtractionGraphBuilder.State state = service.executeGraph("Bakery receipt").orElseThrow();

        assertEquals("FLAGGED_FOR_HUMAN_REVIEW", state.status().orElse(null));
        assertEquals(wrongTotal, state.extractedData().orElse(null));
        assertFalse(state.validationErrors().isEmpty());
    }

    @Test
    @DisplayName("Large Model output cannot be parsed -> FLAGGED_FOR_HUMAN_REVIEW without stale small-model data")
    void testLargeModelParseFailureFlagsForReview() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> COFFEE,
                (orig, ext) -> ExtractionConfidence.INCOMPLETE
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> fail("Large agent classify should not be called"),
                text -> { throw new RuntimeException("invalid JSON"); },
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        ExtractionGraphBuilder.State state = service.executeGraph("Coffee Receipt Total: 4.50 EUR").orElseThrow();

        assertEquals("FLAGGED_FOR_HUMAN_REVIEW", state.status().orElse(null));
        assertTrue(state.extractedData().isEmpty());
    }

    private static Receipt receipt(String vendor, String date, String total) {
        return new Receipt(date, vendor, null, null, null, null, null, null,
                null, total == null ? null : new BigDecimal(total), List.of());
    }

    // Helper fake implementation of ExtractionAgent interface
    private record TestExtractionAgent(
            java.util.function.Function<String, ClassificationResult> classifier,
            java.util.function.Function<String, Receipt> extractor,
            java.util.function.BiFunction<String, String, ExtractionConfidence> assessor
    ) implements ExtractionAgent {
        @Override
        public ClassificationResult classifyDocument(String text) {
            return classifier.apply(text);
        }

        @Override
        public Receipt extract(String text) {
            return extractor.apply(text);
        }

        @Override
        public ExtractionConfidence assessExtraction(String originalText, String extractedData) {
            return assessor.apply(originalText, extractedData);
        }
    }
}
