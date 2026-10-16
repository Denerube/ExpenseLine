package be.rubikub.expenseline.extraction;

import be.rubikub.expenseline.costumEvents.TextIntakeEvent;

public class ModelStrategy {

    private final TextIntakeEvent textIntakeEvent;
    private ModelPicker strategy = ModelPicker.SMALL;

    public ModelPicker getStrategy() {
        return this.strategy;
    }

    public ModelStrategy(TextIntakeEvent textIntakeEvent) {
        this.textIntakeEvent = textIntakeEvent;
        this.determineStrategy();
    }
    private void determineStrategy() {
        strategy = ModelPicker.SMALL;
        if (textIntakeEvent.getCharCount() > 5000) {
            strategy = ModelPicker.BIG;
        }
        if (textIntakeEvent.getCharCount() > 2500) {
            strategy = ModelPicker.BIG;
        }
    }

    public enum ModelPicker {
        SMALL,
        BIG,
    }

}
