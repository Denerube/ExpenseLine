package be.rubikub.expenseline.costumEvents.models;


import java.io.Serializable;
import java.math.BigDecimal;
import dev.langchain4j.model.output.structured.Description;


public record ReceiptItem(@Description("the quantity of this item (number of units)")
                          BigDecimal amount,
                          @Description("the VAT amount of this line")
                          BigDecimal VAT,
                          @Description("the VAT percentage in int")
                          int VATpercentage,
                          @Description("the date of the item, use DD/MM/YYYY and append HH:MM only if a time is present")
                          String Date,
                          @Description("a short description of the item")
                          String Description,
                          @Description("the name of the item")
                          String ItemName,
                          @Description("the line total of the item excluding VAT")
                          BigDecimal amountExclVat,
                          @Description("the line total of the item including VAT")
                          BigDecimal amountInclVat
                          ) implements Serializable {

    /** Single-line representation, used per line inside {@link Receipt#toString()}. */
    @Override
    public String toString() {
        return amount + " x " + ItemName
                + " | excl. VAT " + amountExclVat
                + " | VAT " + VAT + " (" + VATpercentage + "%)"
                + " | incl. VAT " + amountInclVat
                + (Description != null ? " | " + Description : "")
                + (Date != null ? " | " + Date : "");
    }
}
