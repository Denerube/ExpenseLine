package be.rubikub.expenseline.extraction;

import be.rubikub.expenseline.costumEvents.models.Receipt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * ==============================================================================================
 *                              LANGGRAPH4J TUTORIAL & GRAPH BUILDER
 * ==============================================================================================
 *
 * This class demonstrates how to model complex LLM workflows (multi-agent routing, fallbacks,
 * quality self-assessments, and human-in-the-loop flags) using LangGraph4j.
 *
 * ----------------------------------------------------------------------------------------------
 * 1. WHAT IS LANGGRAPH4J?
 * ----------------------------------------------------------------------------------------------
 * LangGraph4j is a Java implementation of the LangGraph paradigm (originally by LangChain).
 * It models agentic workflows as directed cyclic/acyclic state graphs following the Pregel
 * computation model:
 *
 *   [START] -> (Node 1) --[Condition]--> (Node 2) --> [END]
 *                   |                         ^
 *                   +----[Alternative Path]---+
 *
 * Core Building Blocks:
 *   - State: A thread-safe, immutable data container (extending AgentState) representing
 *     the shared memory of the entire workflow.
 *   - Nodes: Discrete executable actions (functions). A node reads current State and returns
 *     a Map<String, Object> containing *delta* updates to be merged into the State.
 *   - Edges: Fixed transitions from one node to another.
 *   - Conditional Edges: Dynamic routing functions that inspect the State and decide which
 *     node to transition to next.
 *   - CompiledGraph: The verified, executable graph instance ready for invoke() or stream().
 *
 * ----------------------------------------------------------------------------------------------
 * 2. VISUAL WORKFLOW OF THIS EXTRACTION GRAPH
 * ----------------------------------------------------------------------------------------------
 *
 *   [START] -> determine_strategy  (inspects character count)
 *                 |
 *                 |-- SMALL --> classify_small
 *                 |               |-- NOK ----------> handle_rejected -> [END]
 *                 |               |-- NEEDS_REVIEW -> classify_large  (escalate)
 *                 |               '-- OK -----------> extract_small
 *                 |                                     |-- parse failed ------> extract_large
 *                 |                                     '-> validate_small  (deterministic Java checks)
 *                 |                                           |-- invalid -----> extract_large
 *                 |                                           '-> assess_small  (LLM grounding check)
 *                 |                                                 |-- INCOMPLETE -> extract_large
 *                 |                                                 '-- COMPLETE ---> finish_success -> [END]
 *                 |
 *                 '-- BIG ----> classify_large
 *                                 |-- NOK ----------> handle_rejected -> [END]
 *                                 |-- NEEDS_REVIEW -> handle_needs_review -> [END]
 *                                 '-- OK -----------> extract_large
 *                                                       |-- parse failed ------> handle_needs_review
 *                                                       '-> validate_large  (deterministic Java checks)
 *                                                             |-- invalid -----> handle_needs_review
 *                                                             '-- valid -------> finish_success -> [END]
 *
 * ==============================================================================================
 */
public class ExtractionGraphBuilder {

    private static final Logger log = Logger.getLogger(ExtractionGraphBuilder.class);

    // Node Name Constants (helps prevent typos when connecting edges)
    public static final String NODE_DETERMINE_STRATEGY = "determine_strategy";
    public static final String NODE_CLASSIFY_SMALL = "classify_small";
    public static final String NODE_CLASSIFY_LARGE = "classify_large";
    public static final String NODE_EXTRACT_SMALL = "extract_small";
    public static final String NODE_VALIDATE_SMALL = "validate_small";
    public static final String NODE_ASSESS_SMALL = "assess_small";
    public static final String NODE_EXTRACT_LARGE = "extract_large";
    public static final String NODE_VALIDATE_LARGE = "validate_large";
    public static final String NODE_HANDLE_REJECTED = "handle_rejected";
    public static final String NODE_HANDLE_NEEDS_REVIEW = "handle_needs_review";
    public static final String NODE_FINISH_SUCCESS = "finish_success";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExtractionAgent smallAgent;
    private final ExtractionAgent largeAgent;

