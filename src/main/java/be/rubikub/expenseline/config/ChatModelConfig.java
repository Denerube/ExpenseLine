package be.rubikub.expenseline.config;


import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import org.atmosphere.inject.annotation.ApplicationScoped;


import java.net.http.HttpClient;
import java.time.Duration;

@ApplicationScoped
public class ChatModelConfig {

    @Produces
    @Default
    @Named("smallModel")
    public ChatModel smallModel() {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10));

        var langchainHttpClientBuilder = JdkHttpClient.builder()
                .httpClientBuilder(httpClientBuilder);

        return OpenAiChatModel.builder()
                .baseUrl("http://127.0.0.1:1234/v1")
                .modelName("mistralai/ministral-3-3b")
                .apiKey("lm-studio")
                .timeout(Duration.ofSeconds(30))
                .httpClientBuilder(langchainHttpClientBuilder)
                .logRequests(true)
                .logResponses(true)
                .temperature(0.0)
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();
    }

    @Produces
    @Named("largeModel")
    public ChatModel largeModel() {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10));

        var langchainHttpClientBuilder = JdkHttpClient.builder()
                .httpClientBuilder(httpClientBuilder);

        return OpenAiChatModel.builder()
                .baseUrl("http://127.0.0.1:1234/v1")
                .modelName("mistralai/ministral-3-14b-reasoning")
                .apiKey("lm-studio")
                .timeout(Duration.ofSeconds(60))
                .httpClientBuilder(langchainHttpClientBuilder)
                .logRequests(true)
                .logResponses(true)
                .temperature(0.0)
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();
    }
}