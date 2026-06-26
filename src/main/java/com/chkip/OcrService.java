package com.chkip;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class OcrService {

    private final ThreadLocal<Tesseract> tesseractThreadLocal = ThreadLocal.withInitial(() -> {
        Tesseract t = new Tesseract();
        t.setDatapath("/usr/share/tesseract/tessdata");
        t.setLanguage("fra+eng");
        return t;
    });

    public String extractText(File image) {
        try {
            String text = tesseractThreadLocal.get().doOCR(image);
            System.out.println("OCR result: " + text);
            return text;
        } catch (TesseractException e) {
            System.err.println("OCR failed for " + image.getName() + ": " + e.getMessage());
            return "";
        }
    }
}