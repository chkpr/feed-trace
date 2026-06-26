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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;

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
        AtomicInteger count = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (Path image : images) {
            futures.add(executor.submit(() -> {
                try {
                    int current = count.incrementAndGet();
                    System.out.println("Analyzing " + current + "/" + total + ": " + image.getFileName());

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
                } catch (Exception e) {
                    System.err.println("Error processing " + image.getFileName() + ": " + e.getMessage());
                }
            }));
        }
             

        futures.forEach(f -> {
            try { f.get(); } catch (Exception e) { e.printStackTrace(); }
        });

        executor.shutdown();
    
    }
}