package net.lightningesp.core;

/**
 * Drop-in replacement for {@code pyrock.utility.render.ColorRGBA}.
 *
 * Only the members the Lightning renderer actually uses are implemented:
 * getRed/getGreen/getBlue/getAlpha, getRGB, withAlpha, mulAlpha, mix.
 * Channel semantics are identical to Rockstar's ColorRGBA: channels are
 * floats in the 0..255 range and {@link #getRGB()} packs them as ARGB
 * (alpha in the high byte), which is what BufferBuilder.color(int) expects.
 */
public final class LightningEspColor {

    public static final LightningEspColor WHITE = new LightningEspColor(255.0F, 255.0F, 255.0F, 255.0F);
    public static final LightningEspColor RED = new LightningEspColor(255.0F, 0.0F, 0.0F, 255.0F);

    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    public LightningEspColor(float red, float green, float blue) {
        this(red, green, blue, 255.0F);
    }

    public LightningEspColor(float red, float green, float blue, float alpha) {
        this.red = clampChannel(red);
        this.green = clampChannel(green);
        this.blue = clampChannel(blue);
        this.alpha = clampChannel(alpha);
    }

    /** Builds a colour from a packed 0xAARRGGBB int. */
    public static LightningEspColor fromArgb(int argb) {
        return new LightningEspColor(
            (argb >> 16) & 0xFF,
            (argb >> 8) & 0xFF,
            argb & 0xFF,
            (argb >>> 24) & 0xFF
        );
    }

    public float getRed() {
        return this.red;
    }

    public float getGreen() {
        return this.green;
    }

    public float getBlue() {
        return this.blue;
    }

    public float getAlpha() {
        return this.alpha;
    }

    public int getRGB() {
        int a = Math.round(clampUnit(this.alpha));
        int r = Math.round(clampUnit(this.red));
        int g = Math.round(clampUnit(this.green));
        int b = Math.round(clampUnit(this.blue));
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
    }

    public LightningEspColor withAlpha(float newAlpha) {
        return new LightningEspColor(this.red, this.green, this.blue, newAlpha);
    }

    public LightningEspColor mulAlpha(float factor) {
        return this.withAlpha(this.alpha * factor);
    }

    public LightningEspColor mix(LightningEspColor other, float t) {
        float k = Math.min(1.0F, Math.max(0.0F, t));
        return new LightningEspColor(
            lerp(this.red, other.red, k),
            lerp(this.green, other.green, k),
            lerp(this.blue, other.blue, k),
            lerp(this.alpha, other.alpha, k)
        );
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float clampChannel(float value) {
        return Math.max(0.0F, Math.min(255.0F, value));
    }

    private static float clampUnit(float value) {
        return Math.max(0.0F, Math.min(255.0F, value));
    }

    @Override
    public String toString() {
        return "LightningEspColor[r=" + this.red + ", g=" + this.green + ", b=" + this.blue + ", a=" + this.alpha + "]";
    }
}