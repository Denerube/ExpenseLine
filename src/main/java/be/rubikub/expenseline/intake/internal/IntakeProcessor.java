package be.rubikub.expenseline.intake.internal;

import be.rubikub.expenseline.costumEvents.TextExtractedEvent;
import be.rubikub.expenseline.intake.IntakeProcessorPublic;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
class IntakeProcessor implements IntakeProcessorPublic {

    private final Tika tika = new Tika();
    private final ApplicationEventPublisher events;

    IntakeProcessor(ApplicationEventPublisher events) {
        this.events = events;
    }


    @Override
    public void processFile(byte[] bytes, String fileType) {
        IO.println("test %s".formatted(fileType));
        IO.println(bytes.length);
        String text = "";
        try {
            text = extractText(bytes, fileType);
        } catch (TikaException | IOException | SAXException e) {
            IO.println("Error processing PDF: " + e.getMessage());
            text = "";
        }
        if (!text.isBlank()) {
            publicExtractedTest(text,fileType);
        }
    }

    private void publicExtractedTest(String text, String fileType){
        events.publishEvent(new TextExtractedEvent(text,"test"));
    }

     private String extractText (byte[] bytes, String contentType) throws TikaException, IOException, SAXException  {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, contentType);
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        parser.parse(new ByteArrayInputStream(bytes), handler, metadata, context);
        return handler.toString();
    }


}
