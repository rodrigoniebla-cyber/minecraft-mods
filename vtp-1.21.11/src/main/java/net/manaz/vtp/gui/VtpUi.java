package net.manaz.vtp.gui;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class VtpUi {
   public static final int TEXT = -1;
   public static final int MUTED = -6250336;
   public static final int HUD_TEXT = -1;
   public static final int HUD_MUTED = -6250336;
   public static final int EDGE = -13092808;

   public static int accent() {
      return VtpAccent.color();
   }

   public static int accentText() {
      return VtpAccent.text();
   }

   private VtpUi() {
   }

   public static void frame(GuiGraphics g, int x, int y, int w, int h) {
      panel(g, x, y, w, h, 0.94F);
   }

   public static void panel(GuiGraphics g, int x, int y, int w, int h, float opacity) {
      g.fill(x + 2, y + 1, x + w - 2, y + h - 1, alpha(-15527149, opacity));
      g.fill(x + 1, y + 2, x + 2, y + h - 2, alpha(-15527149, opacity));
      g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, alpha(-15527149, opacity));
      int top = alpha(-13092808, opacity);
      int bottom = alpha(-14408668, opacity);
      g.fill(x + 2, y, x + w - 2, y + 1, top);
      g.fill(x, y + 2, x + 1, y + h - 2, top);
      g.fill(x + 1, y + 1, x + 2, y + 2, top);
      g.fill(x + w - 2, y + 1, x + w - 1, y + 2, top);
      g.fill(x + w - 1, y + 2, x + w, y + h - 2, bottom);
      g.fill(x + 2, y + h - 1, x + w - 2, y + h, bottom);
      g.fill(x + 1, y + h - 2, x + 2, y + h - 1, bottom);
      g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, bottom);
   }

   public static void slot(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, -15987700);
      g.fill(x, y, x + w, y + 1, -13092808);
      g.fill(x, y, x + 1, y + h, -13092808);
      g.fill(x, y + h - 1, x + w, y + h, -14408668);
      g.fill(x + w - 1, y, x + w, y + h, -14408668);
   }

   public static void hud(GuiGraphics g, int x, int y, int w, int h) {
      panel(g, x, y, w, h, 0.7F);
   }

   public static int alpha(int color, float alpha) {
      return Math.clamp((long)Math.round((color >>> 24) * alpha), 0, 255) << 24 | color & 16777215;
   }

   public static String fit(Font font, String value, int width) {
      if (width <= 0) {
         return "";
      } else {
         return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
      }
   }

   public static String fitBook(Font font, String value, int width) {
      int space = value.lastIndexOf(32);
      if (space > 0 && font.width(value) > width && value.substring(space + 1).matches("[0-9IVX]+")) {
         String level = value.substring(space);
         return fit(font, value.substring(0, space), width - font.width(level)) + level;
      } else {
         return fit(font, value, width);
      }
   }

   public static void tooltip(GuiGraphics g, Font font, String value, int mx, int my) {
      int width = Math.min(240, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24);
      g.setTooltipForNextFrame(font, font.split(Component.literal(value), width), mx, my);
   }

   public static String name(String id) {
      String path = id.substring(id.lastIndexOf(58) + 1);
      StringBuilder result = new StringBuilder();

      for (String word : path.split("_")) {
         if (!word.isEmpty()) {
            if (!result.isEmpty()) {
               result.append(' ');
            }

            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
         }
      }

      return result.toString();
   }

   public static void text(GuiGraphics g, Font font, String text, int x, int y, int w, int color, int mx, int my) {
      g.drawString(font, fit(font, text, w), x, y, color, true);
      if (font.width(text) > w && mx >= x && mx < x + w && my >= y && my < y + 10) {
         tooltip(g, font, text, mx, my);
      }
   }
}
