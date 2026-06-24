package be.rubikub.expenseline.costumEvents.models;

import dev.langchain4j.model.output.structured.Description;
import org.apache.james.mime4j.dom.datetime.DateTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Description("the POJO to hold the data")
public record Receipt(
        @Description("the total amount of the receipt")
        BigDecimal Totalamount,
        @Description("the date of purchase, use DD/MM/YYYY HH:MM")
        String Date,
        @Description("the vendor name")
        String Vendor,
        @Description("a short description of the document")
        String Description,
        @Description("the code of the customer if present")
        Optional<String> CostumerCode,
        @Description("the name of the customer/employee")
        String CustomerName,
        @Description("the contact info of the vendor if present")
        Optional<String> VendorContactInfo,
        @Description("the address of the vendor if present")
        Optional<String> VendorAddress,
        @Description("the VAT number of the vendor if present")
        Optional<String> VendorVATNumber,
        @Description("the total amount of the receipt excluding VAT")
        BigDecimal TotalamountExclVat,
        @Description("the total amount of the receipt including VAT")
        BigDecimal TotalamountInclVat,
        @Description("the items of the receipt")
        List<ReceiptItem> items) {
    public static final Instant DECLARATION_DATE = Instant.now();

}
