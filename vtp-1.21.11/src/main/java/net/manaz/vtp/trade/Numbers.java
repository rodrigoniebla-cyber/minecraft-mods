package net.manaz.vtp.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.minecraft.util.Mth;

/**
 * Reader for loot-table number providers as they appear in 26.2 trade JSON. Evaluation consumes the
 * random sequence exactly like the 26.2 providers (constant, uniform, binomial, sum).
 */
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
         return type(object).equals("minecraft:constant") && isConstant(object.get("value"));
      } else {
         return false;
      }
   }

   /** True when every provider in the tree is one this class can evaluate. */
   static boolean isSupported(JsonElement element) {
      if (element == null) {
         return false;
      } else if (element.isJsonPrimitive()) {
         return element.getAsJsonPrimitive().isNumber();
      } else if (!element.isJsonObject()) {
         return false;
      } else {
         JsonObject object = element.getAsJsonObject();
         return switch (type(object)) {
            case "minecraft:constant" -> isSupported(object.get("value"));
            case "minecraft:uniform" -> isSupported(object.get("min")) && isSupported(object.get("max"));
            case "minecraft:binomial" -> isSupported(object.get("n")) && isSupported(object.get("p"));
            case "minecraft:sum" -> object.has("summands") && object.get("summands").isJsonArray()
               && object.getAsJsonArray("summands").asList().stream().allMatch(Numbers::isSupported);
            default -> false;
         };
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

   static int getInt(JsonElement element, SimulatedRandomSource random) {
      if (element.isJsonPrimitive()) {
         return Math.round(element.getAsFloat());
      } else {
         JsonObject object = element.getAsJsonObject();
         return switch (type(object)) {
            case "minecraft:constant" -> Math.round(getFloat(object.get("value"), random));
            case "minecraft:uniform" -> {
               int min = getInt(object.get("min"), random);
               int max = getInt(object.get("max"), random);
               yield min >= max ? min : random.nextInt(max - min + 1) + min;
            }
            case "minecraft:binomial" -> binomial(object, random);
            case "minecraft:sum" -> Mth.floor(sum(object, random));
            default -> throw new IllegalArgumentException("Unsupported number provider " + object);
         };
      }
   }

   static float getFloat(JsonElement element, SimulatedRandomSource random) {
      if (element.isJsonPrimitive()) {
         return element.getAsFloat();
      } else {
         JsonObject object = element.getAsJsonObject();
         return switch (type(object)) {
            case "minecraft:constant" -> getFloat(object.get("value"), random);
            case "minecraft:uniform" -> {
               float min = getFloat(object.get("min"), random);
               float max = getFloat(object.get("max"), random);
               yield min >= max ? min : random.nextFloat() * (max - min) + min;
            }
            case "minecraft:binomial" -> binomial(object, random);
            case "minecraft:sum" -> sum(object, random);
            default -> throw new IllegalArgumentException("Unsupported number provider " + object);
         };
      }
   }

   private static int binomial(JsonObject object, SimulatedRandomSource random) {
      int n = getInt(object.get("n"), random);
      float p = getFloat(object.get("p"), random);
      int result = 0;

      for (int i = 0; i < n; i++) {
         if (random.nextFloat() < p) {
            result++;
         }
      }

      return result;
   }

   private static float sum(JsonObject object, SimulatedRandomSource random) {
      float value = 0.0F;

      for (JsonElement summand : object.getAsJsonArray("summands")) {
         value += getFloat(summand, random);
      }

      return value;
   }

   private static String type(JsonObject object) {
      if (!object.has("type")) {
         // A bare {"min":..,"max":..} object is the uniform shorthand.
         return object.has("min") && object.has("max") ? "minecraft:uniform" : (object.has("value") ? "minecraft:constant" : "");
      } else {
         String type = object.get("type").getAsString();
         return type.contains(":") ? type : "minecraft:" + type;
      }
   }
}
