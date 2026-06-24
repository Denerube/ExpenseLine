package be.rubikub.expenseline.costumEvents.models;


import java.math.BigDecimal;
import java.util.List;

public record ReceiptItem(BigDecimal amount,
                          @Description("the VAT")
                          BigDecimal VAT,
                          @Description("the VAT percentage in int")
                          int VATpercentage,
                          @Description("the date of the item, use DD/MM/YYYY HH:MM")
                          String Date,
                          @Description("a short description of the item")
                          String Description,
                          @Description("the name of the item")
                          String ItemName,
                          @Description("the amount of the item excluding VAT")
                          BigDecimal amountExclVat,
                          @Description("the amount of the item including VAT")
                          BigDecimal amountInclVat
                          ) {
}
