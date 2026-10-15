package net.manaz.vtp.gui;

import java.lang.reflect.Field;
import net.minecraft.client.gui.screens.Screen;

/** Opens the mod's package-private screens for the end-to-end test. */
public final class GuiAccess {
   private GuiAccess() {
   }

   public static Screen settingsOnTab(int tab) {
      try {
         Field lastTab = VtpSettingsScreen.class.getDeclaredField("lastTab");
         lastTab.setAccessible(true);
         lastTab.setInt(null, tab);
      } catch (ReflectiveOperationException e) {
         throw new AssertionError(e);
      }

      return new VtpSettingsScreen();
   }

   public static Screen targetsOfKind(int kind) {
      try {
         Field lastKind = VtpSettingsScreen.class.getDeclaredField("lastTargetKind");
         lastKind.setAccessible(true);
         lastKind.setInt(null, kind);
      } catch (ReflectiveOperationException e) {
         throw new AssertionError(e);
      }

      return settingsOnTab(1);
   }

   public static Screen hudSettings(Screen parent) {
      return new VtpHudSettingsScreen(parent);
   }

   public static Screen enchantmentPicker(Screen parent) {
      return new EnchantmentPickerScreen(parent, chosen -> {
      });
   }
}
