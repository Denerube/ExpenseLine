package be.rubikub.expenseline.extraction.internal;

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
    }

    @Bean
    @Qualifier("largeAgent")
    ExtractionAgent largeAgent(@Qualifier("largeModel") ChatModel largeModel) {
        return AiServices.create(ExtractionAgent.class, largeModel);
    }
}
