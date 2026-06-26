package com.chkip;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ScreenshotService {

    private final ChatClient chatClient;
    private final OcrService ocrService;

    public ScreenshotService(ChatClient.Builder builder, OcrService ocrService) {
        this.chatClient = builder.build();
        this.ocrService = ocrService;
    }

    public void analyzeScreenshots(String folderPath) throws IOException {
        List<Path> images = Files.list(Paths.get(folderPath))
                .filter(p -> p.toString().matches(".*\\.(png|jpg|jpeg)$"))
                .toList();

        int total = images.size();
        int count = 0;

        for (Path image : images) {
            count++;
            System.out.println("Analyzing " + count + "/" + total + ": " + image.getFileName());

            String ocrText = ocrService.extractText(image.toFile());
            String prompt = "Look at this screenshot. " +
                (ocrText.isBlank() ? "" : "The text visible in the image is: \"" + ocrText.strip() + "\". ") +
                "What topics, interests or themes does it suggest about the person who saved it? Be concise.";

            var userMessage = UserMessage.builder()
                    .text(prompt)
                    .media(new Media(MimeTypeUtils.IMAGE_PNG, new FileSystemResource(image)))
                    .build();

            String response = chatClient.prompt()
                    .messages(userMessage)
                    .call()
                    .content();

            System.out.println("→ " + response);
            System.out.println("---");
        }
    }
}