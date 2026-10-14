package net.manaz.vtp.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Minimal reader for loot-table number providers as they appear in 26.2 trade JSON. */
final class Numbers {
   private Numbers() {
   }

   static boolean isConstant(JsonElement element) {
      if (element == null) {
         return false;
      } else if (element.isJsonPrimitive()) {
         return element.getAsJsonPrimitive().isNumber();
      } else if (element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         if (!object.has("type") || !object.has("value")) {
            return false;
         } else {
            String type = object.get("type").getAsString();
            return (type.equals("minecraft:constant") || type.equals("constant")) && isConstant(object.get("value"));
         }
      } else {
         return false;
      }
   }

   static float constantFloat(JsonElement element) {
      if (element.isJsonObject()) {
         return constantFloat(element.getAsJsonObject().get("value"));
      } else {
         return element.getAsFloat();
      }
   }

   /** Mirrors {@code NumberProvider.getInt}, which rounds the float value. */
   static int constantInt(JsonElement element) {
      return Math.round(constantFloat(element));
   }
}
