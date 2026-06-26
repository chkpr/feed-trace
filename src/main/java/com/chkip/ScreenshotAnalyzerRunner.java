package com.chkip;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ScreenshotAnalyzerRunner implements CommandLineRunner {

    private final ScreenshotService screenshotService;

    public ScreenshotAnalyzerRunner(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }

    @Override
    public void run(String... args) throws Exception {
        screenshotService.analyzeScreenshots("/home/chkpr/web/screenshot-analyzer/screens");
    }
}