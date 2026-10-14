package net.manaz.vtp.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.gui.VtpSettingsScreen;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerData;

public class VtpKeybinds {
   private static KeyMapping toggleKey;
   private static KeyMapping openGuiKey;
   private static KeyMapping rerollUpKey;
   private static KeyMapping rerollDownKey;

   public static void register() {
      Category vtpCategory = Category.register(Identifier.fromNamespaceAndPath("villager-trading-predictor", "category"));
      toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.villager-trading-predictor.toggle", 25, vtpCategory));
      openGuiKey = KeyBindingHelper.registerKeyBinding(
         new KeyMapping("key.villager-trading-predictor.open_gui", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), vtpCategory)
      );
      rerollUpKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.villager-trading-predictor.reroll_up", 82, vtpCategory));
      rerollDownKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.villager-trading-predictor.reroll_down", 81, vtpCategory));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         while (toggleKey.consumeClick()) {
            boolean newState = !VillagerHudRenderer.isEnabled();
            VillagerHudRenderer.setEnabled(newState);
            VtpPersistence.saveGlobalSettings();
            if (client.player != null) {
               client.player.displayClientMessage(Component.literal("[VTP] Overlay " + (newState ? "enabled" : "disabled")), false);
            }
         }

         while (openGuiKey.consumeClick()) {
            if (client.screen == null) {
               client.setScreen(new VtpSettingsScreen());
            }
         }

         while (rerollUpKey.consumeClick()) {
            adjustLastVillagerReroll(client, 1);
         }

         while (rerollDownKey.consumeClick()) {
            adjustLastVillagerReroll(client, -1);
         }
      });
   }

   public static KeyMapping getOpenGuiKey() {
      return openGuiKey;
   }

   private static void adjustLastVillagerReroll(Minecraft client, int delta) {
      if (client.screen == null && client.getSingleplayerServer() == null) {
         UUID uuid = VillagerTracker.getLastVillagerUuid();
         Optional<VillagerData> data = VillagerTracker.getLastVillagerData();
         if (uuid != null && !data.isEmpty()) {
            VillagerTracker.adjustRerollCount(uuid, data.get(), delta);
            if (client.player != null) {
               client.player.displayClientMessage(Component.literal("[VTP] Reroll count: " + VillagerTracker.getRerollCount(uuid)), false);
            }
         }
      }
   }
}
