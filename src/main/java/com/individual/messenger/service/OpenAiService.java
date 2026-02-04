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

    // ✅ fromEnv() 제거, 스프링 설정으로 키 주입
    public OpenAiService(@Value("${app.openai.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API Key가 없습니다. " +
                            "환경변수 OPENAI_API_KEY 또는 application.yml의 openai.api-key를 설정하세요."
            );
        }

        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public String reply(List<Message> recent, String userText) {
        String prompt = buildPrompt(recent, userText);

        ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-4.1-mini")
                .input(prompt)
                .build();

        OpenAiResponse res = OpenAiResponse.from(client.responses().create(params));
        return res.outputText();
    }

    private String buildPrompt(List<Message> recent, String userText) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 메신저 채팅방의 AI 비서다. 한국어로 간결하게 답해라.\n\n");

        for (Message m : recent) {
            sb.append(m.senderName != null ? m.senderName : m.senderId)
                    .append(": ")
                    .append(m.content == null ? "" : m.content)
                    .append("\n");
        }

        sb.append("\n질문: ").append(userText == null ? "" : userText);
        return sb.toString();
    }
}
