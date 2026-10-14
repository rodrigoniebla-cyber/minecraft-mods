package net.manaz.vtp.render;

public final class HudSize {
   public static final int MIN = 50;
   public static final int MAX = 200;
   public static final int DEFAULT = 100;
   private static int percent = 100;

   private HudSize() {
   }

   public static int percent() {
      return percent;
   }

   public static void set(int value) {
      percent = Math.clamp((long)value, 50, 200);
   }

   public static HudSize.Layout layout(int screenWidth, int screenHeight, int mainHeight) {
      return layout(screenWidth, screenHeight, mainHeight, percent);
   }

   public static HudSize.Layout layout(int screenWidth, int screenHeight, int mainHeight, int percent) {
      percent = Math.clamp((long)percent, 50, 200);
      float scale = Math.min(percent / 100.0F, Math.min((screenWidth - 20) / 240.0F, (float)(screenHeight - 56) / Math.max(1, mainHeight)));
      scale = Math.max(0.01F, scale);
      int width = Math.min(212, (int)Math.floor((screenWidth - 20) / (2.0F * scale)));
      return new HudSize.Layout(
         scale,
         width,
         (int)Math.ceil(6.0F / scale),
         (int)Math.floor((screenWidth - 6) / scale) - width,
         (int)Math.ceil(6.0F / scale),
         (int)Math.floor((screenHeight - 48) / scale)
      );
   }

   public record Layout(float scale, int panelWidth, int left, int right, int top, int bottom) {
   }
}
