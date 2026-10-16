package be.rubikub.expenseline.extraction;

import be.rubikub.expenseline.costumEvents.TextIntakeEvent;
import be.rubikub.expenseline.costumEvents.models.Receipt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;

/**
 * ==============================================================================================
 *                              RECEIPT EXTRACTION SERVICE
 * ==============================================================================================
 *
 * This service receives raw receipt text events (TextIntakeEvent) and processes them through
 * an intelligent multi-model agentic pipeline built with LangGraph4j.
 *
 * ----------------------------------------------------------------------------------------------
 * WHY REWRITE TO LANGGRAPH4J?
 * ----------------------------------------------------------------------------------------------
 * Before LangGraph4j, the extraction flow relied on imperative, deeply nested if/switch
 * statements with recursive fallback calls (e.g. process(text, true)).
 *
 * Drawbacks of imperative approach:
 *   - Difficult to visualize complex branchings and multi-step fallbacks.
 *   - Error-prone state management across retries and model escalations.
 *   - Hard to monitor, checkpoint, stream, or add human-in-the-loop steps.
 *
 * Benefits of LangGraph4j State Graph:
 *   - Declarative: The entire routing logic (heuristics, classification, extraction,
 *     self-assessment, retry) is explicit as nodes and conditional edges.
 *   - Centralized State: The immutable ExtractionGraphBuilder.State tracks inputs, intermediate
 *     classifications, confidence evaluations, active model, and final JSON output.
 *   - Extensible: Adding new validation steps, tools, or memory takes only a node and an edge.
 *   - Resilient & Observable: Graph transitions can be logged, streamed, and debugged easily.
 * ==============================================================================================
 */
@ApplicationScoped
public class ReceiptExtractionService {

    private static final Logger log = Logger.getLogger(ReceiptExtractionService.class);

    final ExtractionAgent smallAgent;
    final ExtractionAgent largeAgent;

    /**
     * The compiled, thread-safe executable LangGraph4j instance.
     * Compiled once during service instantiation.
     */
    private final CompiledGraph<ExtractionGraphBuilder.State> extractionGraph;

    @Inject
    public ReceiptExtractionService(
            @Named("smallAgent") ExtractionAgent smallAgent,
            @Named("largeAgent") ExtractionAgent largeAgent) {
        this.smallAgent = smallAgent;
        this.largeAgent = largeAgent;

        try {
            // Build and compile the state graph once during bean initialization
            ExtractionGraphBuilder builder = new ExtractionGraphBuilder(smallAgent, largeAgent);
            this.extractionGraph = builder.build();
            log.info("LangGraph4j ReceiptExtraction graph compiled successfully");
        } catch (GraphStateException e) {
            log.error("Failed to compile LangGraph4j extraction graph", e);
            throw new IllegalStateException("Failed to initialize extraction graph", e);
        }
    }

    /**
     * ==========================================================================================
     * EVENT HANDLER: onTextExtracted
     * ==========================================================================================
     * Triggered automatically whenever a TextIntakeEvent is fired (e.g. after document intake).
     *
     * How LangGraph4j Execution Works Here:
     * ------------------------------------------------------------------------------------------
     * 1. PREPARE INPUT STATE:
     *    LangGraph expects an initial GraphInput representing starting state keys.
     *    Here we provide GraphInput.args(Map.of("text", event.text())).
     *
     * 2. INVOKE GRAPH:
     *    `extractionGraph.invoke(GraphInput.args(inputs), RunnableConfig.builder().build())` starts execution at `StateGraph.START`.
     *    It traverses nodes and evaluates conditional edges asynchronously until reaching
     *    `StateGraph.END`, producing the final converged `ExtractionGraphBuilder.State`.
     *
     * 3. PROCESS FINAL STATE:
     *    We inspect the final state to determine the workflow outcome:
     *    - SUCCESS: Receipt extracted and validated.
     *    - REJECTED_NON_RECEIPT: Document was classified as not a receipt (NOK).
     *    - FLAGGED_FOR_HUMAN_REVIEW: Uncertainty persists across models (NEEDS_REVIEW).
     *
     * @param event CDI event containing raw extracted document text and metadata.
     */
    void onTextExtracted(@Observes TextIntakeEvent event) {
        log.info("Received TextIntakeEvent - dispatching to LangGraph4j extraction graph");

        // Execute the workflow graph with the intake text
        Optional<ExtractionGraphBuilder.State> finalState = executeGraph(event.text());

        // Handle the final outcome
        finalState.ifPresentOrElse(
                state -> {
                    String status = state.status().orElse("UNKNOWN");
                    String activeModel = state.activeModel().orElse("none");
                    switch (status) {
                        case "SUCCESS" -> {
                            log.infof("Receipt extraction workflow SUCCEEDED using [%s model]", activeModel);
                            log.infof("Extracted Data:\n%s", state.extractedData().orElse(null));
                        }
                        case "REJECTED_NON_RECEIPT" -> {
                            log.warn("Receipt extraction workflow finished: Document REJECTED (non-receipt)");
                        }
                        case "FLAGGED_FOR_HUMAN_REVIEW" -> {
                            log.warn("Receipt extraction workflow finished: FLAGGED FOR HUMAN REVIEW");
                        }
                        default -> {
                            log.warnf("Receipt extraction workflow finished with unhandled status: [%s]", status);
                        }
                    }
                },
                () -> log.error("LangGraph4j execution returned empty final state")
        );
    }

    /**
     * Convenience method to execute the graph directly on raw text.
     * Useful for programmatic invocation and unit testing.
     *
     * @param text Raw document text
     * @return Optional containing the final AgentState after execution
     */
    public Optional<ExtractionGraphBuilder.State> executeGraph(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Input text is empty, skipping graph execution");
            return Optional.empty();
        }

        // LangGraph4j takes an initial GraphInput wrapping state properties and RunnableConfig
        Map<String, Object> initialInputs = Map.of(
                "text", text
        );

        // Run the graph to completion (from START to END)
        return extractionGraph.invoke(
                GraphInput.args(initialInputs),
                RunnableConfig.builder().build()
        );
    }

    /**
     * Helper method to extract text and return the resulting Receipt.
     *
     * @param text Raw document text
     * @return Optional containing the extracted Receipt if successful
     */
    public Optional<Receipt> process(String text) {
        return executeGraph(text)
                .filter(state -> "SUCCESS".equals(state.status().orElse(null)))
                .flatMap(ExtractionGraphBuilder.State::extractedData);
    }
}
