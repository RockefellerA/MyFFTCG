package shufflingway;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

public class FontLoader {

    public static Font loadPixelNESFont(float size) {
        try {
            // 1. Grab the file from your resources folder
            InputStream is = FontLoader.class.getResourceAsStream("/fonts/Pixel_NES.otf");
            
            // 2. Create the font object
            Font customFont = Font.createFont(Font.TRUETYPE_FONT, is);
            
            // 3. Register it so it's available to the JVM
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(customFont);
            
            // 4. Return it at the desired size (deriveFont)
            return customFont.deriveFont(size);
            
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to a standard font if something goes wrong
            return new Font("Arial", Font.PLAIN, (int)size);
        }
    }
}