    public ExtractionGraphBuilder(ExtractionAgent smallAgent, ExtractionAgent largeAgent) {
        this.smallAgent = Objects.requireNonNull(smallAgent, "smallAgent must not be null");
        this.largeAgent = Objects.requireNonNull(largeAgent, "largeAgent must not be null");
    }

    /**
     * ==========================================================================================
     * STEP 1: DEFINE THE STATE (AgentState)
     * ==========================================================================================
     * The State holds all data across the graph's lifetime.
     * In LangGraph4j, state is a key-value dictionary (Map<String, Object>) wrapped in a strongly
     * typed class subclassing `AgentState`.
     *
     * Rules of AgentState:
     * 1. Subclass `org.bsc.langgraph4j.state.AgentState`.
     * 2. Provide a constructor `(Map<String, Object> initData)` and pass it to `super(initData)`.
     * 3. Provide typed accessor helper methods using `value(key)`.
     */
    public static class State extends AgentState {

        public State(Map<String, Object> initData) {
            super(initData);
        }

        /** The original raw receipt text to process. */
        public Optional<String> text() {
            return value("text");
        }

        /** The initial model strategy chosen (SMALL vs BIG based on text length). */
        public Optional<ModelStrategy.ModelPicker> strategy() {
            return value("strategy");
        }

        /** The classification result (OK, NOK, NEEDS_REVIEW). */
        public Optional<ClassificationResult> classification() {
            return value("classification");
        }

        /** The self-assessment confidence from the small model (COMPLETE, INCOMPLETE). */
        public Optional<ExtractionConfidence> confidence() {
            return value("confidence");
        }
        /** The extracted receipt. Absent when extraction failed to produce a parsable Receipt. */
        public Optional<Receipt> extractedData() {
            return value("extractedData");
        }

        /** Problems found by the deterministic {@link ReceiptValidator} checks (empty = valid). */
        public List<String> validationErrors() {
            return this.<List<String>>value("validationErrors").orElse(List.of());
        }

        /** The name or tier of the model that performed the final extraction. */
        public Optional<String> activeModel() {
            return value("activeModel");
        }

        /** The final outcome status (e.g. SUCCESS, REJECTED_NON_RECEIPT, FLAGGED_FOR_HUMAN_REVIEW). */
        public Optional<String> status() {
            return value("status");
        }
    }

