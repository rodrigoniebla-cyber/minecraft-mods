package net.manaz.vtp.mixin;

import net.manaz.vtp.gui.VtpUi;
import net.manaz.vtp.render.HudView;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MerchantScreen.class})
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
   protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
   }

   @Inject(
      method = {"renderContents"},
      at = {@At("RETURN")}
   )
   private void vtp$renderPrediction(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
      if (VillagerHudRenderer.isEnabled()) {
         HudView view = HudView.current();
         if (view != null && view.uuid().equals(VillagerTracker.getMenuVillagerUuid()) && !VillagerHudRenderer.getTargets().isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int room = this.leftPos - 12;
            if (room >= 144) {
               VillagerHudRenderer.renderTarget(g, font, view, this.leftPos - Math.min(220, room) - 6, this.topPos, Math.min(220, room));
            } else {
               int w = Math.min(this.imageWidth, this.width - 12);
               int x = (this.width - w) / 2;
               int y = Math.max(4, this.topPos - 23);
               VtpUi.hud(g, x, y, w, 18);
               String text = view.target() + "  ·  " + (view.warning().isEmpty() ? view.result() : view.warning());
               g.drawString(font, VtpUi.fit(font, text, w - 12), x + 6, y + 5, view.found() ? VtpUi.accentText() : -1);
            }
         }
      }
   }
}
