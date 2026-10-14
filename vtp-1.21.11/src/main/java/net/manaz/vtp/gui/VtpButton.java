package net.manaz.vtp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public final class VtpButton extends Button {
   private final VtpMotion.Value hover = new VtpMotion.Value();
   private Item icon;
   private boolean selected;

   public VtpButton(String label, int x, int y, int width, Runnable action) {
      super(x, y, width, 20, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
   }

   public VtpButton icon(Item value) {
      this.icon = value;
      return this;
   }

   public VtpButton selected(boolean value) {
      this.selected = value;
      return this;
   }

   protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
      Font font = Minecraft.getInstance().font;
      int x = this.getX();
      int y = this.getY();
      float glow = this.hover.sample(this.active && this.isHoveredOrFocused() ? 1.0F : 0.0F);
      VtpUi.panel(g, x, y, this.width, this.height, 1.0F);
      if (glow > 0.0F || this.selected) {
         g.fill(x + 1, y + 1, x + this.width - 1, y + this.height - 1, VtpUi.alpha(VtpUi.accent(), this.selected ? 0.1F : glow * 0.06F));
         int length = this.selected ? this.width - 4 : Math.round((this.width - 4) * glow);
         int start = x + (this.width - length) / 2;
         g.fill(start, y + this.height - 2, start + length, y + this.height - 1, VtpUi.alpha(VtpUi.accent(), this.selected ? 0.85F : glow * 0.65F));
      }

      String label = this.getMessage().getString();
      boolean showIcon = this.icon != null && this.width >= font.width(label) + 30;
      int labelWidth = this.width - (showIcon ? 28 : 4);
      String fitted = VtpUi.fit(font, label, labelWidth);
      int color = this.selected ? VtpUi.accentText() : (this.active ? -1 : -9408400);
      int textX = showIcon ? x + 24 : x + (this.width - font.width(fitted)) / 2;
      if (showIcon) {
         g.renderItem(VtpIcons.of(this.icon), x + 4, y + 2);
      }

      g.drawString(font, fitted, textX, y + 6, color, true);
      if (this.isHovered() && !fitted.equals(label)) {
         VtpUi.tooltip(g, font, label, mx, my);
      }
   }
}
