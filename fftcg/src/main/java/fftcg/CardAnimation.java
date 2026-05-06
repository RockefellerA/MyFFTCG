package fftcg;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

/** Static utilities for rendering and transforming card images. */
class CardAnimation {

	static final int CARD_W = 140;
	static final int CARD_H = 205;

	private CardAnimation() {}

	static BufferedImage renderBackupCardAtAngle(BufferedImage card, double angle) {
		BufferedImage canvas = new BufferedImage(CARD_H, CARD_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.translate(CARD_H / 2.0, CARD_H / 2.0);
		g.rotate(angle);
		g.translate(-CARD_W / 2.0, -CARD_H / 2.0);
		g.drawImage(card, 0, 0, null);
		g.dispose();
		return canvas;
	}

	/**
	 * Composites a (possibly transformed) card image onto a square
	 * {@code CARD_H × CARD_H} canvas, respecting the slot alignment rules:
	 * <ul>
	 *   <li>Active - upright card pinned to the left edge, top</li>
	 *   <li>Dull — card rotated 90° CW ({@code CARD_H × CARD_W}), pinned left + bottom</li>
	 * </ul>
	 */
	static BufferedImage renderBackupCard(BufferedImage card, CardState state) {
		return renderBackupCard(card, state, false);
	}

	static BufferedImage renderBackupCard(BufferedImage card, CardState state, boolean highlight) {
		return renderBackupCard(card, state, highlight, false);
	}

	static BufferedImage renderBackupCard(BufferedImage card, CardState state, boolean highlight, boolean selected) {
		return renderBackupCard(card, state, highlight, selected, false);
	}

	static BufferedImage renderBackupCard(BufferedImage card, CardState state, boolean highlight, boolean selected, boolean frozen) {
		BufferedImage canvas = new BufferedImage(CARD_H, CARD_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		if (frozen) card = applyBlueTint(card);
		switch (state) {
			case CardState.DULL -> {
				BufferedImage rotated = rotateCW90(card);          // now CARD_H × CARD_W
				g.drawImage(rotated, 0, CARD_H - CARD_W, null);   // pinned to bottom-left
			}
			default -> g.drawImage(card, 0, 0, null);             // pinned to top-left
		}
		if (selected) {
			g.setColor(new Color(255, 165, 0));
			g.setStroke(new BasicStroke(4f));
			g.drawRect(2, 2, CARD_W - 5, CARD_H - 5);
		} else if (highlight) {
			g.setColor(new Color(0, 220, 0));
			g.setStroke(new BasicStroke(3f));
			g.drawRect(1, 1, CARD_W - 3, CARD_H - 3);
		}
		g.dispose();
		return canvas;
	}

	/**
	 * Draws {@code value} in a dark pill in the bottom-left of {@code canvas} using
	 * {@code textColor} as the text color.  Used to show remaining HP, boosted power,
	 * or reduced power with different colors.
	 */
	static void renderPowerOverlay(BufferedImage canvas, int value, Color textColor) {
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		String text = String.valueOf(value);
		Font font = new Font("Pixel NES", Font.BOLD, 13);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int tw = fm.stringWidth(text);
		int tx = 4;
		int ty = canvas.getHeight() - 5;
		g.setColor(new Color(0, 0, 0, 180));
		g.fillRoundRect(tx - 4, ty - fm.getAscent() - 1, tw + 8, fm.getAscent() + fm.getDescent() + 2, 5, 5);
		g.setColor(textColor);
		g.drawString(text, tx, ty);
		g.dispose();
	}

	/** Draws remaining HP in a red pill — delegates to {@link #renderPowerOverlay}. */
	static void renderDamageOverlay(BufferedImage canvas, int remainingHp) {
		renderPowerOverlay(canvas, remainingHp, new Color(255, 50, 50));
	}

	/** Converts any {@link Image} to a scaled {@link BufferedImage} (ARGB). */
	static BufferedImage toARGB(Image src, int w, int h) {
		BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = buf.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return buf;
	}

	/** Rotates a {@link BufferedImage} 90° clockwise. Result dimensions are {@code h × w}. */
	static BufferedImage rotateCW90(BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		BufferedImage dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.translate(h, 0);
		g.rotate(Math.PI / 2);
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return dst;
	}

	/** Applies a blue tint to a {@link BufferedImage} (darkens R/G, boosts B). */
	static BufferedImage applyBlueTint(BufferedImage src) {
		float[] scales  = { 0.4f, 0.4f, 1.0f, 1.0f };
		float[] offsets = { 0f,   0f,   60f,  0f   };
		return new RescaleOp(scales, offsets, null).filter(src, null);
	}
}
