# Peso Total

Burbuja flotante que se queda **encima del WMS**. Escaneas etiquetas, la burbuja
va sumando los pesos, y al final metes un solo total en el campo del WMS sin
salir de su pantalla.

El peso sale del código de barras: identificador **GS1 AI `310n`** (peso neto en
kilos con `n` decimales). Ejemplo real:

```
921016103310301150010262960731r17260817
          3103 011500   ->  011500 / 10^3  =  11,5 kg
```

Si una etiqueta no trae ese identificador, hay un recorte de posición fija de
respaldo, calibrable desde Ajustes sin recompilar.

---

## 1. Compilar el APK

No hace falta instalar nada en el PC. Compila GitHub.

```bash
git init
git add .
git commit -m "Peso Total"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
git push -u origin main
```

En GitHub, pestaña **Actions** → el job **APK** → al terminar, descarga el
artefacto `peso-total-apk`. Dentro va `app-debug.apk`.

Los tests corren antes de ensamblar: si el parser se rompe, no sale APK.

## 2. Instalar y dar permisos

1. Copia el APK al terminal e instálalo (hay que permitir orígenes desconocidos).
2. Abre **Peso Total**. La pantalla de arriba muestra el estado de cada permiso.
3. **Permiso: mostrar sobre otras apps** → concédelo. Sin esto no hay burbuja.
4. **Activar accesibilidad** → búscala en la lista y actívala. Sirve para que el
   total se escriba solo en el campo.
   Si el MDM de la empresa lo bloquea, **no pasa nada**: la app cae al
   portapapeles automáticamente y el botón pasa a decir *Copiar*.
5. **Mostrar burbuja**.

## 3. Configurar el lector

Esta es la parte que hay que hacer bien. Sin ella la app no recibe nada y
parece rota.

De fábrica el lector trabaja en modo *keyboard wedge*: teclea el código en el
campo que tenga el foco. Así el código llega al WMS pero **nuestra app no lo
ve**, y no puede robarle el foco al WMS para verlo.

La solución es la salida por **intent**: el lector emite además un mensaje
interno de Android que nuestra app escucha esté o no en primer plano.

### Zebra (DataWedge)

1. Abre **DataWedge** en el terminal.
2. Elige el perfil que ya usa el WMS (o crea uno y asócialo a la app del WMS).
3. **Keystroke output** → déjalo **activado**. El WMS sigue funcionando igual
   que hoy.
4. **Intent output** → **activado**, con:
   - Intent action: `cl.icestar.pesototal.SCAN`
   - Intent delivery: **Broadcast intent**
5. En la app, campo *Nombre del extra*, toca **Preset Zebra**
   (`com.symbol.datawedge.data_string`). Guarda.

Con las dos salidas encendidas nadie pierde nada: el WMS recibe el código
tecleado y nosotros recibimos la copia.

### Honeywell

Mismo principio desde *Scanner Settings* / *Enhanced Settings*: activa la salida
por intent con la misma acción, y en la app toca **Preset Honeywell**
(extra `data`). Si ese extra no funciona, prueba
`com.honeywell.aidc.EXTRA_BARCODE_DATA` — la app acepta los dos.

### Verificar

En la pantalla de la app, sección **PRUEBA**: dispara la pistola. Si el código
aparece y sale un peso, está bien configurado. Si no aparece nada, el intent no
está llegando: revisa la acción y que la entrega sea *Broadcast*.

## 4. Uso diario

1. El operario trabaja en el WMS. La burbuja está en el borde, atenuada.
2. Al llegar a pesar, toca el chip **WMS** para pasarlo a **SUMA**.
3. Escanea las etiquetas. La burbuja va marcando `34,7 kg · 3`.
4. Toca la burbuja para ver el detalle, deshacer o borrar una línea.
5. Toca el campo del WMS para enfocarlo y luego **Insertar**.
   Sin accesibilidad el botón dice **Copiar**: mantén pulsado el campo y pega.
6. La suma se reinicia sola. **Deshacer** la recupera si te adelantaste.

La burbuja se arrastra a donde estorbe menos y se imanta al borde.

## 5. Calibración

- **Recorte de respaldo (desde / largo):** solo entra si el código no trae el
  `310n`. Si un peso no calza con la planilla, ajústalo aquí. Por defecto
  *desde 13, largo 7*.
- **Separador decimal:** coma por defecto. Si el WMS rechaza el valor, cámbialo
  a punto.
- **Opacidad y modo barra de borde:** para que estorbe menos.
- **Cambiar de perfil al tocar el interruptor:** apagado por defecto. Enciéndelo
  solo si creaste dos perfiles de DataWedge (`WMS` con teclado + intent, `SUMA`
  con intent solo) **y verificaste que el cambio en caliente funciona en ese
  terminal**. Si falla, el WMS se queda sin escaneos.

## 6. Si algo falla

| Síntoma | Causa más probable |
|---|---|
| La burbuja no aparece | Falta el permiso de superposición, o el servicio se detuvo |
| Escaneo sin efecto | El intent no está configurado, o el chip está en **WMS** |
| "Sin peso en el código" | La etiqueta no trae `310n` y el recorte no calza |
| El botón dice *Copiar* | La accesibilidad no está activa (o la bloqueó el MDM) |
| El total entra mal en el WMS | Coma vs punto |

## Banco de pruebas

`index.html` abre en cualquier navegador y valida el parser contra la planilla
sin compilar nada. `node test.js` corre los mismos 11 casos que los tests de
Kotlin: **es la especificación**, si cambia una regla, cambia en los dos lados.
