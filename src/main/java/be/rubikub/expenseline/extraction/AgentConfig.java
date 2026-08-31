package be.rubikub.expenseline.extraction;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AgentConfig {

    @Bean
    @Qualifier("smallAgent")
    ExtractionAgent smallAgent(@Qualifier("smallModel") ChatModel smallModel) {
        return AiServices.create(ExtractionAgent.class, smallModel);
//        return AiServices.builder(ExtractionAgent.class)
//                .chatModel(smallModel)
//                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(1))
//                .build();

    }

    @Bean
    @Qualifier("largeAgent")
    ExtractionAgent largeAgent(@Qualifier("largeModel") ChatModel largeModel) {
        return AiServices.create(ExtractionAgent.class, largeModel);
    }
}
