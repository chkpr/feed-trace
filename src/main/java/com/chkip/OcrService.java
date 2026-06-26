package com.chkip;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class OcrService {

    private final Tesseract tesseract;

    public OcrService() {
        tesseract = new Tesseract();
        tesseract.setDatapath("/usr/share/tesseract/tessdata");
        tesseract.setLanguage("fra+eng");
    }

    public String extractText(File image) {
        try {
        	String text = tesseract.doOCR(image);
            System.out.println("OCR result: " + text);
            return text;
        } catch (TesseractException e) {
            System.err.println("OCR failed for " + image.getName() + ": " + e.getMessage());
            return "";
        }
    }
}