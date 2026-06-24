package be.rubikub.expenseline.costumEvents;

public record TextIntakeEvent(String text, String originalFileType) {

    public int getCharCount(){
        return text.length();
    }

    public int getWordtCount(){
        return text.split("\\s+").length;
    }

}

