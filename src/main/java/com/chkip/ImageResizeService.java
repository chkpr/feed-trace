package com.chkip;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ImageResizeService {

    private static final int MAX_WIDTH = 512;

    public File resize(Path imagePath) throws IOException {
        BufferedImage original = ImageIO.read(imagePath.toFile());

        if (original.getWidth() <= MAX_WIDTH) {
            return imagePath.toFile();
        }

        int newWidth = MAX_WIDTH;
        int newHeight = (int) ((double) original.getHeight() / original.getWidth() * newWidth);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        Path tempFile = Files.createTempFile("resized_", ".png");
        ImageIO.write(resized, "png", tempFile.toFile());
        return tempFile.toFile();
    }
}
