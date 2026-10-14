package net.manaz.vtp.render;

public final class HudFade {
   public static final double OUT_SECONDS = 0.25;
   public static final double IN_SECONDS = 0.15;
   private double progress;

   public void reset() {
      this.progress = 0.0;
   }

   public float opacity() {
      return (float)(this.progress * this.progress * (3.0 - 2.0 * this.progress));
   }

   public float advance(boolean visible, double seconds) {
      if (seconds > 0.0 && Double.isFinite(seconds)) {
         double step = seconds / (visible ? 0.15 : 0.25);
         this.progress = Math.clamp(this.progress + (visible ? step : -step), 0.0, 1.0);
      }

      return this.opacity();
   }

   public static int itemTint(int color, float opacity) {
      float fade = Math.clamp(opacity, 0.0F, 1.0F);
      return Math.round((color >>> 24) * fade) << 24
         | Math.round((color >> 16 & 0xFF) * fade) << 16
         | Math.round((color >> 8 & 0xFF) * fade) << 8
         | Math.round((color & 0xFF) * fade);
   }
}
