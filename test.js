// Self-check del parser de peso.  Correr:  node test.js
// Lee el bloque <script id="core"> de index.html para no duplicar codigo.
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
const m = html.match(/<script id="core">([\s\S]*?)<\/script>/);
assert.ok(m, 'no se encontro <script id="core"> en index.html');
(0, eval)(m[1]);

const { parseWeight, format } = globalThis.PesoCore;
const REAL = '921016103310301150010262960731r17260817';

const cases = [
  ['codigo real de la etiqueta -> 11.5 kg', function () {
    const r = parseWeight(REAL);
    assert.strictEqual(r.kg, 11.5);
    assert.strictEqual(r.source, 'GS1 AI 3103');
    assert.strictEqual(r.warn, null);
  }],

  ['AI 3102 (2 decimales) -> 11.5 kg', function () {
    assert.strictEqual(parseWeight('0100000000000000' + '3102' + '001150').kg, 11.5);
  }],

  ['recorte de respaldo, empieza en 3 -> se salta el 3', function () {
    assert.strictEqual(parseWeight('3011500', { offset: 1, len: 7 }).kg, 11.5);
  }],

  ['recorte de respaldo, empieza en 2 -> se salta el 2', function () {
    assert.strictEqual(parseWeight('2011500', { offset: 1, len: 7 }).kg, 11.5);
  }],

  ['recorte de respaldo sin prefijo 2/3 -> toma los 6 primeros', function () {
    assert.strictEqual(parseWeight('011500', { offset: 1, len: 7 }).kg, 11.5);
  }],

  ['offset por defecto 13/7 sobre el codigo real', function () {
    assert.strictEqual(parseWeight(REAL.replace('3103', 'XYZ3')).kg, 11.5);
  }],

  ['codigo sin peso -> error, no inventa numero', function () {
    const r = parseWeight('abc123');
    assert.strictEqual(r.ok, false);
    assert.ok(!('kg' in r));
  }],

  ['codigo vacio -> error', function () {
    assert.strictEqual(parseWeight('').ok, false);
    assert.strictEqual(parseWeight(null).ok, false);
  }],

  ['peso fuera de rango -> marca advertencia', function () {
    const r = parseWeight('3100999999');
    assert.strictEqual(r.kg, 999999);
    assert.ok(r.warn, 'deberia advertir');
  }],

  ['limpia separadores GS y espacios', function () {
    assert.strictEqual(parseWeight('  ' + REAL.slice(0, 20) + '\x1d' + REAL.slice(20) + '\r\n').kg, 11.5);
  }],

  ['formato decimal coma / punto', function () {
    assert.strictEqual(format(11.5, 'coma'), '11,5');
    assert.strictEqual(format(11.5, 'punto'), '11.5');
    assert.strictEqual(format(921.016, 'coma'), '921,016');
  }]
];

let fail = 0;
cases.forEach(function (c) {
  try { c[1](); console.log('PASS  ' + c[0]); }
  catch (e) { fail++; console.log('FAIL  ' + c[0] + '  ::  ' + e.message); }
});
console.log(fail ? '\n' + fail + ' fallo(s)' : '\nTodo OK (' + cases.length + ' casos)');
process.exit(fail ? 1 : 0);
