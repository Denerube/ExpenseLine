package be.rubikub.expenseline.extraction;

import be.rubikub.expenseline.costumEvents.TextExtractedEvent;

public class ModelStrategy {

    private final TextExtractedEvent textExtractedEvent;
    private ModelPicker strategy = ModelPicker.SMALL;

    public ModelPicker getStrategy() {
        return this.strategy;
    }

    public ModelStrategy(TextExtractedEvent textExtractedEvent) {
        this.textExtractedEvent = textExtractedEvent;
        this.determineStrategy();
    }

    private void determineStrategy() {
        strategy = ModelPicker.SMALL;
        if (textExtractedEvent.getCharCount() > 5000) {
            strategy = ModelPicker.BIG;
        }
        if (textExtractedEvent.getCharCount() > 2500) {
            strategy = ModelPicker.BIG;
        }
    }

    public enum ModelPicker {
        SMALL,
        BIG,
    }

}