    /**
     * ==========================================================================================
     * STEP 2, 3, 4, 5 & 6: BUILD & COMPILE THE STATE GRAPH
     * ==========================================================================================
     * Construct the StateGraph, attach nodes and edges, and compile into a Runnable CompiledGraph.
     */
    public CompiledGraph<State> build() throws GraphStateException {

        // --------------------------------------------------------------------------------------
        // STEP 2: Instantiate StateGraph with state constructor factory (State::new)
        // --------------------------------------------------------------------------------------
        StateGraph<State> workflow = new StateGraph<>(State::new);

        // --------------------------------------------------------------------------------------
        // STEP 3: Register Nodes (addNode)
        // --------------------------------------------------------------------------------------
        // Each node is registered with a unique string ID and a NodeAction.
        // We wrap our lambda in `node_async(...)` so LangGraph4j can handle it asynchronously.
        // The return value of each NodeAction is a Map<String, Object> with new/updated state keys.

        // Node 1: Determine initial model strategy based on character count heuristics
        workflow.addNode(NODE_DETERMINE_STRATEGY, node_async(state -> {
            String text = state.text().orElse("");
            ModelStrategy.ModelPicker strategy = text.length() > 2500
                    ? ModelStrategy.ModelPicker.BIG
                    : ModelStrategy.ModelPicker.SMALL;

            log.infof("Node [%s]: Strategy determined as [%s] (text length: %d)",
                    NODE_DETERMINE_STRATEGY, strategy, text.length());

            return Map.of("strategy", strategy);
        }));

        // Node 2: Classify document with Small Model
        workflow.addNode(NODE_CLASSIFY_SMALL, node_async(state -> {
            String text = state.text().orElse("");
            log.infof("Node [%s]: Classifying document using small model", NODE_CLASSIFY_SMALL);
            ClassificationResult result = smallAgent.classifyDocument(text);
            log.infof("Node [%s]: Small model classification result = [%s]", NODE_CLASSIFY_SMALL, result);

            return Map.of(
                    "classification", result,
                    "activeModel", "small"
            );
        }));

        // Node 3: Classify document with Large Model (direct or escalated)
        workflow.addNode(NODE_CLASSIFY_LARGE, node_async(state -> {
            String text = state.text().orElse("");
            log.infof("Node [%s]: Classifying document using large model", NODE_CLASSIFY_LARGE);
            ClassificationResult result = largeAgent.classifyDocument(text);
            log.infof("Node [%s]: Large model classification result = [%s]", NODE_CLASSIFY_LARGE, result);

            return Map.of(
                    "classification", result,
                    "activeModel", "large"
            );
        }));

        // Node 4: Extract receipt data with Small Model
        // A parse failure (invalid JSON, "12,50" into a BigDecimal, ...) is not fatal: any previous
        // Receipt is removed from the state and the conditional edge routes to the large model.
        workflow.addNode(NODE_EXTRACT_SMALL, node_async(state ->
                extract(smallAgent, "small", NODE_EXTRACT_SMALL, state.text().orElse(""))));

        // Node 5: Deterministic validation of the small model's Receipt (arithmetic, required fields)
        workflow.addNode(NODE_VALIDATE_SMALL, node_async(state -> validate(NODE_VALIDATE_SMALL, state)));

        // Node 6: Small Model Self-Assessment
        // The arithmetic is already checked in Java, so the LLM only judges whether the values are
        // grounded in the original text. The Receipt is serialized back to JSON so the model sees
        // exactly what was parsed.
        workflow.addNode(NODE_ASSESS_SMALL, node_async(state -> {
            String text = state.text().orElse("");
            String extracted = toJson(state.extractedData().orElse(null));
            log.infof("Node [%s]: Small model assessing its own extraction quality", NODE_ASSESS_SMALL);
            ExtractionConfidence confidence = smallAgent.assessExtraction(text, extracted);
            log.infof("Node [%s]: Small model confidence assessment = [%s]", NODE_ASSESS_SMALL, confidence);

            return Map.of("confidence", confidence);
        }));

        // Node 7: Extract receipt data with Large Model (fallback / retry / direct)
        workflow.addNode(NODE_EXTRACT_LARGE, node_async(state ->
                extract(largeAgent, "large", NODE_EXTRACT_LARGE, state.text().orElse(""))));

        // Node 8: Deterministic validation of the large model's Receipt
        workflow.addNode(NODE_VALIDATE_LARGE, node_async(state -> validate(NODE_VALIDATE_LARGE, state)));

        // Node 9: Terminal Node - Rejected Document (NOK)
        workflow.addNode(NODE_HANDLE_REJECTED, node_async(state -> {
            log.warnf("Node [%s]: Rejected non-receipt document", NODE_HANDLE_REJECTED);
            return Map.of("status", "REJECTED_NON_RECEIPT");
        }));

        // Node 10: Terminal Node - Flagged for Human Review
        // (large model uncertain, no parsable Receipt, or the large model's Receipt failed validation)
        workflow.addNode(NODE_HANDLE_NEEDS_REVIEW, node_async(state -> {
            log.warnf("Node [%s]: Model uncertain/rejected - flagged for human review %s",
                    NODE_HANDLE_NEEDS_REVIEW, state.validationErrors());
            return Map.of("status", "FLAGGED_FOR_HUMAN_REVIEW");
        }));

        // Node 11: Terminal Node - Successfully Extracted
        workflow.addNode(NODE_FINISH_SUCCESS, node_async(state -> {
            String model = state.activeModel().orElse("unknown");
            log.infof("Node [%s]: Extraction successful with %s model!", NODE_FINISH_SUCCESS, model);
            log.infof("Extracted with %s model:%n%s", model, toJson(state.extractedData().orElse(null)));
            return Map.of("status", "SUCCESS");
        }));

        // --------------------------------------------------------------------------------------
        // STEP 4: Define Direct / Unconditional Edges (addEdge)
        // --------------------------------------------------------------------------------------
        // Direct edges always route unconditionally from source node to target node.

        // Entry point: When the graph is invoked, start at determine_strategy
        workflow.addEdge(START, NODE_DETERMINE_STRATEGY);

        // Terminal nodes route to the special END state
        workflow.addEdge(NODE_FINISH_SUCCESS, END);
        workflow.addEdge(NODE_HANDLE_REJECTED, END);
        workflow.addEdge(NODE_HANDLE_NEEDS_REVIEW, END);

        // --------------------------------------------------------------------------------------
        // STEP 5: Define Conditional Edges (addConditionalEdges)
        // --------------------------------------------------------------------------------------
        // Conditional edges evaluate a function (EdgeAction) on the state and map the returned
        // string key to the next node name using a routing map.

        // Conditional Edge A: Branch based on strategy (SMALL vs BIG)
        workflow.addConditionalEdges(
                NODE_DETERMINE_STRATEGY,
                edge_async(state -> {
                    ModelStrategy.ModelPicker strategy = state.strategy()
                            .orElse(ModelStrategy.ModelPicker.SMALL);
                    return strategy == ModelStrategy.ModelPicker.BIG ? "BIG" : "SMALL";
                }),
                Map.of(
                        "SMALL", NODE_CLASSIFY_SMALL,
                        "BIG", NODE_CLASSIFY_LARGE
                )
        );

        // Conditional Edge B: Branch based on Small Model Classification
        // - OK -> extract with small model
        // - NOK -> reject document
        // - NEEDS_REVIEW -> escalate classification to large model
        workflow.addConditionalEdges(
                NODE_CLASSIFY_SMALL,
                edge_async(state -> {
                    ClassificationResult classification = state.classification()
                            .orElse(ClassificationResult.NOK);
                    return switch (classification) {
                        case OK -> "OK";
                        case NOK -> "NOK";
                        case NEEDS_REVIEW -> "NEEDS_REVIEW";
                    };
                }),
                Map.of(
                        "OK", NODE_EXTRACT_SMALL,
                        "NOK", NODE_HANDLE_REJECTED,
                        "NEEDS_REVIEW", NODE_CLASSIFY_LARGE
                )
        );

        // Conditional Edge C: Branch based on Large Model Classification
        // - OK -> extract with large model
        // - NOK -> reject document
        // - NEEDS_REVIEW -> flag for human review (large model also uncertain)
        workflow.addConditionalEdges(
                NODE_CLASSIFY_LARGE,
                edge_async(state -> {
                    ClassificationResult classification = state.classification()
                            .orElse(ClassificationResult.NOK);
                    return switch (classification) {
                        case OK -> "OK";
                        case NOK -> "NOK";
                        case NEEDS_REVIEW -> "NEEDS_REVIEW";
                    };
                }),
                Map.of(
                        "OK", NODE_EXTRACT_LARGE,
                        "NOK", NODE_HANDLE_REJECTED,
                        "NEEDS_REVIEW", NODE_HANDLE_NEEDS_REVIEW
                )
        );

        // Conditional Edge D: Did the small model produce a parsable Receipt?
        // - PARSED -> run deterministic validation
        // - FAILED -> retry extraction using large model
        workflow.addConditionalEdges(
                NODE_EXTRACT_SMALL,
                edge_async(state -> state.extractedData().isPresent() ? "PARSED" : "FAILED"),
                Map.of(
                        "PARSED", NODE_VALIDATE_SMALL,
                        "FAILED", NODE_EXTRACT_LARGE
                )
        );

        // Conditional Edge E: Branch based on deterministic validation of the small model's Receipt
        // - VALID -> let the small model assess grounding against the original text
        // - INVALID -> retry extraction using large model
        workflow.addConditionalEdges(
                NODE_VALIDATE_SMALL,
                edge_async(state -> state.validationErrors().isEmpty() ? "VALID" : "INVALID"),
                Map.of(
                        "VALID", NODE_ASSESS_SMALL,
                        "INVALID", NODE_EXTRACT_LARGE
                )
        );

        // Conditional Edge F: Did the large model produce a parsable Receipt?
        // - PARSED -> run deterministic validation
        // - FAILED -> no model left to escalate to, flag for human review
        workflow.addConditionalEdges(
                NODE_EXTRACT_LARGE,
                edge_async(state -> state.extractedData().isPresent() ? "PARSED" : "FAILED"),
                Map.of(
                        "PARSED", NODE_VALIDATE_LARGE,
                        "FAILED", NODE_HANDLE_NEEDS_REVIEW
                )
        );

        // Conditional Edge G: Branch based on deterministic validation of the large model's Receipt
        // - VALID -> finish!
        // - INVALID -> flag for human review (the Receipt stays in the state for the reviewer)
        workflow.addConditionalEdges(
                NODE_VALIDATE_LARGE,
                edge_async(state -> state.validationErrors().isEmpty() ? "VALID" : "INVALID"),
                Map.of(
                        "VALID", NODE_FINISH_SUCCESS,
                        "INVALID", NODE_HANDLE_NEEDS_REVIEW
                )
        );

        // Conditional Edge H: Branch based on Small Model Self-Assessment Quality
        // - COMPLETE -> extraction is accurate and complete, finish!
        // - INCOMPLETE -> retry extraction using large model
        workflow.addConditionalEdges(
                NODE_ASSESS_SMALL,
                edge_async(state -> {
                    ExtractionConfidence confidence = state.confidence()
                            .orElse(ExtractionConfidence.INCOMPLETE);
                    return confidence == ExtractionConfidence.COMPLETE ? "COMPLETE" : "INCOMPLETE";
                }),
                Map.of(
                        "COMPLETE", NODE_FINISH_SUCCESS,
                        "INCOMPLETE", NODE_EXTRACT_LARGE
                )
        );

        // --------------------------------------------------------------------------------------
        // STEP 6: Compile the Graph (compile())
        // --------------------------------------------------------------------------------------
        // Validates graph integrity (unreachable nodes, missing targets, cycles) and returns
        // an immutable, thread-safe executable graph.
        return workflow.compile();
    }

    /** Runs the extraction; on a parse failure any previous Receipt is removed from the state. */
    private static Map<String, Object> extract(ExtractionAgent agent, String model, String node, String text) {
        log.infof("Node [%s]: Extracting receipt data using %s model", node, model);
        try {
            Receipt receipt = agent.extract(text);
            if (receipt != null) {
                return Map.of("extractedData", receipt, "activeModel", model);
            }
            log.warnf("Node [%s]: %s model returned no receipt", node, model);
        } catch (RuntimeException e) {
            log.warnf(e, "Node [%s]: %s model output could not be parsed into a Receipt", node, model);
        }
        return Map.of("extractedData", AgentState.MARK_FOR_REMOVAL, "activeModel", model);
    }

    private static Map<String, Object> validate(String node, State state) {
        List<String> errors = ReceiptValidator.validate(state.extractedData().orElse(null));
        if (errors.isEmpty()) {
            log.infof("Node [%s]: Receipt passed validation", node);
        } else {
            log.warnf("Node [%s]: Receipt failed validation: %s", node, errors);
        }
        return Map.of("validationErrors", errors);
    }

    private static String toJson(Receipt receipt) {
        try {
            return JSON.writeValueAsString(receipt);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize receipt", e);
        }
    }
}
