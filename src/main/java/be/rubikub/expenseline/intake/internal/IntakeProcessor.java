package be.rubikub.expenseline.intake.internal;

import be.rubikub.expenseline.intake.IntakeProcessorPublic;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
class IntakeProcessor implements IntakeProcessorPublic {

    private final Tika tika = new Tika();

    @Override
    public void processFile(byte[] bytes, String fileType) {
        IO.println("test %s".formatted(fileType));
        IO.println(bytes.length);
        String text = "";
        try {
            text = extractText(bytes, fileType);
        } catch (TikaException | IOException | SAXException e) {
            IO.println("Error processing PDF: " + e.getMessage());
        }
        IO.println(text);
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
