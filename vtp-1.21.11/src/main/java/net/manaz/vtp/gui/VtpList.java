package net.manaz.vtp.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VtpList extends AbstractWidget {
   private static final int ROW = 20;
   private List<VtpList.Entry> entries = List.of();
   private int offset;
   private int selected = -1;
   private boolean dragging;
   private final Map<Integer, VtpMotion.Value> hoverMotion = new HashMap<>();

   public VtpList(int x, int y, int w, int h, String title) {
      super(x, y, w, h, Component.literal(title));
   }

   public void entries(List<VtpList.Entry> values) {
      this.entries = List.copyOf(values);
      this.offset = Math.clamp((long)this.offset, 0, this.maxOffset());
      this.selected = Math.min(this.selected, this.entries.size() - 1);
      this.hoverMotion.keySet().removeIf(index -> index >= this.entries.size());
   }

   public int offset() {
      return this.offset;
   }

   public void offset(int value) {
      this.offset = Math.clamp((long)value, 0, this.maxOffset());
   }

   public void selected(int value) {
      this.selected = value;
   }

   public int selected() {
      return this.selected;
   }

   public int size() {
      return this.entries.size();
   }

   private int visibleRows() {
      return Math.max(1, (this.height - 4) / 20);
   }

   private int maxOffset() {
      return Math.max(0, this.entries.size() - this.visibleRows());
   }

   protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
      Font font = Minecraft.getInstance().font;
      int x = this.getX();
      int y = this.getY();
      VtpUi.slot(g, x, y, this.width, this.height);
      g.enableScissor(x + 1, y + 1, x + this.width - 1, y + this.height - 1);
      if (this.entries.isEmpty()) {
         g.drawString(font, "—", x + 7, y + 7, -1, true);
      }

      for (int r = 0; r < this.visibleRows() && this.offset + r < this.entries.size(); r++) {
         int index = this.offset + r;
         int ry = y + 2 + r * 20;
         VtpList.Entry e = this.entries.get(index);
         boolean hovered = mx >= x + 1 && mx < x + this.width - 8 && my >= ry && my < ry + 20;
         boolean marked = this.selected == index || e.highlight();
         float glow = this.hoverMotion
            .computeIfAbsent(index, ignored -> new VtpMotion.Value())
            .sample(!marked && (!hovered || e.action() == null) ? 0.0F : 1.0F);
         if (glow > 0.0F) {
            g.fill(x + 2, ry, x + this.width - 8, ry + 20, VtpUi.alpha(VtpUi.accent(), glow * 0.09F));
            if (marked) {
               g.fill(x + 2, ry + 2, x + 3, ry + 20 - 2, VtpUi.alpha(VtpUi.accent(), glow * 0.8F));
            }
         }

         int tx = x + 6;
         if (!e.trade().isEmpty()) {
            int sx = tx;

            for (int i = 0; i < e.trade().size(); i++) {
               if (i != 1 || !e.trade().get(i).isEmpty()) {
                  if (i == 2) {
                     g.fill(sx, ry + 8, sx + 9, ry + 10, -1);

                     for (int a = 0; a < 4; a++) {
                        g.fill(sx + 6 + a, ry + 5 + a, sx + 7 + a, ry + 13 - a, -1);
                     }

                     sx += 14;
                  } else if (i == 1) {
                     g.drawString(font, "+", sx, ry + 6, -1, true);
                     sx += 9;
                  }

                  ItemStack stack = e.trade().get(i);
                  if (!stack.isEmpty()) {
                     g.renderItem(stack, sx, ry + 1);
                     g.renderItemDecorations(font, stack, sx, ry + 1);
                     if (hovered && mx >= sx && mx < sx + 16) {
                        g.setTooltipForNextFrame(font, stack, mx, my);
                     }
                  }

                  sx += 20;
               }
            }

            tx = sx + 4;
         } else if (!e.icon().isEmpty()) {
            g.renderItem(e.icon(), tx, ry + 1);
            tx += 20;
         }

         boolean emeraldPrice = e.suffix().endsWith(" em");
         String suffix = emeraldPrice ? e.suffix().substring(0, e.suffix().length() - 3) : e.suffix();
         int suffixW = suffix.isEmpty() ? 0 : font.width(suffix) + (emeraldPrice ? 23 : 6);
         if (e.trade().isEmpty() || x + this.width - 12 - tx - suffixW >= 50) {
            g.drawString(
               font, VtpUi.fitBook(font, e.text(), Math.max(0, x + this.width - 12 - tx - suffixW)), tx, ry + 6, e.highlight() ? VtpUi.accentText() : -1, true
            );
         }

         if (!suffix.isEmpty()) {
            int sx = x + this.width - 12 - (emeraldPrice ? 17 : 0) - font.width(suffix);
            g.drawString(font, suffix, sx, ry + 6, -6250336, true);
            if (emeraldPrice) {
               g.renderItem(VtpIcons.of(Items.EMERALD), x + this.width - 27, ry + 1);
            }
         }

         if (hovered && e.trade().isEmpty() && !e.tooltip().isEmpty()) {
            VtpUi.tooltip(g, font, e.tooltip(), mx, my);
         }
      }

      g.disableScissor();
      if (this.maxOffset() > 0) {
         int track = this.height - 4;
         int thumb = Math.max(12, track * this.visibleRows() / this.entries.size());
         int top = y + 2 + (track - thumb) * this.offset / this.maxOffset();
         g.fill(x + this.width - 7, y + 2, x + this.width - 2, y + this.height - 2, -15198184);
         g.fill(x + this.width - 7, top, x + this.width - 2, top + thumb, -11513776);
         g.fill(x + this.width - 7, top, x + this.width - 6, top + thumb - 1, -9145228);
      }
   }

   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      if (event.x() >= this.getX() + this.width - 8 && this.maxOffset() > 0) {
         this.dragging = true;
         this.dragTo(event.y());
      } else {
         int index = this.offset + (int)(event.y() - this.getY() - 2.0) / 20;
         if (index >= 0 && index < this.entries.size() && index < this.offset + this.visibleRows()) {
            this.selected = index;
            if (this.entries.get(index).action() != null) {
               this.entries.get(index).action().run();
            }
         }
      }
   }

   private void dragTo(double y) {
      int track = this.height - 4;
      int thumb = Math.max(12, track * this.visibleRows() / this.entries.size());
      this.offset((int)Math.round((y - this.getY() - 2.0 - thumb / 2.0) * this.maxOffset() / Math.max(1, track - thumb)));
   }

   protected void onDrag(MouseButtonEvent event, double dx, double dy) {
      if (this.dragging) {
         this.dragTo(event.y());
      }
   }

   public void onRelease(MouseButtonEvent event) {
      this.dragging = false;
   }

   public boolean mouseScrolled(double x, double y, double sx, double sy) {
      if (this.isMouseOver(x, y) && sy != 0.0) {
         this.offset(this.offset - (int)Math.signum(sy) * 3);
         return true;
      } else {
         return false;
      }
   }

   public boolean keyPressed(KeyEvent event) {
      int key = event.key();
      if (key != 82 && key != 81 && key != 75 && key != 78 && key != 74 && key != 77) {
         if ((key == 40 || key == 44) && this.selected >= 0 && this.selected < this.entries.size()) {
            Runnable action = this.entries.get(this.selected).action();
            if (action != null) {
               this.playDownSound(Minecraft.getInstance().getSoundManager());
               action.run();
            }

            return true;
         } else {
            return false;
         }
      } else if (this.entries.isEmpty()) {
         return true;
      } else {
         int step = key != 75 && key != 78 ? 1 : this.visibleRows();
         this.selected = key == 74
            ? 0
            : (key == 77 ? this.entries.size() - 1 : Math.clamp((long)(this.selected + (key != 82 && key != 75 ? step : -step)), 0, this.entries.size() - 1));
         if (this.selected < this.offset) {
            this.offset(this.selected);
         }

         if (this.selected >= this.offset + this.visibleRows()) {
            this.offset(this.selected - this.visibleRows() + 1);
         }

         return true;
      }
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, this.getMessage());
      if (this.selected >= 0 && this.selected < this.entries.size()) {
         output.add(NarratedElementType.USAGE, this.entries.get(this.selected).tooltip());
      } else {
         output.add(NarratedElementType.USAGE, "Use arrow keys to browse, Enter to select.");
      }
   }

   public record Entry(String text, String suffix, ItemStack icon, List<ItemStack> trade, String tooltip, boolean highlight, Runnable action) {
      public static VtpList.Entry text(String text, Runnable action) {
         return new VtpList.Entry(text, "", ItemStack.EMPTY, List.of(), text, false, action);
      }
   }
}
