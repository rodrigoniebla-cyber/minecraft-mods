package net.manaz.vtp.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

public final class HudItems {
   private HudItems() {
   }

   public static void render(GuiGraphics g, ItemStack stack, int x, int y, float opacity) {
      if (opacity >= 1.0F) {
         g.renderItem(stack, x, y);
      } else if (!(opacity <= 0.0F) && !stack.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         TrackingItemStackRenderState model = new TrackingItemStackRenderState();
         mc.getItemModelResolver().updateForTopItem(model, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
         GuiItemRenderState item = new GuiItemRenderState(stack.getItem().getName().toString(), new Matrix3x2f(g.pose()), model, x, y, g.scissorStack.peek());
         ((HudItemOpacity)(Object)item).vtp$setOpacity(opacity);
         g.guiRenderState.submitItem(item);
      }
   }
}
