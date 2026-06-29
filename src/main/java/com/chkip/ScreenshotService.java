package com.chkip;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


@Service
public class ScreenshotService {

	private final ChatClient ollamaChatClient;
    private final ChatClient geminiChatClient;
    private final OcrService ocrService;
    private final TextDetectionService textDetectionService;
    private final ImageResizeService imageResizeService;

    public ScreenshotService(@Qualifier("ollamaClient") ChatClient.Builder ollamaBuilder, @Qualifier("geminiClient") ChatClient.Builder geminiBuilder, OcrService ocrService, TextDetectionService textDetectionService, ImageResizeService imageResizeService) {
        this.ollamaChatClient = ollamaBuilder.build();
        this.geminiChatClient = geminiBuilder.build();
        this.ocrService = ocrService;
        this.textDetectionService = textDetectionService;
        this.imageResizeService = imageResizeService;
    }

    public void analyzeScreenshots(String folderPath) throws IOException, InterruptedException, ExecutionException {
        List<Path> images = Files.list(Paths.get(folderPath))
                .filter(p -> p.toString().matches(".*\\.(png|jpg|jpeg)$"))
                .toList();

        int total = images.size();
        long startTotal = System.currentTimeMillis();
        System.out.println("=== Starting analysis of " + total + " images ===");
        
        
        //Detection rapide pour tri des images
        List<Path> textImages = new ArrayList<>();
        List<Path> visualImages = new ArrayList<>();
        
        for (Path image : images) {
        	if(textDetectionService.hasSignificantText(image.toFile())) {
        		textImages.add(image);
        	} else {
        		visualImages.add(image);
        	}
        }
        
        System.out.println("=== Text: " + textImages.size() + " | Visual: " + visualImages.size() + " ===");
        
        // exécuter OCR + llama3 en parallèle avec llava 
        int ocrThreads = Runtime.getRuntime().availableProcessors() - 2;
        ExecutorService ocrExecutor = Executors.newFixedThreadPool(ocrThreads);
        ExecutorService visualExecutor = Executors.newFixedThreadPool(2);
        
        AtomicInteger count = new AtomicInteger(0);
        List<Future<?>> allFutures = new java.util.ArrayList<>();
        
        // analyse du texte : OCR + llama3     
        for (Path image : textImages) {
            allFutures.add(ocrExecutor.submit(() -> {
                try {
                	
                	long startImage = System.currentTimeMillis();
                	
                    int current = count.incrementAndGet();
                    System.out.println("Analyzing " + current + "/" + total + ": " + image.getFileName());
                    long start = System.currentTimeMillis();
                    
                    
                    String ocrText = ocrService.extractText(image.toFile());
                    String response = ollamaChatClient.prompt()
                    		.user("based on this text extracted from an Instagram screenshot, what topic or insterest does it sugests? Be concise.\n\n" +ocrText)
                    		.call()
                    		.content();

       
                    
                    long duration = System.currentTimeMillis() - start;
                    System.out.println("-> " + response);
                    System.out.println("Duration: " + duration + "ms ---");
                } catch (Exception e) {
                    System.err.println("Error processing " + image.getFileName() + ": " + e.getMessage());
                }
            }));
        }
             
        // Pool visuel → llava
        for (Path image : visualImages) {
            allFutures.add(visualExecutor.submit(() -> {
                try {
                    int current = count.incrementAndGet();
                    System.out.println("🖼 " + current + "/" + total + ": " + image.getFileName());
                    long start = System.currentTimeMillis();

                    File resizedImage = imageResizeService.resize(image);
                    var userMessage = UserMessage.builder()
                            .text("Look at this screenshot. What topics, interests or themes does it suggest about the person who saved it? Be concise.")
                            .media(new Media(MimeTypeUtils.IMAGE_PNG, new FileSystemResource(resizedImage)))
                            .build();

                    String response = geminiChatClient.prompt()
                            .messages(userMessage)
                            .call()
                            .content();

                    long duration = System.currentTimeMillis() - start;
                    System.out.println("→ " + response);
                    System.out.println("⏱ " + duration + "ms ---");
                } catch (Exception e) {
                    System.err.println("Error: " + image.getFileName() + ": " + e.getMessage());
                }
            }));
        }

        for (Future<?> f : allFutures) f.get();
        ocrExecutor.shutdown();
        visualExecutor.shutdown();

        long totalDuration = System.currentTimeMillis() - startTotal;
        System.out.println("=== Total: " + totalDuration + "ms pour " + total + " images ===");
        System.out.println("=== Moyenne: " + (totalDuration / total) + "ms par image ===");
    }
}