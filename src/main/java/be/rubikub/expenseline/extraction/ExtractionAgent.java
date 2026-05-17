package be.rubikub.expenseline.extraction;


import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

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
        Extract receipt data in JSON format. Find these fields:

        Required: merchant, date, total,items,amount of items,VAT,customer_name
        Optional: customer_number, store_number,bank_details,metadata

        Rules:
        - Use exact values from the text
        - If a field is missing, omit it from JSON
        - Accept any language
        - Use ISO date format (YYYY-MM-DD) when possible

        Output valid JSON only.
        """)
    @UserMessage("Extract data from:\n\n{{text}}")
    String extract(@V("text") String text);

    @SystemMessage("""
        You are a quality assessor. Review the extracted data and determine if it contains
        the minimum required information for expense processing.

        Required fields: merchant/vendor name, date, total amount
        Recommended fields: VAT/BTW, line items with prices

        Evaluate if the extraction is:
        - COMPLETE: All required fields present and values look correct, pay extra attention to the price and if it matches the total amount of items times price per item
        also pay attention the VAT is correct
        - INCOMPLETE: Missing required fields or values seem incorrect/ambiguous

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
