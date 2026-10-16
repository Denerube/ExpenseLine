package be.rubikub.expenseline.extraction;


import be.rubikub.expenseline.costumEvents.models.Receipt;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Stateless agent: every uploaded document is processed independently, so no chat memory is used.
 */
interface ExtractionAgent {

    @SystemMessage("""
        You are a document classifier. Determine if this is a receipt/invoice/expense/proof of payment or purchase document.

        Look for: merchant name, date, items, prices, totals, VAT/BTW/tax (any language).

        Respond with EXACTLY one of these values (no formatting, no markdown, no extra text):

        OK
        NOK
        NEEDS_REVIEW
        """)
    @UserMessage("Classify this document:\n\n{{text}}")
    ClassificationResult classifyDocument(@V("text") String text);

    @SystemMessage("""
        You extract structured data from receipts, invoices and other proofs of purchase.

        Rules:
        - Use exact values from the text, never guess or invent values
        - If a field is not present in the text, set it to null
        - Accept any language
        - Amounts are plain numbers with a dot as decimal separator (e.g. 12.50, not 12,50 or EUR 12.50)
        """)
    @UserMessage("Extract data from:\n\n{{text}}")
    Receipt extract(@V("text") String text);

    @SystemMessage("""
        You are a quality assessor. Compare the extracted data against the original document text.
        The arithmetic (totals, VAT sums) has already been verified, focus on whether the values are
        actually present in the original text.

        Evaluate if the extraction is:
        - COMPLETE: vendor, date and total amount are present and every extracted value can be found in
          the original text
        - INCOMPLETE: a required value is null, or any value is not supported by the original text
          (invented, taken from the wrong place, or misread)

        Respond with EXACTLY one word (no formatting, no markdown, no extra text):
        COMPLETE
        INCOMPLETE
        """)
    @UserMessage("""
        Original document text:
        {{originalText}}

        Extracted data:
        {{extractedData}}

        Is this extraction complete?
        """)
    ExtractionConfidence assessExtraction(
            @V("originalText") String originalText,
            @V("extractedData") String extractedData
    );

}
