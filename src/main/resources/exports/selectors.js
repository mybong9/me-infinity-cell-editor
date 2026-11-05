// priority: 1000
// Item IDs/globs, #tags; precedence: ! then &/&& then |/||. No eval.
global.meInfinityCompileSelector = function(expression, tagTest) {
  var text = String(expression), at = 0;
  function space() { while (/\s/.test(text.charAt(at)) && at < text.length) at++; }
  function take(value) { space(); if (text.substr(at, value.length) !== value) return false; at += value.length; return true; }
  function fail() { throw new Error('Invalid ME item selector at ' + at + ': ' + text); }
  function unary() {
    if (take('!')) { var child = unary(); return function(id) { return !child(id); }; }
    if (take('(')) { var nested = union(); if (!take(')')) fail(); return nested; }
    space(); var start = at;
    while (at < text.length && /[a-z0-9_:./*?#-]/.test(text.charAt(at))) at++;
    var atom = text.substring(start, at);
    if (!atom || atom === '#') fail();
    if (atom.charAt(0) === '#') {
      if (!/^#[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(atom)) fail();
      return tagTest(atom);
    }
    if (atom.indexOf(':') < 0 && atom !== '*') atom = 'minecraft:' + atom;
    if (!/^(\*|[a-z0-9_*?.-]+:[a-z0-9_/*?.-]+)$/.test(atom)) fail();
    var regex = new RegExp('^' + atom.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.') + '$');
    return function(id) { return regex.test(id); };
  }
  function intersection() {
    var left = unary();
    while (take('&&') || take('&')) { var right = unary(); left = (function(a, b) { return function(id) { return a(id) && b(id); }; })(left, right); }
    return left;
  }
  function union() {
    var left = intersection();
    while (take('||') || take('|')) { var right = intersection(); left = (function(a, b) { return function(id) { return a(id) || b(id); }; })(left, right); }
    return left;
  }
  var result = union(); space(); if (at !== text.length) fail(); return result;
};
