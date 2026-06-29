package com.chkip;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.core.io.ByteArrayResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
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
        
        
        //Phase 1 OCR complet sur toutes les images
        int ocrThreads = Runtime.getRuntime().availableProcessors() - 2;
        ExecutorService ocrExecutor = Executors.newFixedThreadPool(ocrThreads);
        Map<Path, String> ocrResults = new ConcurrentHashMap<>();
        List<Future<?>> ocrFutures = new ArrayList<>();
        
        for (Path image : images) {
        	ocrFutures.add(ocrExecutor.submit(() -> {
        		String text = ocrService.extractText(image.toFile());
        		ocrResults.put(image,  text);
        	}));
        }
        for (Future<?> f : ocrFutures) f.get();
        ocrExecutor.shutdown();
        
        long ocrDuration = System.currentTimeMillis() - startTotal;
        System.out.println("=== OCR done in " + ocrDuration +" ms ===");
        
         //Phase 2 : tri fiable basé sur OCR complet
         
        List<Path> textImages = new ArrayList<>();
        List<Path> visualImages = new ArrayList<>();
        
        for (Path image : images) {
        	String text = ocrResults.getOrDefault(image, "");
        	long wordCount = text.strip().lines()
        			.filter(line-> line.trim().length() > 3)
        			.count();
        	if (wordCount > 5) {
        		textImages.add(image);
        	} else {
        		visualImages.add(image);
        	}
        }
        
        
        System.out.println("=== Text: " + textImages.size() + " | Visual: " + visualImages.size() + " ===");
        
        // Phase 3 — analyse en parallèle
        AtomicInteger count = new AtomicInteger(0);
        ExecutorService ocrAnalysisExecutor = Executors.newFixedThreadPool(2);
        ExecutorService visualExecutor = Executors.newFixedThreadPool(1);
        List<Future<?>> allFutures = new ArrayList<>();
        
        // Texte → Gemini
        for (Path image : textImages) {
            allFutures.add(ocrAnalysisExecutor.submit(() -> {
                try {
                    int current = count.incrementAndGet();
                    long start = System.currentTimeMillis();
                    System.out.println("📝 " + current + "/" + total + ": " + image.getFileName());

                    String ocrText = ocrResults.get(image);
                    String response = geminiChatClient.prompt()
                            .user("Based on this text extracted from an Instagram screenshot, what topics or interests does it suggest? Be concise.\n\n" + ocrText)
                            .call()
                            .content();
                    
                    Thread.sleep(4000);

                    long duration = System.currentTimeMillis() - start;
                    System.out.println("→ " + response);
                    System.out.println("⏱ " + duration + "ms ---");
                } catch (Exception e) {
                    System.err.println("Error: " + image.getFileName() + ": " + e.getClass().getName() + " - " + e.getMessage());
                }
            }));
        }

        // Visuel → Gemini
        for (Path image : visualImages) {
            allFutures.add(visualExecutor.submit(() -> {
                try {
                    int current = count.incrementAndGet();
                    long start = System.currentTimeMillis();
                    System.out.println("🖼 " + current + "/" + total + ": " + image.getFileName());

                    byte[] imageBytes = Files.readAllBytes(image);
                    var userMessage = UserMessage.builder()
                            .text("Look at this screenshot. What topics, interests or themes does it suggest about the person who saved it? Be concise.")
                            .media(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)))
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
        ocrAnalysisExecutor.shutdown();
        visualExecutor.shutdown();

        long totalDuration = System.currentTimeMillis() - startTotal;
        System.out.println("=== Total: " + totalDuration + "ms pour " + total + " images ===");
        System.out.println("=== Moyenne: " + (totalDuration / total) + "ms par image ===");
    }
}