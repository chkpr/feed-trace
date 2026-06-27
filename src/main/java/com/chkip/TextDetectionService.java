package com.chkip;

import java.io.File;

import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class TextDetectionService {
	
	private final ThreadLocal<Tesseract>tesseractThreadLocal = ThreadLocal.withInitial(() -> {
		Tesseract t = new Tesseract();
		t.setDatapath("/usr/share/tesseract/tessdata");
		t.setLanguage("fra+eng");
		t.setPageSegMode(1);
		return t;
	});
	
	public boolean hasSignificantText(File image) {
		try {
			String text = tesseractThreadLocal.get().doOCR(image);
			long wordCount = text.strip().lines()
					.filter(line -> line.trim().length() > 3)
					.count();
			return wordCount > 5;
		} catch (TesseractException e) {
			return false;
		}
	}

}
