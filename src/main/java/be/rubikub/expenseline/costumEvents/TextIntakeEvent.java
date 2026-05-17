package be.rubikub.expenseline.costumEvents;

import dev.langchain4j.data.document.Document;

public record TextIntakeEvent(String text,
                              String originalFileType,
                              Document doc) {

    public int getCharCount(){
        return text.length();
    }

    public int getWordtCount(){
        return text.split("\\s+").length;
    }


}

