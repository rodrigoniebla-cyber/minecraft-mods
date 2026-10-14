package net.manaz.vtp.mixin;

import net.manaz.vtp.render.HudItemOpacity;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({GuiItemRenderState.class})
public abstract class GuiItemRenderStateMixin implements HudItemOpacity {
   @Unique
   private float vtp$opacity = 1.0F;

   @Override
   public float vtp$getOpacity() {
      return this.vtp$opacity;
   }

   @Override
   public void vtp$setOpacity(float opacity) {
      this.vtp$opacity = Math.clamp(opacity, 0.0F, 1.0F);
   }
}
