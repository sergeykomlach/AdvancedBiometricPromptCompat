package dev.skomlach.biometric.compat.utils.statusbar;

import android.graphics.Color;

import androidx.annotation.RestrictTo;
import androidx.core.graphics.ColorUtils;

/**
 * Common color utilities.
 *
 * @author <a href="mailto:info@geosoft.no">GeoSoft</a>
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class ColorUtil {
    /**
     * Blend two colors.
     *
     * @param color1 First color to blend.
     * @param color2 Second color to blend.
     * @param ratio  Blend ratio. 0.5 will give even blend, 1.0 will return
     *               color1, 0.0 will return color2 and so on.
     * @return Blended color.
     */
    public static int blend(int color1, int color2, double ratio) {
        float r = (float) ratio;
        float ir = (float) 1.0 - r;
        return Color.argb((int) (Color.alpha(color1) * r + Color.alpha(color2) * ir), (int) (Color.red(color1) * r + Color.red(color2) * ir),
                (int) (Color.green(color1) * r + Color.green(color2) * ir),
                (int) (Color.blue(color1) * r + Color.blue(color2) * ir));
    }

    /**
     * Make an even blend between two colors.
     *
     * @param color1 First color to blend.
     * @param color2 Second color to blend.
     * @return Blended color.
     */
    public static int blend(int color1, int color2) {
        return ColorUtil.blend(color1, color2, 0.5);
    }

    /**
     * Make a color darker.
     *
     * @param color    Color to make darker.
     * @param fraction Darkness fraction.
     * @return Darker color.
     */
    public static int darker(int color, double fraction) {
        int red = (int) Math.round(Color.red(color) * (1.0 - fraction));
        int green = (int) Math.round(Color.green(color) * (1.0 - fraction));
        int blue = (int) Math.round(Color.blue(color) * (1.0 - fraction));

        if (red < 0) red = 0;
        else if (red > 255) red = 255;
        if (green < 0) green = 0;
        else if (green > 255) green = 255;
        if (blue < 0) blue = 0;
        else if (blue > 255) blue = 255;

        int alpha = Color.alpha(color);

        return Color.argb(alpha, red, green, blue);
    }

    /**
     * Make a color lighter.
     *
     * @param color    Color to make lighter.
     * @param fraction Darkness fraction.
     * @return Lighter color.
     */
    public static int lighter(int color, double fraction) {
        int red = (int) Math.round(Color.red(color) * (1.0 + fraction));
        int green = (int) Math.round(Color.green(color) * (1.0 + fraction));
        int blue = (int) Math.round(Color.blue(color) * (1.0 + fraction));

        if (red < 0) red = 0;
        else if (red > 255) red = 255;
        if (green < 0) green = 0;
        else if (green > 255) green = 255;
        if (blue < 0) blue = 0;
        else if (blue > 255) blue = 255;

        int alpha = Color.alpha(color);

        return Color.argb(alpha, red, green, blue);
    }

    /**
     * Return the hex name of a specified color.
     *
     * @param color Color to get hex name of.
     * @return Hex name of color: "rrggbb".
     */
    public static String getHexName(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        String rHex = Integer.toString(r, 16);
        String gHex = Integer.toString(g, 16);
        String bHex = Integer.toString(b, 16);

        return (rHex.length() == 2 ? "" + rHex : "0" + rHex) +
                (gHex.length() == 2 ? "" + gHex : "0" + gHex) +
                (bHex.length() == 2 ? "" + bHex : "0" + bHex);
    }

    /**
     * Return the "distance" between two colors. The rgb entries are taken
     * to be coordinates in a 3D space [0.0-1.0], and this method returnes
     * the distance between the coordinates for the first and second color.
     *
     * @param r1, g1, b1  First color.
     * @param r2, g2, b2  Second color.
     * @return Distance bwetween colors.
     */
    public static double colorDistance(double r1, double g1, double b1,
                                       double r2, double g2, double b2) {
        double a = r2 - r1;
        double b = g2 - g1;
        double c = b2 - b1;

        return Math.sqrt(a * a + b * b + c * c);
    }

    /**
     * Return the "distance" between two colors.
     *
     * @param color1 First color [r,g,b].
     * @param color2 Second color [r,g,b].
     * @return Distance bwetween colors.
     */
    public static double colorDistance(double[] color1, double[] color2) {
        return ColorUtil.colorDistance(color1[0], color1[1], color1[2],
                color2[0], color2[1], color2[2]);
    }

    /**
     * Return the "distance" between two colors.
     *
     * @param color1 First color.
     * @param color2 Second color.
     * @return Distance between colors.
     */
    public static double colorDistance(int color1, int color2) {
        return ColorUtil.colorDistance(Color.red(color1) / 255.0f, Color.green(color1) / 255.0f, Color.blue(color1) / 255.0f,
                Color.red(color2) / 255.0f, Color.green(color2) / 255.0f, Color.blue(color2) / 255.0f);
    }

    /**
     * Check if a color is more dark than light. Useful if an entity of
     * this color is to be labeled: Use white label on a "dark" color and
     * black label on a "light" color.
     *
     * @param color Color to check.
     * @return True if this is a "dark" color, false otherwise.
     */
    public static boolean isDark(int color) {
        float[] tmpHsl = new float[3];
        ColorUtils.colorToHSL(color, tmpHsl);
        return tmpHsl[2] < 0.45f;
    }

    public static boolean trueDarkColor(int color) {

        double ratio = 0.9;//keep X of origin color
        boolean isDark = ColorUtil.isDark(ColorUtil.blend(color, Color.GRAY));

        if (isDark) {
            color = ColorUtil.blend(color, Color.WHITE, ratio);
        } else {
            color = ColorUtil.blend(color, Color.BLACK, ratio);
        }
        isDark = ColorUtil.isDark(color);

        return isDark;
    }
}