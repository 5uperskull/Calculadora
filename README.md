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

### Configuración: un solo perfil

**Borra o desactiva el perfil `SUMA`.** No sirve, y ahora explico por qué.

DataWedge elige el perfil según la app que está en primer plano. Un perfil sin
aplicación asociada **nunca se activa solo** — por eso, dejando activo solo
`SUMA`, el escáner no responde dentro del WMS. Y `SWITCH_TO_PROFILE`, la API
que cambia de perfil, está pensada para que la llame la app que está al frente;
la nuestra vive detrás del WMS, así que DataWedge revierte al perfil del WMS.

Con los dos perfiles activos el que manda es el del WMS, que tiene la salida de
teclado encendida: de ahí que el código llegue a los dos sitios.

La app no cambia de perfil. **Le apaga y le enciende la salida de teclado al
perfil del WMS**, que es la opción que decide si el código se escribe en el
campo. Eso sí funciona desde segundo plano, porque no toca cuál perfil está
activo, solo reescribe una opción del que ya lo está.

Configura un único perfil, el asociado a la app del WMS:

1. **Perfil habilitado** → activado.
2. **Entrada de código de barras** → activada.
3. **Salida de pulsación de teclas** → **activada**. La app la apagará y
   encenderá según el modo; este es su estado de reposo.
4. **Salida de intents** → **activada, y se queda activada siempre**:
   - **Acción del intent**: `cl.icestar.pesototal.SCAN`
   - **Categoría del intent**: vacía
   - **Entrega del intent**: **Emitir intent**

En la app:

1. Campo *Nombre del perfil asociado al WMS*: escribe el nombre **exacto** del
   perfil, tal como aparece en la lista de DataWedge. Si no calza, el corte no
   hace nada.
2. Marca **En modo SUMA, cortar la salida de teclado del lector**.
3. **Guardar y reiniciar burbuja**.

Ahora el operario puede dejar el cursor dentro del textbox del WMS: en modo
**SUMA** el código no se escribe ahí y solo alimenta la suma; en **WMS** vuelve
a escribirse como siempre.

### Si el terminal no acepta el corte

DataWedge no contesta si aceptó el cambio, así que la app no puede confirmarlo.
Lo ves al primer escaneo: si en modo SUMA el código igual se escribe en el
campo, ese terminal no acepta `SET_CONFIG`. Desmarca la casilla y trabaja con
las dos salidas encendidas — se suma bien, pero hay que limpiar el campo del
WMS a mano.

Dos protecciones para que el corte no deje a nadie tirado:

- Al arrancar, la app **siempre** vuelve al modo WMS y enciende el teclado.
- Al ocultar la burbuja o al morir el servicio, restaura el teclado.

Así, si el proceso muere en modo SUMA, el WMS no queda sin poder escanear.

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
3. Escanea las etiquetas. La burbuja va marcando `34,7 kg · 3` y **vibra** en
   cada lectura: una vibración corta confirma, dos seguidas avisan de etiqueta
   repetida. Además **lo dice en voz alta**: *"Duplicado"* si la etiqueta ya se
   había leído, *"Sin peso"* si el código no trae peso.
4. Toca la burbuja para ver el detalle, deshacer o borrar una línea.
5. Enfoca el campo del WMS y **toca el chip SUMA para apagarlo**: eso inserta
   el total. También sirve el botón **Insertar** del panel.
   Sin accesibilidad el botón dice **Copiar**: mantén pulsado el campo y pega.
6. La suma se reinicia sola. **Deshacer** la recupera si te adelantaste.

La burbuja se arrastra a donde estorbe menos y se imanta al borde.

### Señales de la burbuja

| Señal | Qué significa |
|---|---|
| Borde azul | Todo normal |
| **Borde amarillo y total en amarillo** | La última etiqueta ya se había leído. Se suma igual: si son dos cajas iguales está bien, si fue un doble disparo usa **Deshacer** |
| Chip **SUMA** encendido | Los escaneos alimentan la suma |
| Chip **WMS** apagado | Los escaneos van al WMS |

Salir de **SUMA** con etiquetas acumuladas **inserta el total**. Si la inserción
falla porque no hay ningún campo enfocado, el modo se queda en SUMA: nadie
pierde la cuenta por un descuido.

### Apagar la burbuja

En el panel, botón **Salir**. Pide **dos toques** — el primero lo pone en rojo
preguntando *¿Seguro?* y se desarma solo a los 3 segundos. Apagar la burbuja en
mitad de un turno sería caro, y con guantes se toca lo que no es.

Para volver a encenderla: abre **Peso Total** y toca *Mostrar burbuja*.

## 5. Calibración

- **Recorte de respaldo (desde / largo):** solo entra si el código no trae el
  `310n`. Si un peso no calza con la planilla, ajústalo aquí. Por defecto
  *desde 13, largo 7*.
- **Separador decimal:** coma por defecto. Si el WMS rechaza el valor, cámbialo
  a punto.
- **Opacidad y modo barra de borde:** para que estorbe menos.
- **Aviso hablado:** encendido por defecto. Dice *"Duplicado"* y *"Sin peso"*
  por el canal de notificaciones. Si el terminal no trae motor de voz o le falta
  el español, cae a un pitido — compruébalo con **Probar la voz** antes de
  desplegar, y mira la línea *Voz disponible* del estado.
- **Cortar la salida de teclado en modo SUMA:** apagado por defecto. Enciéndelo
  cuando el operario necesite escanear con el cursor dentro del textbox del WMS
  sin que el código se escriba ahí. Requiere que el *Nombre del perfil asociado
  al WMS* sea exacto. Ver la sección 3.

## 6. Si algo falla

| Síntoma | Causa más probable |
|---|---|
| La burbuja no aparece | Falta el permiso de superposición, o el servicio se detuvo |
| Escaneo sin efecto | El intent no está configurado, o el chip está en **WMS** |
| En SUMA el código igual se escribe en el WMS | El nombre del perfil no calza, o el terminal no acepta `SET_CONFIG` |
| Aviso "Intent recibido pero sin código" | El extra tiene otro nombre: cópialo del aviso al campo *Nombre del extra* |
| Funcionaba y dejó de funcionar | La burbuja se ocultó, o el servicio murió: vuelve a abrir la app y toca *Mostrar burbuja* |
| "Sin peso en el código" | La etiqueta no trae `310n` y el recorte no calza |
| El botón dice *Copiar* | La accesibilidad no está activa (o la bloqueó el MDM) |
| El total entra mal en el WMS | Coma vs punto |
| No habla, solo pita | El terminal no tiene motor de voz o le falta el español. Instala Google TTS y su voz en español, o quédate con el pitido |
| No se oye nada | Volumen de notificaciones al mínimo, o el aviso hablado está desmarcado |

## Banco de pruebas

`index.html` abre en cualquier navegador y valida el parser contra la planilla
sin compilar nada. `node test.js` corre los mismos 11 casos que los tests de
Kotlin: **es la especificación**, si cambia una regla, cambia en los dos lados.
