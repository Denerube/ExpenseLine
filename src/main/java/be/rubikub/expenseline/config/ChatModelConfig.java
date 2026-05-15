package be.rubikub.expenseline.config;


import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.http.client.jdk.JdkHttpClient;


import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ChatModelConfig {

    @Bean
    public ChatModel chatLanguageModel() {

        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10));

        var langchainHttpClientBuilder = JdkHttpClient.builder()
                .httpClientBuilder(httpClientBuilder);

        ChatModel chatMode = OpenAiChatModel.builder()
                .baseUrl("http://127.0.0.1:1234/v1")
                .modelName("ministral-3-3b")
                .apiKey("lm-studio")
                .timeout(Duration.ofSeconds(60))
                .httpClientBuilder(langchainHttpClientBuilder)
                .logRequests(true)
                .logResponses(true)
                .temperature(0.0)
                .build();
        return chatMode;

    }
}