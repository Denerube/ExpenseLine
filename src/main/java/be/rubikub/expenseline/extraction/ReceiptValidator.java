package be.rubikub.expenseline.extraction;

import be.rubikub.expenseline.costumEvents.models.Receipt;
import be.rubikub.expenseline.costumEvents.models.ReceiptItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic checks on an extracted {@link Receipt}.
 * Arithmetic and required fields are verified in code, so the LLM assessment only has to
 * judge whether the values are grounded in the original text.
 */
final class ReceiptValidator {

    /** Allowed rounding difference when comparing amounts (receipts round per line). */
    static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ReceiptValidator() {
    }

    /** @return a list of problems, empty when the receipt passes all checks */
    static List<String> validate(Receipt receipt) {
        List<String> problems = new ArrayList<>();
        if (receipt == null) {
            problems.add("no receipt extracted");
            return problems;
        }

        if (isBlank(receipt.Vendor())) {
            problems.add("vendor is missing");
        }
        if (isBlank(receipt.Date())) {
            problems.add("date is missing");
        } else if (!isValidDate(receipt.Date())) {
            problems.add("date '" + receipt.Date() + "' is not in DD/MM/YYYY [HH:MM] format");
        }

        BigDecimal inclVat = receipt.TotalamountInclVat();
        BigDecimal exclVat = receipt.TotalamountExclVat();
        if (inclVat == null) {
            problems.add("total amount incl. VAT is missing");
        } else if (exclVat != null && exclVat.compareTo(inclVat) > 0) {
            problems.add("total excl. VAT (" + exclVat + ") is greater than total incl. VAT (" + inclVat + ")");
        }

        List<ReceiptItem> items = receipt.items() == null ? List.of() : receipt.items();
        for (ReceiptItem item : items) {
            if (item == null) {
                continue;
            }
            if (item.amountExclVat() != null && item.VAT() != null && item.amountInclVat() != null
                    && !approxEquals(item.amountExclVat().add(item.VAT()), item.amountInclVat())) {
                problems.add("item '" + item.ItemName() + "': excl. VAT " + item.amountExclVat()
                        + " + VAT " + item.VAT() + " != incl. VAT " + item.amountInclVat());
            }
        }

        boolean allLinesHaveTotal = !items.isEmpty()
                && items.stream().allMatch(i -> i != null && i.amountInclVat() != null);
        if (inclVat != null && allLinesHaveTotal) {
            BigDecimal sum = items.stream()
                    .map(ReceiptItem::amountInclVat)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!approxEquals(sum, inclVat)) {
                problems.add("sum of item totals (" + sum + ") does not match total incl. VAT (" + inclVat + ")");
            }
        }

        return problems;
    }

    private static boolean approxEquals(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCE) <= 0;
    }

    private static boolean isValidDate(String value) {
        String trimmed = value.trim();
        try {
            LocalDateTime.parse(trimmed, DATE_TIME);
            return true;
        } catch (DateTimeParseException ignored) {
            // fall through to date-only format
        }
        try {
            LocalDate.parse(trimmed, DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
