package net.manaz.vtp.gui;

import java.awt.Color;
import java.util.Locale;
import java.util.OptionalInt;

public final class VtpAccent {
   public static final int DEFAULT = -11141291;
   private static int color = -11141291;
   private static int text = -11141291;

   private VtpAccent() {
   }

   public static int color() {
      return color;
   }

   public static void set(int rgb) {
      color = 0xFF000000 | rgb & 16777215;
      text = readable(color);
   }

   public static String hex(int rgb) {
      return String.format(Locale.ROOT, "#%06X", rgb & 16777215);
   }

   public static OptionalInt parse(String value) {
      if (value == null) {
         return OptionalInt.empty();
      } else {
         String hex = value.trim();
         if (hex.startsWith("#")) {
            hex = hex.substring(1);
         }

         return hex.matches("[0-9a-fA-F]{6}") ? OptionalInt.of(0xFF000000 | Integer.parseInt(hex, 16)) : OptionalInt.empty();
      }
   }

   public static int hsv(float hue, float saturation, float brightness) {
      return Color.HSBtoRGB(hue, saturation, brightness);
   }

   public static float[] hsv(int rgb) {
      return Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
   }

   public static int text() {
      return text;
   }

   public static int readable(int rgb) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;

      int b;
      for (b = rgb & 0xFF; luminance(r, g, b) < 0.28 && (r < 255 || g < 255 || b < 255); b = Math.min(255, b + 4)) {
         r = Math.min(255, r + 4);
         g = Math.min(255, g + 4);
      }

      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static double luminance(int r, int g, int b) {
      return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
   }

   private static double linear(int channel) {
      double value = channel / 255.0;
      return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
   }
}
