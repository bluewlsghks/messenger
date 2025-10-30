package com.individual.messenger.config;

import com.individual.messenger.crypto.Aes256;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {
    // 운영에서는 환경변수/외부설정에서 주입
    @Bean
    public Aes256 aes256() {
        String key32 = "0123456789ABCDEF0123456789ABCDEF"; // 예시: 32자
        String iv16  = "ABCDEF0123456789";                   // 예시: 16자
        return new Aes256(key32, iv16);
    }
}
