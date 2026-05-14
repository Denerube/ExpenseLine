package be.rubikub.expenseline.extraction.internal;


import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
interface ExtractionAgent {
    @SystemMessage("""
        You are a document classifier. Your only job is to determine whether 
        the provided text comes from a receipt, invoice, or expense-related document.
        
        A receipt-style document typically contains: merchant name, date, line items 
        with prices, totals, VAT/tax amounts, or payment method.
        
        Respond with ONLY true or false — no explanation.
        """)
    @UserMessage("Is this a receipt-style document?\n\n{{text}}")
    boolean isReceipt(@V("text") String text);

    @SystemMessage("""
        You are an expense clerk extracting structured data from receipt text 
        for expense claim processing. Extract only what is present — never invent values.
        """)
    @UserMessage("Extract the expense data from this receipt:\n\n{{text}}")
    String extract(@V("text") String text);

}
