package be.rubikub.expenseline.web;

import be.rubikub.expenseline.intake.IntakeProcessorPublic;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.InMemoryUploadHandler;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.server.streams.UploadMetadata;
import org.springframework.stereotype.Component;
import org.springframework.web.service.invoker.HttpRequestValues;

import java.io.File;


@Route()
@Component
public class MainView extends VerticalLayout {

    private final IntakeProcessorPublic intakeProcessor;

    InMemoryUploadHandler inMemoryHandler = UploadHandler
            .inMemory((metadata, data) -> {
                processFile(data, metadata);
            });
    public MainView(IntakeProcessorPublic intakeProcessor){
        this.intakeProcessor = intakeProcessor;
        add(new Text("welcome to MainView"));
        add(new Upload(inMemoryHandler));
    }
    private void processFile(byte[] data, UploadMetadata metadata){
        intakeProcessor.processFile(data, metadata.contentType());
    }
}
