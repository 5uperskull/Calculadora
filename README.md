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

## 3. Configurar el lector (DataWedge)

Esta es la parte que hay que hacer bien. Sin ella la app no recibe nada y
parece rota.

### Primero: la traducción de la UI

Los menús de DataWedge en español no coinciden con la documentación de Zebra,
que está en inglés. Esta es la correspondencia de lo que vas a ver:

| Español (tu terminal) | Inglés (documentación Zebra) |
|---|---|
| Salida de pulsación de teclas | Keystroke output |
| Salida de intents | Intent output |
| Acción del intent | Intent action |
| Categoría del intent | Intent category |
| Entrega del intent | Intent delivery |
| **Emitir intent** | **Broadcast intent** ← esta es la que necesitas |
| enviar mediante startActivity | Send via startActivity |
| enviar mediante startService | Send via startService |
| enviar mediante startForegroundService | Send via startForegroundService |

**`Emitir intent` es Broadcast intent.** Las otras tres arrancan un componente
en lugar de emitir un aviso general, y no sirven aquí: la app escucha un
*broadcast*.

### Segundo: qué perfil hay que editar

Un perfil de DataWedge se activa cuando la app que tiene asociada pasa a
primer plano. Si editas el perfil equivocado, la configuración no se aplica
nunca mientras el operario usa el WMS.

1. Abre **DataWedge** y mira la lista de perfiles.
2. Entra en cada uno y revisa **Aplicaciones asociadas**. El que tenga la app
   del WMS es el que hay que editar.
3. Si ninguno la tiene, el que se está usando es **Profile0 (default)**, el
   comodín que cubre a toda app sin perfil propio. Ese es el que editas.

> Editar Profile0 afecta a todas las apps del terminal. Es aceptable aquí
> porque solo vamos a **añadir** una salida, sin quitar la que ya existe.

### Configuración A — la recomendada, no puede romper nada

El WMS sigue recibiendo el código exactamente como hoy, y nosotros recibimos
una copia. Las dos salidas conviven.

Dentro del perfil que identificaste:

1. **Perfil habilitado** → activado.
2. **Entrada de código de barras** → activada (ya lo estará).
3. **Salida de pulsación de teclas** → **déjala activada**. Es la que teclea el
   código en el campo del WMS. Si la apagas aquí, rompes el flujo actual.
4. **Salida de intents** → **actívala**, y dentro:
   - **Acción del intent**: `cl.icestar.pesototal.SCAN`
     (exactamente igual que en la app, campo *Acción del intent* de Ajustes)
   - **Categoría del intent**: **déjala vacía**
   - **Entrega del intent**: **Emitir intent**
5. Sal del perfil. DataWedge guarda solo, no hay botón de guardar.

En la app: campo *Nombre del extra* → botón **Preset Zebra**
(`com.symbol.datawedge.data_string`) → **Guardar y reiniciar burbuja**.

Con esta configuración el interruptor de la burbuja **no** corta el código al
WMS: solo decide si sumamos o no. Es a propósito — es lo único que no puede
dejar al operario sin poder trabajar.

### Configuración B — dos perfiles, para que el interruptor corte de verdad

Solo si necesitas que en modo SUMA el código **no** llegue al campo del WMS.
Requiere que el cambio de perfil en caliente funcione en tu terminal, y eso no
está garantizado cuando lo pide una app en segundo plano.

1. Deja el perfil de la Configuración A tal cual, y ponle nombre `WMS`.
2. Menú ⋮ → **Nuevo perfil** → nómbralo `SUMA`.
3. En `SUMA`: **no le asocies ninguna aplicación** (tiene que quedar libre, es
   requisito de la API que lo activa).
4. En `SUMA`: **Entrada de código de barras** activada, **Salida de pulsación
   de teclas DESACTIVADA**, **Salida de intents** igual que en A (misma acción,
   misma entrega `Emitir intent`).
5. En la app: marca *Cambiar de perfil al tocar el interruptor* y guarda.
6. **Pruébalo antes de dárselo a un operario:** toca el interruptor a SUMA,
   escanea, y confirma que el código no aparece en el campo del WMS. Después
   vuelve a WMS y confirma que sí aparece. Si el segundo paso falla, desmarca
   la casilla y quédate con la Configuración A.

### Tercero: el formato de los datos

Si tu DataWedge ofrece una opción de formato dentro de *Salida de intents*
(a veces **Formato de datos del intent**), elige la variante de **cadena de
texto**, no la de **matriz de bytes**.

Si solo tienes la de bytes, no importa: la app también lee
`com.symbol.datawedge.decode_data`, que es el formato en bytes.

### Verificar que funciona

En la pantalla de la app, sección **PRUEBA**: dispara la pistola.

| Lo que ves | Qué significa |
|---|---|
| Aparece el código y sale un peso | Todo correcto |
| No pasa absolutamente nada | El intent no llega. Revisa la acción y que la entrega sea *Emitir intent* |
| Aviso *"Intent recibido pero sin código. Extras: ..."* | El intent llega pero el código viene en otro extra. **Copia el nombre que sale en el aviso** y pégalo en el campo *Nombre del extra* |

La línea **Extras del último intent** en el estado de la app guarda esas claves
para consultarlas después con calma.

> La burbuja tiene que estar visible para recibir escaneos. Es el servicio de la
> burbuja el que escucha; si la ocultas, nadie recibe el intent.

### Copiar la configuración a otros terminales

DataWedge → menú ⋮ → **Importar/Exportar** → exportar el perfil. Genera un
archivo que puedes copiar al resto de los equipos e importar desde el mismo
menú. Ahorra repetir todo esto equipo por equipo.

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
| Aviso "Intent recibido pero sin código" | El extra tiene otro nombre: cópialo del aviso al campo *Nombre del extra* |
| Funcionaba y dejó de funcionar | La burbuja se ocultó, o el servicio murió: vuelve a abrir la app y toca *Mostrar burbuja* |
| "Sin peso en el código" | La etiqueta no trae `310n` y el recorte no calza |
| El botón dice *Copiar* | La accesibilidad no está activa (o la bloqueó el MDM) |
| El total entra mal en el WMS | Coma vs punto |

## Banco de pruebas

`index.html` abre en cualquier navegador y valida el parser contra la planilla
sin compilar nada. `node test.js` corre los mismos 11 casos que los tests de
Kotlin: **es la especificación**, si cambia una regla, cambia en los dos lados.
