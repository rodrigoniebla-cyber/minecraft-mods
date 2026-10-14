package net.manaz.vtp.render;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.manaz.vtp.gui.VtpIcons;
import net.manaz.vtp.gui.VtpUi;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class VillagerHudRenderer implements HudElement {
   private static boolean enabled = true;
   private static boolean romanNumerals;
   private static boolean showLevels = true;
   private static final List<EnchantmentTarget> targets = new ArrayList<>();
   private static final String[] ROMAN = new String[]{"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
   private static ItemStack emerald;
   private final HudFade fade = new HudFade();
   private Object world;
   private long lastFrame;

   private static ItemStack emerald() {
      if (emerald == null) {
         emerald = new ItemStack(Items.EMERALD);
      }

      return emerald;
   }

   public static boolean isRomanNumerals() {
      return romanNumerals;
   }

   public static void setRomanNumerals(boolean roman) {
      romanNumerals = roman;
   }

   public static boolean isShowLevels() {
      return showLevels;
   }

   public static void setShowLevels(boolean show) {
      showLevels = show;
   }

   public static String fmtLvl(int level) {
      return romanNumerals && level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
   }

   public static void register() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("villager-trading-predictor", "hud_overlay"), new VillagerHudRenderer());
   }

   public static void setEnabled(boolean value) {
      enabled = value;
   }

   public static boolean isEnabled() {
      return enabled;
   }

   public static void addTarget(EnchantmentTarget target) {
      if (!targets.contains(target)) {
         targets.add(target);
      }

      PredictionEngine.clearCache();
   }

   public static void removeTarget(int index) {
      if (index >= 0 && index < targets.size()) {
         targets.remove(index);
         PredictionEngine.clearCache();
      }
   }

   public static void clearTargets() {
      targets.clear();
      PredictionEngine.clearCache();
   }

   public static List<EnchantmentTarget> getTargets() {
      return List.copyOf(targets);
   }

   public static void setTargets(List<EnchantmentTarget> list) {
      targets.clear();
      targets.addAll(list);
      PredictionEngine.clearCache();
   }

   public static void setTarget(EnchantmentTarget target) {
      setTargets(List.of(target));
   }

   public static EnchantmentTarget getTarget() {
      return targets.isEmpty() ? null : targets.getFirst();
   }

   public static String rowMarker(HudView.Row row) {
      return row.label().equals("Current") ? ">" : row.label();
   }

   public void render(GuiGraphics g, DeltaTracker delta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && !mc.options.hideGui && mc.screen == null) {
         HudView view = HudView.current();
         if (view == null) {
            this.resetFade();
         } else {
            if (this.world != mc.level) {
               this.resetFade();
               this.world = mc.level;
            }

            long now = System.nanoTime();
            double elapsed = this.lastFrame == 0L ? 0.0 : (now - this.lastFrame) / 1.0E9;
            this.lastFrame = now;
            float opacity = this.fade.advance(enabled && HudVisibility.visible(view.uuid()), elapsed);
            if (!(opacity < 0.015686275F)) {
               render(g, view, opacity, HudSize.percent(), showLevels);
            }
         }
      } else {
         this.resetFade();
      }
   }

   public static void renderPreview(GuiGraphics g, int percent, boolean levels) {
      HudView view = HudView.current();
      if (view == null) {
         view = new HudView(
            new UUID(0L, 0L),
            "Mending 1",
            "2 rerolls",
            10,
            true,
            "",
            List.of(
               new HudView.Row("Current", "Unbreaking 3", 17, false, false),
               new HudView.Row("+1", "Efficiency 4", 24, false, true),
               new HudView.Row("+2", "Mending 1", 10, true, true),
               new HudView.Row("+3", "Sharpness 5", 23, false, true)
            ),
            List.of(
               new HudView.Row("II", "Protection 4", 22, false, true),
               new HudView.Row("III", "—", -1, false, true),
               new HudView.Row("IV", "—", -1, false, true)
            ),
            false
         );
      }

      render(g, view, 1.0F, percent, levels);
   }

   public static int rowsHeight(List<HudView.Row> rows) {
      return 26 + rows.size() * 16;
   }

   public static HudSize.Layout layout(HudView view, int screenWidth, int screenHeight, int percent, boolean levels) {
      int rightHeight = rowsHeight(view.trades());
      if (view.locked() && levels && !view.levels().isEmpty()) {
         rightHeight += 6 + rowsHeight(view.levels());
      }

      return HudSize.layout(screenWidth, screenHeight, Math.max(targetHeight(view), rightHeight), percent);
   }

   private static void render(GuiGraphics g, HudView view, float opacity, int percent, boolean levels) {
      Minecraft mc = Minecraft.getInstance();
      HudSize.Layout layout = layout(view, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), percent, levels);
      g.pose().pushMatrix();

      try {
         g.pose().scale(layout.scale(), layout.scale());
         renderTarget(g, mc.font, view, layout.left(), layout.top(), layout.panelWidth(), opacity);
         if (view.trades().isEmpty()) {
            return;
         }

         int bottom = renderRows(
            g, mc.font, layout.right(), layout.top(), layout.panelWidth(), view.locked() ? "Locked trades" : "Trades", view.trades(), Items.LECTERN, opacity
         );
         int levelHeight = rowsHeight(view.levels());
         if (levels && !view.levels().isEmpty() && bottom + 6 + levelHeight <= layout.bottom()) {
            renderRows(
               g,
               mc.font,
               layout.right(),
               bottom + 6,
               layout.panelWidth(),
               view.locked() ? "Predicted" : "Level preview",
               view.levels(),
               Items.EXPERIENCE_BOTTLE,
               opacity
            );
         }
      } finally {
         g.pose().popMatrix();
      }
   }

   private void resetFade() {
      this.fade.reset();
      this.world = null;
      this.lastFrame = 0L;
   }

   private static int targetHeight(HudView view) {
      return view.warning().isEmpty() ? 61 : 75;
   }

   private static void text(GuiGraphics g, Font font, String value, int x, int y, int color, float opacity) {
      g.drawString(font, value, x, y, VtpUi.alpha(color, opacity));
   }

   public static void renderTarget(GuiGraphics g, Font font, HudView view, int x, int y, int width) {
      renderTarget(g, font, view, x, y, width, 1.0F);
   }

   public static void renderTarget(GuiGraphics g, Font font, HudView view, int x, int y, int width, float opacity) {
      if (!(opacity < 0.015686275F)) {
         VtpUi.panel(g, x, y, width, targetHeight(view), 0.7F * opacity);
         HudItems.render(g, VtpIcons.book(), x + 7, y + 5, opacity);
         text(g, font, "Target", x + 29, y + 9, -1, opacity);
         text(g, font, VtpUi.fitBook(font, view.target(), width - 16), x + 8, y + 27, -1, opacity);
         text(
            g,
            font,
            VtpUi.fit(font, view.result(), width - (view.price() >= 0 ? 55 : 16)),
            x + 8,
            y + 43,
            view.found() ? VtpUi.accentText() : -6250336,
            opacity
         );
         if (view.price() >= 0) {
            String price = String.valueOf(view.price());
            text(g, font, price, x + width - 27 - font.width(price), y + 43, -1, opacity);
            HudItems.render(g, emerald(), x + width - 24, y + 38, opacity);
         }

         if (!view.warning().isEmpty()) {
            text(g, font, VtpUi.fit(font, view.warning(), width - 16), x + 8, y + 59, -13210, opacity);
         }
      }
   }

   private static int renderRows(GuiGraphics g, Font font, int x, int y, int width, String title, List<HudView.Row> rows, Item icon, float opacity) {
      int height = rowsHeight(rows);
      VtpUi.panel(g, x, y, width, height, 0.7F * opacity);
      HudItems.render(g, VtpIcons.of(icon), x + 7, y + 5, opacity);
      text(g, font, VtpUi.fit(font, title, width - 56), x + 29, y + 9, -1, opacity);
      HudItems.render(g, emerald(), x + width - 24, y + 5, opacity);
      int nameX = rows.stream().anyMatch(rowx -> !rowx.label().isEmpty()) ? 27 : 8;

      for (int i = 0; i < rows.size(); i++) {
         HudView.Row row = rows.get(i);
         int ry = y + 27 + i * 16;
         if (row.match()) {
            g.fill(x + 3, ry - 3, x + 4, ry + 10, VtpUi.alpha(VtpUi.accent(), 0.7F * opacity));
         }

         text(g, font, rowMarker(row), x + 8, ry, -6250336, opacity);
         text(g, font, VtpUi.fitBook(font, row.text(), width - nameX - 25), x + nameX, ry, row.match() ? VtpUi.accentText() : -1, opacity);
         if (row.price() >= 0) {
            String price = String.valueOf(row.price());
            text(g, font, price, x + width - 10 - font.width(price), ry, -6250336, opacity);
         }
      }

      return y + height;
   }
}
