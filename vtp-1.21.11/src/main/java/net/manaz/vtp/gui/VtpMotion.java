package net.manaz.vtp.gui;

public final class VtpMotion {
   private VtpMotion() {
   }

   public static float approach(float current, float target, double seconds, double responseSeconds) {
      if (seconds <= 0.0) {
         return current;
      } else {
         float value = (float)(target + (current - target) * Math.exp(-seconds / responseSeconds));
         return Math.abs(value - target) < 0.001F ? target : value;
      }
   }

   public static final class Value {
      private long lastNanos;
      private float value;

      public float sample(float target) {
         long now = System.nanoTime();
         double seconds = this.lastNanos == 0L ? 0.0 : Math.min(0.25, (now - this.lastNanos) / 1.0E9);
         this.lastNanos = now;
         this.value = VtpMotion.approach(this.value, target, seconds, 0.045);
         return this.value;
      }
   }
}
