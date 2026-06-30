package com.chkip;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
	
	@Bean
	@Qualifier("ollamaClient")
	public ChatClient.Builder ollamaClientBuilder(
			org.springframework.ai.ollama.OllamaChatModel ollamaModel) {
		return ChatClient.builder(ollamaModel);
		}
	

			

}
