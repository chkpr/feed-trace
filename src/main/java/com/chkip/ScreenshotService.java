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
import java.util.Collections;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


@Service
public class ScreenshotService {

	private final ChatClient ollamaChatClient;
    private final OcrService ocrService;
    private final TextDetectionService textDetectionService;
    private final ImageResizeService imageResizeService;

    public ScreenshotService(@Qualifier("ollamaClient") ChatClient.Builder ollamaBuilder, OcrService ocrService, TextDetectionService textDetectionService, ImageResizeService imageResizeService) {
        this.ollamaChatClient = ollamaBuilder.build();
        this.ocrService = ocrService;
        this.textDetectionService = textDetectionService;
        this.imageResizeService = imageResizeService;
    }
    
    

    public void analyzeScreenshots(String folderPath) throws IOException, InterruptedException, ExecutionException {
    	
    	List<String> allResults = Collections.synchronizedList(new ArrayList<>());
    	
        List<Path> images = Files.list(Paths.get(folderPath))
                .filter(p -> p.toString().matches(".*\\.(png|jpg|jpeg)$"))
                .toList();

        int total = images.size();
        long startTotal = System.currentTimeMillis();
        System.out.println("=== Starting analysis of " + total + " images ===");
        
        
        //Phase 1 OCR complet sur toutes les images
        int ocrThreads = Runtime.getRuntime().availableProcessors() - 2;
        ExecutorService ocrFullExecutor = Executors.newFixedThreadPool(ocrThreads);
        Map<Path, String> ocrResults = new ConcurrentHashMap<>();
        List<Future<?>> ocrFullFutures = new ArrayList<>();
        
        for (Path image : images) {
        	ocrFullFutures.add(ocrFullExecutor.submit(() -> {
        		String text = ocrService.extractText(image.toFile());
        		ocrResults.put(image,  text);
        	}));
        }
        for (Future<?> f : ocrFullFutures) f.get();
        ocrFullExecutor.shutdown();
        
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
        
        ExecutorService ocrAnalysisExecutor = Executors.newFixedThreadPool(6);
        ExecutorService visualExecutor = Executors.newFixedThreadPool(2);
        AtomicInteger count = new AtomicInteger(0);
        List<Future<?>> allFutures = new ArrayList<>();
        
        // Texte → llama3 via Ollama
        for (Path image : textImages) {
            allFutures.add(ocrAnalysisExecutor.submit(() -> {
                try {
                    int current = count.incrementAndGet();
                    long start = System.currentTimeMillis();
                    System.out.println("📝 " + current + "/" + total + ": " + image.getFileName());

                    String ocrText = ocrResults.get(image);
                    String response = ollamaChatClient.prompt()
                            .user("Based on this text extracted from an Instagram screenshot, what topics or interests does it suggest? Be concise.\n\n" + ocrText)
                            .call()
                            .content();
                    
                    allResults.add(response);
                    
                    long duration = System.currentTimeMillis() - start;
                    System.out.println("→ " + response);
                    System.out.println("⏱ " + duration + "ms ---");
                } catch (Exception e) {
                    System.err.println("Error: " + image.getFileName() + ": " + e.getClass().getName() + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }));
        }

        // Visuel → llava (resize 512px)
        for (Path image : visualImages) {
            allFutures.add(visualExecutor.submit(() -> {
                try {
                    int current = count.incrementAndGet();
                    long start = System.currentTimeMillis();
                    System.out.println("🖼 " + current + "/" + total + ": " + image.getFileName());

                   File resizedImage = imageResizeService.resize(image);
                   var userMessage = UserMessage.builder()
                		   .text("Look at this screenshot. What topics, interests or themes does it suggests about the person who saved it ? Be concise.")
                   			.media(new Media(MimeTypeUtils.IMAGE_PNG, new FileSystemResource(resizedImage)))
                   			.build();
                    String response = ollamaChatClient.prompt()
                            .messages(userMessage)
                            .call()
                            .content();
                    
                    allResults.add(response);

                    long duration = System.currentTimeMillis() - start;
                    System.out.println("→ " + response);
                    System.out.println("⏱ " + duration + "ms ---");
                } catch (Exception e) {
                    System.err.println("Error: " + image.getFileName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }));
        }

        for (Future<?> f : allFutures) f.get();
        ocrAnalysisExecutor.shutdown();
        visualExecutor.shutdown();
        
        System.out.println("=== Generating summary ===");
        String combinedResults = String.join("\n---\n", allResults);
        String summary = ollamaChatClient.prompt()
                .user("Here are individual analyses of someone's saved Instagram screenshots. "
                    + "Synthesize the recurring themes, interests, and patterns across all of them "
                    + "into a clear, organized summary (3-5 main themes with brief explanations):\n\n"
                    + combinedResults)
                .call()
                .content();

        System.out.println("\n=== SUMMARY ===");
        System.out.println(summary);

        long totalDuration = System.currentTimeMillis() - startTotal;
        System.out.println("=== Total: " + totalDuration + "ms pour " + total + " images ===");
        System.out.println("=== Moyenne: " + (totalDuration / total) + "ms par image ===");
    }
}