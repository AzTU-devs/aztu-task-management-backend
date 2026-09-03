package az.aztu.kanban.service;

/**
 * The canvas coordinate rules, shared by the server and mirrored exactly by
 * frontend src/lib/architecture.ts.
 *
 * Both sides must snap and clamp identically. If they disagree by even one unit, every drop
 * ends with the rectangle visibly twitching as the server's answer arrives and overwrites the
 * position the user let go of.
 */
public final class ArchitectureGeometry {

    public static final int WORLD_WIDTH = 4000;
    public static final int WORLD_HEIGHT = 2600;
    public static final int SNAP = 10;

    public static final int MIN_WIDTH = 120;
    public static final int MAX_WIDTH = 480;
    public static final int MIN_HEIGHT = 60;
    public static final int MAX_HEIGHT = 360;

    private ArchitectureGeometry() {
    }

    public static int snap(int value) {
        return Math.round((float) value / SNAP) * SNAP;
    }

    public static int clampWidth(Integer width) {
        return clamp(width == null ? 220 : width, MIN_WIDTH, MAX_WIDTH);
    }

    public static int clampHeight(Integer height) {
        return clamp(height == null ? 100 : height, MIN_HEIGHT, MAX_HEIGHT);
    }

    /** Snapped, and kept fully inside the world given the node's own size. */
    public static int clampX(int x, int width) {
        return clamp(snap(x), 0, WORLD_WIDTH - width);
    }

    public static int clampY(int y, int height) {
        return clamp(snap(y), 0, WORLD_HEIGHT - height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
