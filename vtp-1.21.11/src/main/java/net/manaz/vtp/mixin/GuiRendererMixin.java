package net.manaz.vtp.mixin;

import net.manaz.vtp.render.HudFade;
import net.manaz.vtp.render.HudItemOpacity;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({GuiRenderer.class})
public abstract class GuiRendererMixin {
   @ModifyArgs(
      method = {"submitBlitFromItemAtlas"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/render/state/BlitRenderState;<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2f;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"
      )}
   )
   private void vtp$fadeHudIcon(Args args, GuiItemRenderState item, float u, float v, int size, int atlasSize) {
      float opacity = ((HudItemOpacity)(Object)item).vtp$getOpacity();
      if (opacity < 1.0F) {
         args.set(11, HudFade.itemTint((Integer)args.get(11), opacity));
      }
   }
}
