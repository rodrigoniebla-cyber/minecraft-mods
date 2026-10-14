package net.manaz.vtp.gui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

class VtpSlider extends AbstractSliderButton {
   private final int min;
   private final int max;
   private final IntFunction<String> label;
   private final IntConsumer change;

   VtpSlider(int x, int y, int width, int min, int max, int initial, IntFunction<String> label, IntConsumer change) {
      super(x, y, width, 20, Component.empty(), (double)(Math.clamp((long)initial, min, max) - min) / (max - min));
      this.min = min;
      this.max = max;
      this.label = label;
      this.change = change;
      this.updateMessage();
   }

   int amount() {
      return this.min + (int)Math.round(this.value * (this.max - this.min));
   }

   void sync(int amount) {
      this.value = (double)(Math.clamp((long)amount, this.min, this.max) - this.min) / (this.max - this.min);
      this.updateMessage();
   }

   protected void applyValue() {
      this.change.accept(this.amount());
   }

   protected void updateMessage() {
      this.setMessage(Component.literal(this.label.apply(this.amount())));
   }

   public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
      Font font = Minecraft.getInstance().font;
      int x = this.getX();
      int y = this.getY();
      VtpUi.panel(g, x, y, this.width, this.height, 1.0F);
      g.fill(x + 4, y + this.height - 4, x + this.width - 4, y + this.height - 3, -13092808);
      int handle = x + 4 + (int)Math.round((this.width - 12) * this.value);
      g.fill(x + 4, y + this.height - 4, handle + 4, y + this.height - 3, VtpUi.alpha(VtpUi.accent(), 0.6F));
      g.fill(handle, y + this.height - 6, handle + 4, y + this.height - 2, VtpUi.accent());
      String fitted = VtpUi.fit(font, this.getMessage().getString(), this.width - 8);
      g.drawString(font, fitted, x + (this.width - font.width(fitted)) / 2, y + 3, this.active ? -1 : -6250336);
      if (this.isHoveredOrFocused()) {
         this.handleCursor(g);
      }
   }
}
