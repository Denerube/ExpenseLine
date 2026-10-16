package be.rubikub.expenseline.extraction;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.jboss.logging.Logger;

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
 *                    +-------------------+
 *                    |      [START]      |
 *                    +---------+---------+
 *                              |
 *                              v
 *                   +---------------------+
 *                   | determine_strategy  | (inspects character count)
 *                   +----------+----------+
 *                              |
 *             +----------------+----------------+
 *             | strategy == SMALL               | strategy == BIG
 *             v                                 v
 *   +-------------------+             +-------------------+
 *   |   classify_small  |             |   classify_large  |<----------------+
 *   +---------+---------+             +---------+---------+                 |
 *             |                                 |                           |
 *      +------+------+                   +------+------+                    |
 *      |      |      |                   |      |      |                    |
 *     NOK     OK  NEEDS_REVIEW          NOK     OK  NEEDS_REVIEW            |
 *      |      |      |                   |      |      |                    |
 *      |      |      +-------------------+      |      v                    |
 *      |      |    (escalate to large)          |  +---------------------+  |
 *      |      v                                 |  | handle_needs_review |  |
 *      |  +---------------+                     |  +----------+----------+  |
 *      |  | extract_small |                     |             |             |
 *      |  +-------+-------+                     |             v             |
 *      |          |                             |          [ END ]          |
 *      |          v                             |                           |
 *      |  +---------------+                     |                           |
 *      |  | assess_small  |                     |                           |
 *      |  +-------+-------+                     |                           |
 *      |          |                             |                           |
 *      |     +----+----+                        |                           |
 *      | COMPLETE     INCOMPLETE (retry)        |                           |
 *      |     |         +------------------------+                           |
 *      |     |                                  v                           |
 *      |     |                        +-------------------+                 |
 *      |     |                        |   extract_large   |                 |
 *      |     |                        +---------+---------+                 |
 *      |     |                                  |                           |
 *      |     +----------------+                 |                           |
 *      |                      |                 |                           |
 *      v                      v                 v                           |
 *  +-----------------+  +-------------------------------+                   |
 *  | handle_rejected |  |        finish_success         |                   |
 *  +--------+--------+  +---------------+---------------+                   |
 *           |                           |                                   |
 *           v                           v                                   |
 *        [ END ]                     [ END ]                                |
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
    public static final String NODE_ASSESS_SMALL = "assess_small";
    public static final String NODE_EXTRACT_LARGE = "extract_large";
    public static final String NODE_HANDLE_REJECTED = "handle_rejected";
    public static final String NODE_HANDLE_NEEDS_REVIEW = "handle_needs_review";
    public static final String NODE_FINISH_SUCCESS = "finish_success";

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

        /** The extracted receipt JSON data. */
        public Optional<String> extractedData() {
            return value("extractedData");
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
        workflow.addNode(NODE_EXTRACT_SMALL, node_async(state -> {
            String text = state.text().orElse("");
            log.infof("Node [%s]: Extracting receipt data using small model", NODE_EXTRACT_SMALL);
            String extracted = smallAgent.extract(text);

            return Map.of(
                    "extractedData", extracted,
                    "activeModel", "small"
            );
        }));

        // Node 5: Small Model Self-Assessment
        workflow.addNode(NODE_ASSESS_SMALL, node_async(state -> {
            String text = state.text().orElse("");
            String extracted = state.extractedData().orElse("");
            log.infof("Node [%s]: Small model assessing its own extraction quality", NODE_ASSESS_SMALL);
            ExtractionConfidence confidence = smallAgent.assessExtraction(text, extracted);
            log.infof("Node [%s]: Small model confidence assessment = [%s]", NODE_ASSESS_SMALL, confidence);

            return Map.of("confidence", confidence);
        }));

        // Node 6: Extract receipt data with Large Model (fallback / retry / direct)
        workflow.addNode(NODE_EXTRACT_LARGE, node_async(state -> {
            String text = state.text().orElse("");
            log.infof("Node [%s]: Extracting receipt data using large model", NODE_EXTRACT_LARGE);
            String extracted = largeAgent.extract(text);

            return Map.of(
                    "extractedData", extracted,
                    "activeModel", "large"
            );
        }));

        // Node 7: Terminal Node - Rejected Document (NOK)
        workflow.addNode(NODE_HANDLE_REJECTED, node_async(state -> {
            log.warnf("Node [%s]: Rejected non-receipt document", NODE_HANDLE_REJECTED);
            return Map.of("status", "REJECTED_NON_RECEIPT");
        }));

        // Node 8: Terminal Node - Flagged for Human Review (NEEDS_REVIEW from large model)
        workflow.addNode(NODE_HANDLE_NEEDS_REVIEW, node_async(state -> {
            log.warnf("Node [%s]: Model uncertain/rejected - flagged for human review", NODE_HANDLE_NEEDS_REVIEW);
            return Map.of("status", "FLAGGED_FOR_HUMAN_REVIEW");
        }));

        // Node 9: Terminal Node - Successfully Extracted
        workflow.addNode(NODE_FINISH_SUCCESS, node_async(state -> {
            String model = state.activeModel().orElse("unknown");
            String data = state.extractedData().orElse("");
            log.infof("Node [%s]: Extraction successful with %s model!", NODE_FINISH_SUCCESS, model);
            System.out.println("Extracted with " + model + " model:");
            System.out.println(data);
            return Map.of("status", "SUCCESS");
        }));

        // --------------------------------------------------------------------------------------
        // STEP 4: Define Direct / Unconditional Edges (addEdge)
        // --------------------------------------------------------------------------------------
        // Direct edges always route unconditionally from source node to target node.

        // Entry point: When the graph is invoked, start at determine_strategy
        workflow.addEdge(START, NODE_DETERMINE_STRATEGY);

        // After extracting with small model, always run self-assessment
        workflow.addEdge(NODE_EXTRACT_SMALL, NODE_ASSESS_SMALL);

        // After extracting with large model, transition directly to finish_success
        workflow.addEdge(NODE_EXTRACT_LARGE, NODE_FINISH_SUCCESS);

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

        // Conditional Edge D: Branch based on Small Model Self-Assessment Quality
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
}
