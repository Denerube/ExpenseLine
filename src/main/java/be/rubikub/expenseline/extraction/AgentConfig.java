package be.rubikub.expenseline.extraction;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.atmosphere.inject.annotation.ApplicationScoped;


@ApplicationScoped
class AgentConfig {

    @Produces
    @Named("smallAgent")
    ExtractionAgent smallAgent(@Named("smallModel") ChatModel smallModel) {
        return AiServices.create(ExtractionAgent.class, smallModel);
//        return AiServices.builder(ExtractionAgent.class)
//                .chatModel(smallModel)
//                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(1))
//                .build();

    }

    @Produces
    @Named("largeAgent")
    ExtractionAgent largeAgent(@Named("largeModel") ChatModel largeModel) {
        return AiServices.create(ExtractionAgent.class, largeModel);
    }
}
