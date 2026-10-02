package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.ResponseCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OpenAiService {
    private final OpenAIClient client;
    public OpenAiService(@Value("${app.openai.api-key:}") String apiKey,
                         @Value("${app.openai.enabled:false}") boolean enabled) {
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException("AI 기능을 활성화하려면 OPENAI_API_KEY가 필요합니다.");
        }
        this.client = enabled ? OpenAIOkHttpClient.builder().apiKey(apiKey).build() : null;
    }
    public boolean isEnabled() { return client != null; }
    public String reply(List<Message> recent, String userText) {
        if (!isEnabled()) throw new IllegalStateException("AI is disabled");
        ResponseCreateParams params = ResponseCreateParams.builder().model("gpt-4.1-mini")
                .input(buildPrompt(recent, userText)).build();
        return OpenAiResponse.from(client.responses().create(params)).outputText();
    }
    private String buildPrompt(List<Message> recent, String userText) {
        StringBuilder text = new StringBuilder("너는 메신저 채팅방의 AI 비서다. 한국어로 간결하게 답해라.\n\n");
        for (Message message : recent) {
            text.append(message.senderName == null ? message.senderId : message.senderName)
                    .append(": ").append(message.content == null ? "" : message.content).append('\n');
        }
        return text.append("\n질문: ").append(userText).toString();
    }
}
