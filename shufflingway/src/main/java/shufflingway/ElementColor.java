package shufflingway;

import java.awt.Color;

public enum ElementColor {

    FIRE      ("#E5502F"),
    ICE       ("#64C7F1"),
    WIND      ("#33B371"),
    EARTH     ("#FCCF00"),
    LIGHTNING ("#B177B1"),
    WATER     ("#96B8DD"),
    LIGHT     ("#777B7E"),
    DARK      ("#2E2825");

    public final Color color;

    ElementColor(String hex) {
        this.color = Color.decode(hex);
    }

    /** Returns the ElementColor for the given element name (case-insensitive), or null if not found. */
    public static ElementColor fromName(String name) {
        if (name == null) return null;
        try {
            return ElementColor.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
