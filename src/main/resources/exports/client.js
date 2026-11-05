// Optional JEI catalogue. Reads fresh export data whenever JEI restarts after /reload.
if (Platform.isLoaded('jei')) {
  JEIEvents.subtypes(event => event.useNBT('kubejs:me_infinity_cell'));
  JEIEvents.addItems(event => {
    var catalogue = JsonIO.read('kubejs/config/me_infinity_editor/catalog.json');
    if (catalogue) Object.keys(catalogue).forEach(id => event.add(global.meInfinityCell(catalogue[id])));
  });
}
