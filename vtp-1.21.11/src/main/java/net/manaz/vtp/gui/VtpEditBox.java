package net.manaz.vtp.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class VtpEditBox extends EditBox {
   private final VtpMotion.Value focus = new VtpMotion.Value();

   public VtpEditBox(Font font, int x, int y, int w, int h, Component label) {
      super(font, x, y, w, h, label);
      this.setTextColor(-1);
      this.setTextColorUneditable(-6250336);
      this.setTextShadow(true);
   }

   public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
      super.renderWidget(g, mx, my, delta);
      if (this.isVisible() && this.isBordered()) {
         int x = this.getX();
         int y = this.getY();
         int w = this.getWidth();
         int h = this.getHeight();
         int edge = this.active ? -11513776 : -13092808;
         g.fill(x, y, x + w, y + 1, edge);
         g.fill(x, y + h - 1, x + w, y + h, edge);
         g.fill(x, y, x + 1, y + h, edge);
         g.fill(x + w - 1, y, x + w, y + h, edge);
         float glow = this.focus.sample(this.active && this.isFocused() ? 1.0F : 0.0F);
         int length = Math.round((w - 2) * glow);
         int start = x + (w - length) / 2;
         if (length > 0) {
            g.fill(start, y + h - 1, start + length, y + h, VtpUi.alpha(VtpUi.accent(), glow * 0.8F));
         }
      }
   }
}
