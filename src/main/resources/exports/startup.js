// Standalone runtime: AE2 + KubeJS only. Restart once after installing this file.
(() => {
  var ID = 'kubejs:me_infinity_cell';
  var CellHandler = Java.loadClass('appeng.api.storage.cells.ICellHandler');
  var StorageCell = Java.loadClass('appeng.api.storage.cells.StorageCell');
  var MEStorage = Java.loadClass('appeng.api.storage.MEStorage');
  var MEInventoryHandler = Java.loadClass('appeng.me.storage.MEInventoryHandler');
  var StorageCells = Java.loadClass('appeng.api.storage.StorageCells');
  var AEItemKey = Java.loadClass('appeng.api.stacks.AEItemKey');
  var AEKey = Java.loadClass('appeng.api.stacks.AEKey');
  var KeyCounter = Java.loadClass('appeng.api.stacks.KeyCounter');
  var CellState = Java.loadClass('appeng.api.storage.cells.CellState');
  var Component = Java.loadClass('net.minecraft.network.chat.Component');
  var Long = Java.loadClass('java.lang.Long');
  var NBTUtils = Java.loadClass('dev.latvian.mods.rhino.mod.util.NBTUtils');
  StartupEvents.registry('item', event => {
    event.create(ID).displayName('ME 自定义无限元件').maxStackSize(1).texture('ae2:item/creative_item_cell');
  });
  StartupEvents.postInit(() => {
    if (global.meInfinityStandaloneInstalled) return;
    StorageCells.addCellHandler(new JavaAdapter(CellHandler, {
      isCell: function(stack) { return String(stack.id) === ID; },
      getCellInventory: function(stack, host) {
        if (String(stack.id) !== ID) return null;
        var keys = [];
        if (stack.nbt) {
          var samples = NBTUtils.accessTagMap(stack.nbt).get('Items');
          if (samples) for (var i = 0; i < samples.size(); i++) {
            var key = AEItemKey.fromTag(samples.get(i));
            if (key !== null && !keys.some(k => k.equals(key))) keys.push(key);
          }
          var extra = NBTUtils.accessTagMap(stack.nbt).get('Keys');
          if (extra) for (var n = 0; n < extra.size(); n++) {
            try {
              var resourceKey = AEKey.fromTagGeneric(extra.get(n));
              if (resourceKey !== null && !keys.some(k => k.equals(resourceKey))) keys.push(resourceKey);
            } catch (error) { console.warn('ME resource type unavailable; retain the required AE2 resource bridge: ' + error); }
          }
        }
        // Rhino skips interface default methods. Extend AE2's concrete handler so
        // extract/insert/getAvailableStacks are real Java method overrides.
        var emptyDelegate = new JavaAdapter(MEStorage, { getDescription: function() { return Text.of('ME 无限元件'); } });
        return new JavaAdapter(MEInventoryHandler, StorageCell, {
          getStatus: function() { return keys.length ? CellState.NOT_EMPTY : CellState.EMPTY; },
          getIdleDrain: function() { return 0; },
          canFitInsideCell: function() { return false; },
          persist: function() {},
          getDescription: function() { return Text.of('ME 无限元件'); },
          insert: function(what, amount, mode, source) { return 0; },
          extract: function(what, amount, mode, source) { return amount > 0 && keys.some(k => k.equals(what)) ? amount : 0; },
          getAvailableStacks: function(out) { if (!out) out = new KeyCounter(); keys.forEach(k => out.add(k, Long.valueOf('9223372036854775807'))); return out; }
        }, emptyDelegate);
      }
    }));
    global.meInfinityStandaloneInstalled = true;
  });
  // NBT-aware stonecutting stays available after removing the editor JAR.
  StartupEvents.registry('recipe_serializer', event => {
    var Serializer = Java.loadClass('net.minecraft.world.item.crafting.RecipeSerializer');
    var Stonecutter = Java.loadClass('net.minecraft.world.item.crafting.StonecutterRecipe');
    var Ingredient = Java.loadClass('net.minecraft.world.item.crafting.Ingredient');
    var CraftingHelper = Java.loadClass('net.minecraftforge.common.crafting.CraftingHelper');
    var RegistryAccess = Java.loadClass('net.minecraft.core.RegistryAccess');
    var serializer;
    var make = (id, group, input, output) => {
      var methods = { getSerializer: function() { return serializer; } };
      methods.m_7707_ = methods.getSerializer;
      return new JavaAdapter(Stonecutter, methods, id, group, input, output);
    };
    var methods = {
      fromJson: function(id, json) { return make(id, json.has('group') ? json.get('group').getAsString() : '', Ingredient.fromJson(json.get('ingredient')), CraftingHelper.getItemStack(json.getAsJsonObject('result'), true)); },
      fromNetwork: function(id, buf) { return make(id, buf.readUtf(), Ingredient.fromNetwork(buf), buf.readItem()); },
      toNetwork: function(buf, recipe) { buf.writeUtf(recipe.getGroup()); recipe.getIngredients().get(0).toNetwork(buf); buf.writeItem(recipe.getResultItem(RegistryAccess.EMPTY)); }
    };
    methods.m_6729_ = methods.fromJson;
    methods.m_8005_ = methods.fromNetwork;
    methods.m_6178_ = methods.toNetwork;
    serializer = new JavaAdapter(Serializer, methods);
    event.createCustom('kubejs:me_infinity_stonecutting', () => serializer);
  });
})();
