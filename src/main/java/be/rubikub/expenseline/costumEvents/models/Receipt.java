package be.rubikub.expenseline.costumEvents.models;

import dev.langchain4j.model.output.structured.Description;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Description("the POJO to hold the data")
public record Receipt(
        @Description("the date of purchase, use DD/MM/YYYY and append HH:MM only if a time is present")
        String Date,
        @Description("the vendor name")
        String Vendor,
        @Description("a short description of the document")
        String Description,
        @Description("the code of the customer if present, otherwise null")
        String CostumerCode,
        @Description("the name of the customer/employee, otherwise null")
        String CustomerName,
        @Description("the contact info of the vendor if present, otherwise null")
        String VendorContactInfo,
        @Description("the address of the vendor if present, otherwise null")
        String VendorAddress,
        @Description("the VAT number of the vendor if present, otherwise null")
        String VendorVATNumber,
        @Description("the total amount of the receipt excluding VAT")
        BigDecimal TotalamountExclVat,
        @Description("the total amount of the receipt including VAT, this is the amount that was paid")
        BigDecimal TotalamountInclVat,
        @Description("the items of the receipt")
        List<ReceiptItem> items) implements Serializable {

    /** Multi-line, human-readable representation for console debugging. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Receipt\n")
                .append("  Vendor            : ").append(Vendor).append('\n')
                .append("  Date              : ").append(Date).append('\n')
                .append("  Description       : ").append(Description).append('\n')
                .append("  Customer name     : ").append(CustomerName).append('\n')
                .append("  Customer code     : ").append(CostumerCode).append('\n')
                .append("  Vendor address    : ").append(VendorAddress).append('\n')
                .append("  Vendor contact    : ").append(VendorContactInfo).append('\n')
                .append("  Vendor VAT number : ").append(VendorVATNumber).append('\n')
                .append("  Total excl. VAT   : ").append(TotalamountExclVat).append('\n')
                .append("  Total incl. VAT   : ").append(TotalamountInclVat).append('\n');

        if (items == null || items.isEmpty()) {
            sb.append("  Items             : none");
        } else {
            sb.append("  Items (").append(items.size()).append("):");
            for (ReceiptItem item : items) {
                sb.append("\n    - ").append(item);
            }
        }
        return sb.toString();
    }
}
