package be.rubikub.expenseline.extraction;

import be.rubikub.expenseline.costumEvents.models.Receipt;
import be.rubikub.expenseline.costumEvents.models.ReceiptItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptValidatorTest {

    private static ReceiptItem item(String name, String excl, String vat, String incl) {
        return new ReceiptItem(BigDecimal.ONE, new BigDecimal(vat), 21, null, null, name,
                new BigDecimal(excl), new BigDecimal(incl));
    }

    private static Receipt receipt(String date, String excl, String incl, List<ReceiptItem> items) {
        return new Receipt(date, "Shop", null, null, null, null, null, null,
                excl == null ? null : new BigDecimal(excl), incl == null ? null : new BigDecimal(incl), items);
    }

    @Test
    void validReceiptHasNoProblems() {
        Receipt r = receipt("16/10/2026 14:05", "12.40", "15.00", List.of(
                item("Coffee", "4.13", "0.87", "5.00"),
                item("Cake", "8.26", "1.74", "10.00")));
        assertEquals(List.of(), ReceiptValidator.validate(r));
    }

    @Test
    void itemSumMismatchIsReported() {
        Receipt r = receipt("16/10/2026", null, "20.00", List.of(item("Coffee", "4.13", "0.87", "5.00")));
        List<String> problems = ReceiptValidator.validate(r);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("sum of item totals"));
    }

    @Test
    void itemVatMismatchIsReported() {
        Receipt r = receipt("16/10/2026", null, "5.00", List.of(item("Coffee", "4.13", "1.50", "5.00")));
        assertTrue(ReceiptValidator.validate(r).stream().anyMatch(p -> p.contains("Coffee")));
    }

    @Test
    void missingRequiredFieldsAndBadDateAreReported() {
        Receipt r = new Receipt("2026-10-16", null, null, null, null, null, null, null, null, null, null);
        List<String> problems = ReceiptValidator.validate(r);
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void nullReceiptIsInvalid() {
        assertFalse(ReceiptValidator.validate(null).isEmpty());
    }
}
