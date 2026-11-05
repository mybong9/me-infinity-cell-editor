const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const context = {global: {}};
vm.runInNewContext(fs.readFileSync('src/main/resources/exports/selectors.js', 'utf8'), context);
const tags = {'#forge:ingots': new Set(['minecraft:iron_ingot', 'minecraft:gold_ingot'])};
const compile = text => context.global.meInfinityCompileSelector(text, tag => id => tags[tag]?.has(id) || false);
const ids = ['minecraft:air', 'minecraft:oak_planks', 'minecraft:birch_planks', 'minecraft:iron_ingot', 'minecraft:gold_ingot', 'thermal:tin_ingot'];
const select = text => ids.filter(compile(text));
assert.deepEqual(select('(minecraft:*_planks | #forge:ingots) & !minecraft:gold_ingot'), ids.slice(1, 4));
assert.deepEqual(select('minecraft:oak_??????'), [ids[1]]);
assert.deepEqual(select('iron_ingot || thermal:* && !thermal:tin_ingot'), [ids[3]]);
assert.deepEqual(select('!(minecraft:* | thermal:*)'), []);
assert.deepEqual(select('* & !minecraft:air'), ids.slice(1));
assert.deepEqual(select('missing:* | #missing:tag'), []);
for (const text of ['', 'minecraft:stone &', '(minecraft:stone', 'minecraft:stone)', 'minecraft:stone minecraft:dirt', '#', '#forge:*', 'a:b:c', 'a ||| b', 'a &&& b', 'global.process()']) {
  assert.throws(() => compile(text), /Invalid ME item selector/);
}
// Resolve against a second pack's registry instead of remembering the first pack's IDs.
assert.deepEqual(['thermal:copper_ingot', 'create:zinc_ingot'].filter(compile('thermal:* | create:*')), ['thermal:copper_ingot', 'create:zinc_ingot']);
for (const file of ['startup.js', 'client.js']) new vm.Script(fs.readFileSync('src/main/resources/exports/' + file, 'utf8'));
context.Java = {loadClass: name => ({
  'dev.latvian.mods.rhino.mod.util.NBTUtils': {accessTagMap: tag => ({put: (key, value) => {tag[key] = value;}})},
  'net.minecraft.nbt.StringTag': {valueOf: value => value}
}[name] || {})};
context.StartupEvents = {registry() {}, postInit() {}};
context.Item = {of: (id, count, nbt) => ({id, nbt: {original: nbt}})};
context.Ingredient = {of: tag => ({test: stack => tags[tag]?.has(stack.id) || false})};
vm.runInNewContext(fs.readFileSync('src/main/resources/exports/startup.js', 'utf8'), context);
const original = '{Items:[{id:"minecraft:diamond",Count:1b,tag:{variant:"a"}}],Keys:[]}';
assert.equal(context.global.meInfinityCell(original).nbt.original, original);
const portable = context.global.meInfinityCell({nbt: original, itemSelector: '#forge:ingots | minecraft:*_planks'});
assert.equal(portable.nbt.original, original);
assert.equal(portable.nbt.ItemSelector, '#forge:ingots | minecraft:*_planks');
assert.throws(() => context.global.meInfinityCell({nbt: original, itemSelector: 'x &'}), /Invalid ME item selector/);
console.log('Selector precedence, globs, tags, invalid input, pack migration and script syntax: PASS');
