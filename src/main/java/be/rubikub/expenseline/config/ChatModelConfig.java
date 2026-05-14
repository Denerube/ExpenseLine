package be.rubikub.expenseline.config;


import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModelConfig {

    @Bean
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl("http://127.0.0.1:1234/v1")
                .apiKey("not-needed")
                .modelName("ministral-3-3b")
                .logRequests(true)
                .logResponses(true)
                .temperature(0.0)
                .build();
    }
}