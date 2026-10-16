package be.rubikub.expenseline.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptExtractionGraphTest {

    @Test
    @DisplayName("Short text -> Small Model Classifies OK -> Small Model Extracts -> Quality COMPLETE -> SUCCESS")
    void testSmallModelSuccessComplete() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> "{\"merchant\":\"Coffee Shop\",\"total\":4.50}",
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
        assertEquals("{\"merchant\":\"Coffee Shop\",\"total\":4.50}", state.extractedData().orElse(null));
    }

    @Test
    @DisplayName("Short text -> Small Model Classifies OK -> Small Model Incomplete -> Retries with Large Model -> SUCCESS")
    void testSmallModelIncompleteRetriesWithLarge() throws Exception {
        ExtractionAgent smallAgent = new TestExtractionAgent(
                text -> ClassificationResult.OK,
                text -> "{\"total\":4.50}",
                (orig, ext) -> ExtractionConfidence.INCOMPLETE
        );
        ExtractionAgent largeAgent = new TestExtractionAgent(
                text -> fail("Large agent classify should not be called"),
                text -> "{\"merchant\":\"Coffee Shop\",\"date\":\"2026-10-16\",\"total\":4.50}",
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph("Coffee Receipt Total: 4.50 EUR");

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("large", state.activeModel().orElse(null));
        assertEquals("{\"merchant\":\"Coffee Shop\",\"date\":\"2026-10-16\",\"total\":4.50}", state.extractedData().orElse(null));
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
                text -> "{\"merchant\":\"Bakery\",\"total\":12.00}",
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph("Uncertain Receipt Format");

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("large", state.activeModel().orElse(null));
        assertEquals("{\"merchant\":\"Bakery\",\"total\":12.00}", state.extractedData().orElse(null));
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
                text -> "{\"merchant\":\"Superstore Wholesale\",\"total\":1450.00}",
                (orig, ext) -> fail("Large agent assess should not be called")
        );

        ReceiptExtractionService service = new ReceiptExtractionService(smallAgent, largeAgent);
        Optional<ExtractionGraphBuilder.State> result = service.executeGraph(longText);

        assertTrue(result.isPresent());
        ExtractionGraphBuilder.State state = result.get();
        assertEquals(ModelStrategy.ModelPicker.BIG, state.strategy().orElse(null));
        assertEquals("SUCCESS", state.status().orElse(null));
        assertEquals("large", state.activeModel().orElse(null));
        assertEquals("{\"merchant\":\"Superstore Wholesale\",\"total\":1450.00}", state.extractedData().orElse(null));
    }

    // Helper fake implementation of ExtractionAgent interface
    private record TestExtractionAgent(
            java.util.function.Function<String, ClassificationResult> classifier,
            java.util.function.Function<String, String> extractor,
            java.util.function.BiFunction<String, String, ExtractionConfidence> assessor
    ) implements ExtractionAgent {
        @Override
        public ClassificationResult classifyDocument(String text) {
            return classifier.apply(text);
        }

        @Override
        public ClassificationResult classifyDocument(String text, int memoryId) {
            return classifier.apply(text);
        }

        @Override
        public String extract(String text) {
            return extractor.apply(text);
        }

        @Override
        public ExtractionConfidence assessExtraction(String originalText, String extractedData) {
            return assessor.apply(originalText, extractedData);
        }
    }
}
