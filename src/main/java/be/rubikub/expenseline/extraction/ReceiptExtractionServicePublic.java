package be.rubikub.expenseline.extraction;

import java.util.Optional;

public interface ReceiptExtractionServicePublic {
    public Optional<String> process(String text);
}
