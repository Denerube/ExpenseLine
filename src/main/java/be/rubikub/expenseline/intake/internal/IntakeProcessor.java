package be.rubikub.expenseline.intake.internal;

import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.*;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import ai.docling.serve.client.DoclingServeClientBuilderFactory;
import be.rubikub.expenseline.costumEvents.TextIntakeEvent;
import be.rubikub.expenseline.intake.IntakeProcessorPublic;

import dev.langchain4j.data.document.Document;
import jakarta.validation.constraints.Null;
import org.apache.fontbox.cff.CFFParser;
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
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.DoclingServeApi;




import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Service
class IntakeProcessor implements IntakeProcessorPublic {

    private final Tika tika = new Tika();
    private final ApplicationEventPublisher events;

    IntakeProcessor(ApplicationEventPublisher events) {
        this.events = events;
    }

    DoclingServeApi doclingServeApi = DoclingServeClientBuilderFactory.newBuilder()
            .baseUrl("http://127.0.0.1:5001/")
            .build();




//    @Override
//    public void processFile(byte[] bytes, String fileType,String fileName) {
//        IO.println("test %s".formatted(fileType));
//        IO.println(bytes.length);
//        String text = "";
//        try {
//            text = extractText(bytes, fileType);
//        } catch (TikaException | IOException | SAXException e) {
//            IO.println("Error processing PDF: " + e.getMessage());
//            text = "";
//        }
//        if (!text.isBlank()) {
//            publicExtractedTest(text,fileType);
//        }
//    }

    @Override
    public void processFile(byte[] bytes, String fileType,String fileName){
        DoclingServeApi doclingServeApi = DoclingServeClientBuilderFactory.newBuilder()
                .baseUrl("http://127.0.0.1:5001/")
                .build();

        ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                .source(FileSource.builder()
                        .filename(fileName)
                        .base64String(Base64.getEncoder().encodeToString(bytes))
                        .build())
                .options(ConvertDocumentOptions.builder()
                        .toFormat(OutputFormat.DOCTAGS)
                        .tableMode(TableFormerMode.ACCURATE)
                        .vlmPipelineModel(VlmModelType.GRANITE_VISION)
                        .build())
                .build();
        InBodyConvertDocumentResponse response = (InBodyConvertDocumentResponse) doclingServeApi.convertSource(request);
        System.out.println(response.getDocument().getDoctagsContent());
        publicExtractedTest(response.getDocument().getDoctagsContent(), fileType,null);
    }
    private void publicExtractedTest(String text, String fileType, Document document){
        events.publishEvent(new TextIntakeEvent(text,fileType,document));
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
