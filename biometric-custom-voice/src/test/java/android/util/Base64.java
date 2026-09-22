package android.util;

/** JVM implementation of the NO_WRAP Android codec used by the production template store. */
public final class Base64 {
    public static final int NO_WRAP = 2;
    public static String encodeToString(byte[] input, int flags) {
        if (flags != NO_WRAP) throw new IllegalArgumentException("Unsupported flags");
        return java.util.Base64.getEncoder().encodeToString(input);
    }
    public static byte[] decode(String input, int flags) {
        if (flags != NO_WRAP) throw new IllegalArgumentException("Unsupported flags");
        return java.util.Base64.getDecoder().decode(input);
    }
}
