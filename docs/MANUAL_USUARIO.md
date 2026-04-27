# Manual de Usuario — App de Agente (calls-agends)

**Versión del documento:** v1.0 — borrador inicial
**Última actualización:** 2026-04-26
**Audiencia:** product owner, supervisores y agentes nuevos.
**Idioma:** español (este documento es la guía oficial de uso).

> Este manual explica **qué hace la app**, **cómo se usa día a día**, y
> **por qué tomamos las decisiones de diseño** que llevaron al producto
> que estás viendo. Está pensado para que cualquier persona del equipo
> de operaciones pueda entender el flujo sin necesidad de mirar código.

> ⚠️ **Nota sobre el idioma de la UI:** la build actual del APK muestra
> los textos de pantalla en **inglés** ("Pending", "Recent", "Untouched",
> "Retry", "Dismiss client", etc.). En este manual usamos la versión
> en español de cada nombre porque es la traducción objetivo. La
> traducción real se aplicará en una pasada de i18n posterior con
> archivos `strings.xml` (`values-es/`). Hasta entonces el agente
> verá los nombres en inglés en la pantalla.

---

## Índice

1. [¿Qué es esta app?](#1-qué-es-esta-app)
2. [Cómo se distribuye e instala](#2-cómo-se-distribuye-e-instala)
3. [Primer ingreso del agente](#3-primer-ingreso-del-agente)
4. [Pantalla principal: Clientes](#4-pantalla-principal-clientes)
5. [El ciclo de una llamada](#5-el-ciclo-de-una-llamada)
6. [Resultados de llamada y qué significa cada uno](#6-resultados-de-llamada-y-qué-significa-cada-uno)
7. [Notas y seguimientos (follow-ups)](#7-notas-y-seguimientos-follow-ups)
8. [Llamadas entrantes y perdidas](#8-llamadas-entrantes-y-perdidas)
9. [Auto-llamado (auto-call)](#9-auto-llamado-auto-call)
10. [Sincronización con el servidor](#10-sincronización-con-el-servidor)
11. [Re-organización de Clientes (próxima versión)](#11-re-organización-de-clientes-próxima-versión)
12. [Descartar un cliente sin llamarlo](#12-descartar-un-cliente-sin-llamarlo)
13. [Reportes y estadísticas](#13-reportes-y-estadísticas)
14. [Preguntas frecuentes del owner](#14-preguntas-frecuentes-del-owner)

---

## 1. ¿Qué es esta app?

`calls-agends` es la **aplicación móvil del agente** del centro de
llamadas. Corre en tabletas Samsung Tab A9+ entregadas por la empresa
y reemplaza al marcador estándar del sistema operativo: cuando el
agente llama o recibe una llamada, lo hace a través de esta app.

Hay un solo tipo de usuario en la app: el **agente**. Los
supervisores, administradores y reportes globales viven en el panel
web (`calls-core`), no en el celular. Esta separación es deliberada:
el celular debe ser una herramienta enfocada en **una sola tarea**
(llamar al siguiente cliente bien y rápido) y nada más.

### 1.1 ¿Qué hace?

- Le muestra al agente la lista de clientes que tiene asignados.
- Le permite llamarlos desde la misma app, con marcador integrado.
- Después de cada llamada le pide marcar el resultado (interesado, no
  interesado, no contestó, etc.) y opcionalmente una nota.
- Si el resultado es "interesado", le obliga a agendar una
  re-llamada futura.
- Tiene una agenda con los seguimientos del día / semana.
- Sincroniza todo con el servidor en cuanto hay conexión.
- Funciona **sin internet** — todo se guarda local y se manda cuando
  vuelve la red.

### 1.2 ¿Qué no hace?

- **No permite agregar clientes nuevos.** Eso lo hace el admin desde
  el panel web subiendo un Excel.
- **No reasigna clientes entre agentes.** Eso lo hace el admin.
- **No muestra reportes globales** (ranking de agentes, totales del
  equipo). El admin los ve en el panel web.
- **No es un marcador genérico.** No sirve para llamar a cualquier
  número desde la lista de contactos del teléfono — solo a los
  clientes asignados.

> **Decisión clave:** todo lo que el agente NO necesita para hacer su
> trabajo de hoy se sacó de la app. Mientras menos pantallas y
> botones, menos chance de error en operación real.

---

## 2. Cómo se distribuye e instala

La app **no se publica en Google Play Store**. Se instala
directamente sobre las tabletas corporativas como un APK. Esta
decisión tiene tres motivos:

1. **Velocidad de iteración:** podemos sacar una nueva versión y
   actualizarla sobre todas las tabletas en horas, sin pasar por el
   proceso de review de Google.
2. **Funcionalidades sensibles:** la app pide ser el "marcador por
   defecto" del sistema, lo que requiere un permiso especial
   (`ROLE_DIALER`). Las apps publicadas en Play Store que piden esto
   pasan por una revisión adicional. Como las tabletas son
   corporativas, evitamos esa fricción.
3. **Control:** la empresa decide cuándo cada tableta recibe la
   actualización, sin que un agente pueda "no actualizar" desde Play
   Store.

> **Para el owner:** cuando saquemos una nueva versión, el equipo de
> IT empuja el APK a las tabletas. No requiere acción del agente.

---

## 3. Primer ingreso del agente

La primera vez que un agente abre la app pasa por tres pasos
obligatorios:

### 3.1 Login

Usa el correo y contraseña que el admin le dio
(`agente1@empresa.com / contraseña`). El servidor le devuelve un
token de autenticación que la app guarda de forma segura. Mientras
ese token sea válido, no tendrá que volver a entrar la contraseña.

### 3.2 Onboarding (compuerta de permisos)

Antes de ver clientes, el agente tiene que conceder **seis permisos**
y aceptar ser el **marcador por defecto**:

| Permiso | Para qué |
|---|---|
| Teléfono | Llamar y leer estado de llamada |
| Contestar llamadas | Aceptar entrantes desde la app |
| Estado de llamada | Detectar inicio/fin de cada llamada |
| Notificaciones | Avisar de seguimientos del día |
| Battery optimization off | Que el sistema no mate la app durante el día |
| Marcador por defecto | Que las llamadas pasen por nuestra app |

Si el agente le da "denegar para siempre" a alguno, la app le muestra
un botón "Abrir ajustes" que lo lleva directo a la sección correcta
del sistema operativo. **Sin estos permisos la app no avanza** —
porque sin ellos no puede hacer lo que tiene que hacer.

> **Decisión clave:** elegimos ser **estrictos, no flexibles**, en
> esta pantalla. Mejor que el agente pelee 30 segundos con permisos
> el primer día que descubrir a la mitad del mes que llamó a 200
> clientes y nada se registró.

### 3.3 Carga inicial

Apenas pasa el onboarding, la app baja del servidor:
- Sus clientes asignados.
- Sus seguimientos pendientes.

Y queda listo para trabajar. El agente entra directo al tab
**Clientes**.

---

## 4. Pantalla principal: Clientes

Es la pantalla que el agente ve la mayor parte del día. En la
versión actual muestra **una sola lista**: los clientes que tiene
asignados y aún no ha llamado (estado `PENDING`).

### 4.1 Estructura de la pantalla

```
┌─────────────────────────────────────────────┐
│  Buenas tardes, agente1                     │
│  113 clientes pendientes        [⟳ sincr.]  │
│                                             │
│  🔍 Buscar por nombre o teléfono…           │
│                                             │
│  Maria López                          [📞]  │
│  +507 6680-1776                             │
│  ─────────────────────────────────────────  │
│  Carlos Pérez                         [📞]  │
│  +507 6234-1100                             │
│  …                                          │
└─────────────────────────────────────────────┘
```

- **Saludo + contador**: arriba, en grande. El contador es el número
  de clientes pendientes y NO cambia cuando el agente busca
  (decisión deliberada — antes cambiaba y confundía).
- **Indicador de sincronización**: chip a la derecha del contador,
  con cuatro estados (ver § 10).
- **Búsqueda**: filtra por nombre o teléfono mientras se tipea.
- **Lista**: ordenada por `queueOrder` — el orden que el admin
  asignó. El agente debería trabajarla de arriba hacia abajo.

### 4.2 ¿Por qué solo PENDING en esta versión?

El MVP v1.0 prioriza **simpleza extrema**: una lista, un siguiente
cliente, un objetivo. Sabemos que esto deja afuera dos casos de uso
reales (recordar a quién llamé hoy, gestionar interesados a
mediano plazo). Esos los resolvemos en la siguiente iteración con
las tres vistas Pendientes / Recientes / Interesados (ver § 11).

---

## 5. El ciclo de una llamada

Cada cliente atraviesa un flujo de **tres pantallas**:

```
Clientes → [tap] → Pre-Call → [tap llamar] → In-Call → [colgar] → Post-Call → [guardar]
                     ↑                                                             │
                     └─────────────── siguiente cliente ───────────────────────────┘
```

### 5.1 Pre-Call — antes de llamar

El agente ve los datos del cliente:
- Nombre, teléfono, intentos previos, último resultado, última nota.
- Datos extra del Excel original (si los hay).
- Botón gigante **"Llamar"** abajo.
- Espacio para escribir notas durante la conversación.

> **Decisión clave:** poner toda la información valiosa en una sola
> pantalla, sin scroll, antes de que el agente apriete "Llamar".
> Una vez en llamada, el agente no debería cambiar de pantalla.

### 5.2 In-Call — durante la llamada

Cuando el agente toca "Llamar", la app inicia una llamada **real**
(no simulación) usando el módulo de Telecom de Android. La pantalla
muestra:
- Foto/avatar genérico + nombre + teléfono.
- Cronómetro de duración.
- Botones: silenciar, altavoz, teclado, **colgar**.

Si el cliente cuelga primero, la app lo detecta automáticamente y
pasa a Post-Call. Si el agente cuelga, igual.

> **Diseño no negociable:** la app NUNCA debe perder el contexto al
> volver de la llamada. Si el sistema mata la actividad durante un
> momento (por ejemplo cuando otra app interrumpe), al volver la app
> reabre Post-Call para el cliente correcto.

### 5.3 Post-Call — después de colgar

El agente ve **cinco botones grandes** correspondientes a los cinco
**resultados de llamada** (ver § 6). Toca uno, opcionalmente
escribe una nota, y toca **Guardar**.

Si marcó "INTERESADO", aparecen **selectores de fecha y hora
obligatorios inmediatamente debajo del botón** para agendar el
próximo seguimiento. Sin agendarlo no se puede guardar —
interesado sin seguimiento es una fuga de oportunidad.

---

## 6. Resultados de llamada y qué significa cada uno

| Botón | Qué pasa con el cliente | Vuelve a aparecer en Pendientes? |
|---|---|---|
| 🟢 **INTERESADO** | Se mueve a estado `INTERESTED`. Obliga a agendar follow-up. | No (queda como lead activo en Agenda) |
| 🔴 **NO INTERESADO** | Se mueve a estado `REJECTED`. Cliente "cerrado". | No |
| 🟡 **NO CONTESTÓ** | Se queda en `PENDING`. Va a la sub-sección "Para reintentar". | Sí, en la sub-sección de reintentos |
| 🟠 **OCUPADO** | Se queda en `PENDING`. Va a la sub-sección "Para reintentar". | Sí, en la sub-sección de reintentos |
| ⚪ **NÚMERO INVÁLIDO** | Se mueve a estado `INVALID_NUMBER`. | No |

> **Decisión clave:** con `NO_ANSWER` y `BUSY` el cliente sigue
> siendo PENDING pero pasa a la sub-sección "Para reintentar" de
> Pendientes. No se mezcla con los nunca llamados.
>
> Con `NOT_INTERESTED`, `INTERESTED` e `INVALID_NUMBER` el cliente
> sale de Pendientes completamente — la decisión final ya se tomó.

> **Pendiente para v2:** un 6to resultado para registrar **ventas
> cerradas** (cuando el cliente acepta el crédito). El backend
> ya tiene el valor `SOLD` reservado pero no lo expusimos en
> mobile todavía — discutimos primero dónde tiene más sentido
> ubicarlo (en Post-Call vs. en una acción separada del lead
> INTERESADO en Agenda) y cómo lo nombramos. Ver
> `docs/PHASE_2_BACKLOG.md § P2-05` para el contexto completo.

---

## 7. Notas y seguimientos (follow-ups)

### 7.1 Notas

El agente puede escribir notas en dos momentos:
1. **Durante la llamada** (en Pre-Call/In-Call) — para apuntar lo
   que está hablando.
2. **Después de la llamada** (en Post-Call) — para resumir.

Cada nota se guarda con un timestamp y queda asociada a:
- El cliente.
- La interacción (la llamada específica) — si vino de Post-Call.

> **Limitación actual (a resolver en la siguiente iteración):** una
> vez que el agente guarda Post-Call, no tiene forma de volver a
> agregar notas tardías al mismo cliente. Eso lo arregla la vista
> Recientes (ver § 11).

### 7.2 Seguimientos (follow-ups)

Solo se crean cuando el resultado es **INTERESADO**. Son una
"promesa de re-llamar" en una fecha y hora específicas.

- Aparecen en el tab **Agenda** de la app.
- El día/hora del follow-up, la app le notifica al agente.
- Cuando el agente abre el follow-up y llama, completa el ciclo y
  el follow-up se marca como **completado**.

---

## 8. Llamadas entrantes y perdidas

Como la app es el marcador por defecto, **todas las llamadas
entrantes** del SIM corporativo pasan por ella, no por el marcador
nativo de Samsung.

### 8.1 Llamada entrante mientras el agente está en la app

Aparece la pantalla de "Llamada entrante" con:
- Nombre del cliente (si el número está en su lista de asignados).
- O simplemente el número (si no es un cliente asignado).
- Botones grandes: Aceptar / Rechazar.

Si la acepta, entra al flujo In-Call → Post-Call normalmente. La
interacción se registra como **INBOUND** en lugar de OUTBOUND, lo
que permite al admin distinguir en reportes quién contestó vs quién
fue contactado.

### 8.2 Llamadas perdidas

Si el agente no contestó (estaba en otra llamada, la tableta estaba
guardada, etc.), la llamada queda registrada como una **llamada
perdida** con timestamp y número.

- Se muestra en una sección de "Llamadas perdidas" en la cabecera
  del tab Clientes hasta que el agente la "reconoce" (la marca como
  vista).
- **NO se sincroniza con el servidor** — es solo un registro local
  para que el agente sepa a quién volver a llamar.

> **Decisión:** las llamadas perdidas son ruido de operación, no
> dato del negocio. No las subimos para no contaminar las
> estadísticas con cosas que no son llamadas reales.

---

## 9. Auto-llamado (auto-call)

Es una función que automatiza llamadas en serie sobre la lista de
Pendientes. El agente toca "Iniciar auto-llamado" en la cabecera y
la app comienza:

1. Llama al primer cliente.
2. Cuando termina, lleva al agente a Post-Call.
3. Después de guardar, espera 5 segundos (cuenta regresiva visible).
4. Llama automáticamente al siguiente.

Esto sigue **hasta que el agente marca INTERESADO** o cancela
manualmente la sesión.

> **Decisión clave:** solo INTERESADO detiene la cuenta regresiva.
> Para los otros resultados (no contestó, ocupado, no interesado,
> número inválido) la sesión sigue automáticamente al siguiente. La
> idea es maximizar la cantidad de llamadas conectadas por jornada
> sin que el agente tenga que pelear con la pantalla.

Cuando hay un INTERESADO, la sesión se "pausa" en Post-Call para
que el agente agende el follow-up con calma. Después puede reanudar
o terminar la sesión.

Al final, la app muestra un **resumen de sesión**: cuántos
contactados, cuántos interesados, cuántos no contestaron, etc.

---

## 10. Sincronización con el servidor

Toda la información del agente vive **dos veces**: en su tableta
(Room/SQLite) y en el servidor (MongoDB). El reto es mantenerlas
alineadas a pesar de la mala red.

### 10.1 Cuándo se sincroniza

| Evento | Qué se sincroniza |
|---|---|
| Login | Baja: clientes asignados + agenda |
| Después de cada Post-Call | Sube: la interacción + nota + follow-up. Baja: estado actualizado |
| Cada 20 minutos (background) | Empuja todo lo pendiente + baja cambios del server |
| Cuando vuelve la conexión | Lo mismo que la periódica |
| Cuando el agente toca el chip de sincronización manualmente | Lo mismo |

### 10.2 Estados del indicador de sincronización

El chip arriba a la derecha del tab Clientes muestra cuatro estados:

| Color | Estado | Significa |
|---|---|---|
| 🟢 Verde | **Sincronizado** | Todo subido, todo al día |
| 🔵 Azul (girando) | **Sincronizando** | En este momento subiendo/bajando |
| 🟡 Amarillo | **Pendiente** (con número) | Hay X cosas sin subir todavía |
| 🔴 Rojo | **Error** | La última sync falló — el agente puede tocar para reintentar |

### 10.3 Modo offline

La app funciona **completamente offline**. Si el agente entra a una
zona sin red:
- Sigue viendo a sus clientes.
- Sigue pudiendo llamar.
- Sigue guardando resultados y notas.
- Las cosas se acumulan en "pendiente de sincronizar" (el indicador
  amarillo se enciende).
- Cuando vuelve la red, se sube todo.

> **Decisión clave:** **nunca** bloqueamos al agente por falta de
> red. La data se guarda local primero y se sube después. Esto
> protege contra zonas muertas, datos agotados, o servidores
> caídos.

### 10.4 Bug conocido (a resolver) — KI-06

> Hay un bug detectado: cuando el agente marca "NO INTERESADO", el
> contador de pendientes baja, pero unos segundos después
> (cuando llega la sincronización del servidor) el cliente
> reaparece y el contador vuelve a subir. El cliente queda
> "atrapado" en pendiente.
>
> Causa raíz identificada (servidor): cuando el servidor recibe una
> interacción que ya tenía registrada (por reintento), salta la
> actualización de status del cliente. El fix está identificado y
> está priorizado para entrar antes del rediseño de la pantalla
> Clientes.

---

## 11. Re-organización de Clientes y Agenda

> Esta sección reemplaza el diseño anterior de "tres pestañas dentro
> de Clientes" (Pendientes / Recientes / Interesados). Después de
> probarlo, decidimos eliminar Interesados como pestaña suelta —
> los leads interesados viven en la **Agenda**, que es el espacio
> mental natural del agente para "qué tengo que hacer y con quién
> estoy trabajando".

### 11.1 Estructura final

**Tab Clientes** — dos vistas:

```
[ Pendientes 113 ]   [ Recientes 8 ]
```

**Tab Agenda** — secciones (en orden):

```
HOY            (5)
MAÑANA         (8)
ESTA SEMANA    (12)
DESPUÉS        (3)
SIN AGENDAR    (2)   ← novedad
```

### 11.2 Pendientes — dos sub-secciones

La pestaña Pendientes ahora se divide en dos grupos:

```
PENDIENTES (113)

  SIN LLAMAR (95)
  ─────────────────
  Maria López          ▸
  Carlos Pérez         ▸
  ...

  PARA REINTENTAR (18)
  ────────────────────
  Pedro Gómez   · ocupado · hace 2h
  Lucia Mora    · sin respuesta · hace 4h
  ...
```

**Sin llamar:** clientes que aún no se han marcado nunca. Es la cola
fría. Ordenados por `queueOrder` (la posición que asignó el admin).
Cada tarjeta tiene un menú `⋯` con la acción "Descartar cliente".

**Para reintentar:** clientes que ya se llamaron una o más veces y el
resultado fue **NO CONTESTÓ** u **OCUPADO** — es decir, la
conversación nunca llegó a darse. Ordenados por la **fecha de la
última llamada más antigua arriba** (los más "listos" para reintentar
suben primero). Muestran:
- Cantidad de intentos.
- Resultado de la última llamada (badge "Ocupado" / "No contestó").
- Hace cuánto fue la última llamada.

> **¿Por qué dos secciones?** Antes se mezclaban en una sola lista y
> el agente no podía distinguir a primera vista entre quién aún no
> había intentado y quién ya había llamado. Con la separación queda
> claro: la cola fría arriba, los reintentos abajo.

> **Cuándo sale un cliente de "Para reintentar":** cuando se le
> vuelve a llamar y se marca un resultado decisivo (INTERESADO,
> NO INTERESADO, NÚMERO INVÁLIDO, VENDIDO) o se descarta. Mientras
> sigan saliendo NO_ANSWER/BUSY, se quedan en esa sección.

> **Auto-llamado:** la cola del modo auto-llamado primero pasa por
> "Sin llamar" y, cuando se acaba, sigue con "Para reintentar".

### 11.3 Recientes (24 h)

Muestra **todo lo que el agente tocó en las últimas 24 horas**:

- **Llamadas** de cualquier resultado: NO_ANSWER, BUSY, INTERESADO,
  NO INTERESADO, NÚMERO INVÁLIDO.
- **Descartes** (ver § 12) — con un botón **"Deshacer descarte"**.

Sirve como:
- Recall del día para agregar una nota tardía (entrando al detalle).
- Ventana de recuperación para deshacer un descarte equivocado.

> **Sin botones en la lista.** La tarjeta abre el detalle al tocarla;
> las acciones (agregar nota, volver a llamar) viven dentro del
> detalle. Eso mantiene la lista compacta.

> El cliente desaparece solo: a las 24 h se sale, sin intervención.

### 11.4 Resultado "Vendido" — pendiente de definir

Pensábamos agregar un 6to botón "Sold" en Post-Call para registrar
ventas cerradas en la misma llamada. Lo armamos, lo probamos, y
quedó claro que el lugar y el nombre necesitan una decisión de
producto antes de exponerlo:

- **Lugar:** ¿el agente cierra la venta en la misma llamada que
  marcó INTERESADO? En la práctica los créditos suelen cerrarse
  después de varios contactos. Quizás el lugar correcto es una
  acción "Cerrar venta" sobre el lead INTERESADO en Agenda, no
  un 6to botón en Post-Call.
- **Nombre:** "Sold" / "Cerrado" / "Convertido" / "Aprobado"
  significan cosas distintas en distintos productos crediticios.
- **Metadata:** ¿al cerrar venta queremos capturar monto, plazo,
  número de contrato? ¿O solo el outcome?

Mientras tanto, el backend ya tiene el valor `SOLD` reservado y
deployado, pero el celular **no lo expone**. Cuando quieras
priorizarlo, ver el contexto completo en
`docs/PHASE_2_BACKLOG.md § P2-05`.

### 11.5 Agenda — Próximas + Sin agendar

La Agenda ya existía con las secciones de tiempo (Hoy, Mañana, Esta
semana, Después). Ahora también tiene una sección nueva:

#### Sin agendar

Captura los **clientes INTERESADOS sin un seguimiento agendado**.
Tres situaciones reales lo alimentan:

1. **Admin reasignó un INTERESADO** del agente B al agente A. Los
   seguimientos del agente B se cancelaron automáticamente. Sin
   esta sección, el lead llegaría al agente A invisible.
2. **El agente completó un seguimiento** pero olvidó / no pudo
   agendar el siguiente. El lead sigue siendo INTERESADO pero no
   tiene fecha futura.
3. **Un seguimiento venció** sin que el agente lo marcara como
   completado.

**Orden:** los más viejos arriba (los que tienen más tiempo
asignados son los más urgentes — están más cerca de enfriarse).

Cada fila tiene un menú `⋯` con la acción **"Descartar cliente"**
(ver § 12) — útil cuando el agente decide cerrar el lead sin
re-llamar.

> **Importante:** la Agenda es la lente "qué tengo que hacer / con
> quién estoy trabajando". Pendientes es el cold queue. Recientes
> es la ventana de las últimas 24 h. Cada vista responde una
> pregunta distinta del agente.

### 11.6 Por qué removimos la pestaña Interesados

La idea original era una tercera pestaña "Interesados" mostrando
todos los leads tibios. La probamos y vimos que **solapaba ~95% con
la Agenda** (un INTERESADO con seguimiento ya está en Agenda → Hoy
/ Mañana / etc.). El 5% restante son los huérfanos — esos ahora
viven en "Sin agendar".

Resultado: una pestaña menos, datos sin duplicar, y la Agenda gana
identidad como el centro de control del pipeline.

### 11.7 Re-asignación por el admin

Cuando el admin reasigna un cliente al agente desde el panel web:

| Tipo de re-asignación | Dónde aparece en el celular |
|---|---|
| Cliente nuevo (PENDING) | En **Pendientes** apenas se sincroniza |
| Cliente que era INTERESADO con otro agente | En **Agenda** (Próximas o Sin agendar según tenga seguimiento o no) |
| Cliente RECHAZADO / DESCARTADO / NÚMERO INVÁLIDO / CONVERTIDO | El servidor lo **resetea automáticamente a PENDING** al re-asignar. Aparece en Pendientes |

> El reset automático es nuevo. Si en el futuro queremos re-asignar
> sin resetear el estado (caso raro), el panel tendría un checkbox
> "Mantener estado". Por defecto: reset.

### 11.8 ¿Cuándo entra esto a producción?

| Versión | Qué incluye |
|---|---|
| **v1.0 (ya en producción)** | Pendientes (Untouched + Retry) + Recientes (calls + descartes) + Agenda con Sin agendar |
| **v1.1** | Pantalla "Detalle de cliente" (solo lectura, historial completo de notas e interactions) |
| **v1.2** | Re-agendar follow-up desde la card de Agenda |

---

## 12. Descartar un cliente sin llamarlo

Esta sección documenta una funcionalidad **nueva**, decidida después
de la revisión inicial del producto. Resuelve un caso real que hoy
no tiene salida limpia: cuando el agente mira los datos de un
cliente y decide que **no vale la pena llamarlo**, o que un
INTERESADO existente ya no debe seguir trabajándose, sin tener que
falsear el resultado de una llamada que no ocurrió.

### 12.1 ¿Qué es "descartar"?

Es una **acción del agente, no un resultado de llamada**. La hace
con un toque desde:

- La tarjeta del cliente en **Pendientes** (menú `⋯` → "Descartar
  cliente").
- La pantalla **Pre-Call**, antes de marcar (botón secundario
  abajo).
- La tarjeta del cliente en **Interesados** (menú `⋯` → "Descartar
  cliente"), cuando un lead se enfrió y se decide cerrarlo.

> No está disponible en Post-Call. Post-Call es para cerrar una
> llamada que sí ocurrió; los cinco resultados existentes cubren
> ese caso.

### 12.2 Qué pasa al descartar

| Cosa | Efecto inmediato |
|---|---|
| Estado del cliente | Pasa a `DESCARTADO` (`DISMISSED`) |
| Lista Pendientes | Desaparece |
| Lista Interesados | Desaparece (si era INTERESADO) |
| Lista Recientes | Aparece con badge **descartado** durante 24 h |
| Follow-up agendado (si lo había) | Se cancela automáticamente |
| Notificación local del follow-up | Se cancela |
| Servidor | Se sincroniza el evento — el admin lo verá |

### 12.3 Sheet de confirmación (lo que ve el agente)

```
┌─────────────────────────────────────────────┐
│  Descartar a Maria López                    │
│                                             │
│  Este cliente saldrá de tu lista de         │
│  llamadas. Lo verás en Recientes por 24 h   │
│  por si quieres deshacer.                   │
│                                             │
│  Razón (opcional)                           │
│  [Corporativo] [Errado] [Duplicado]         │
│  [Opt-out]    [No aplica]    [Otro…]        │
│                                             │
│  ┌──────────────────────────────────────┐   │
│  │ (nota libre opcional, hasta 200 ch.) │   │
│  └──────────────────────────────────────┘   │
│                                             │
│       [ Cancelar ]    [ Descartar ]         │
└─────────────────────────────────────────────┘
```

- Seis razones predefinidas — el agente toca una y listo.
- "Otro…" abre el campo de texto libre.
- También puede combinar una razón predefinida con un texto libre
  para más contexto.
- **No es obligatorio** dar razón. Hacerlo obligatorio agrega
  fricción que termina con agentes escribiendo "x" para pasar.

#### Razones predefinidas

| Etiqueta | Cuándo usarla |
|---|---|
| **Corporativo** | El número es de central / 0800 — no responde una persona |
| **Datos errados** | Nombre o teléfono claramente incorrectos |
| **Duplicado** | Ese cliente ya está en otra fila de la lista |
| **Opt-out** | El cliente pidió fuera-de-canal no ser contactado |
| **No aplica** | El producto no aplica para este perfil |
| **Otro…** | Escribe libremente |

> Las razones son configurables — si más adelante quieres agregar o
> quitar algunas, se ajustan en una próxima versión sin necesidad
> de tocar el servidor.

### 12.4 Reversibilidad — 24 horas para deshacer

Durante 24 h después del descarte, el cliente aparece en
**Recientes** con un badge distinto:

```
┌─────────────────────────────────────────────────────┐
│  ⨯  Maria López                       hace 12 min  │
│     DESCARTADO  · "número corporativo"              │
│     [↩ Deshacer descarte]                           │
└─────────────────────────────────────────────────────┘
```

- Tarjeta gris-oscura (distinta de las tarjetas de outcome de
  llamada).
- Razón del descarte visible.
- Una sola acción: **Deshacer descarte**.

Cuando el agente toca "Deshacer", el cliente vuelve a su estado
anterior:
- Si era PENDING, vuelve a Pendientes.
- Si era INTERESADO, vuelve a Interesados, y se reactiva el
  follow-up cancelado (siempre que la fecha del follow-up no haya
  pasado ya).

Pasadas las 24 h, la tarjeta de descarte desaparece de Recientes y
**el agente ya no puede deshacer por su cuenta**. Si quiere
recuperar al cliente, tendrá que pedírselo al admin.

### 12.5 Qué ve el admin en el panel web

El admin tiene acceso completo al historial de descartes:

- En la lista de clientes: filtro `Estado = DESCARTADO`, con
  columnas "Descartado por" / "Cuándo" / "Razón".
- En el detalle del cliente: timeline completo — descartes,
  reactivaciones, reasignaciones, todo con timestamp y autor.

Esto le permite al admin:
- Detectar agentes que descartan demasiado (mala calidad de
  criterio).
- Detectar problemas en la base de datos de origen (todos
  descartando "datos errados" → el Excel del upload está sucio).
- Auditar, en caso de queja del cliente, quién y cuándo lo sacó de
  la lista activa.

### 12.6 Poderes del admin (sin ventana de tiempo)

A diferencia del agente, el admin **no está limitado a las 24 h**.
Puede hacer dos acciones en cualquier momento:

#### a) Reactivar al mismo agente

Devuelve el cliente al estado anterior (PENDING o INTERESADO),
asignado al **mismo agente** que lo descartó. Útil para corregir
errores del agente o cuando hay nueva información que justifica
intentarlo de nuevo.

#### b) Reasignar a otro agente

Cambia el dueño del cliente y lo regresa a PENDING en la lista del
**nuevo agente**. Útil para repartir un cliente que un agente
descartó por desinterés/cansancio pero que la operación cree que
sigue siendo viable.

> En ambos casos, el evento queda en el historial del cliente — la
> reactivación o reasignación NO borra el descarte previo, solo lo
> "supera". El admin siempre puede ver cuántas veces este cliente
> ha sido descartado y por quiénes.

### 12.7 Casos especiales

| Caso | Comportamiento |
|---|---|
| Agente descarta sin red, sin reconectar en 24 h | Local: cliente fuera de Pendientes, en Recientes. Pasadas las 24 h sale de Recientes localmente. Cuando vuelve la red, el evento se sube y el admin lo ve. |
| Admin reactiva mientras el agente todavía está sin red | Cuando el agente reconecta, su descarte se sincroniza pero el server ya tiene una reactivación más nueva — el descarte queda registrado en historial pero el cliente sigue activo. La sincronización siguiente del agente trae al cliente de vuelta. |
| Agente descarta dos veces el mismo cliente | El sistema lo detecta y lo trata como una sola acción (idempotente). |
| Admin reasigna a otro agente justo después del descarte | Dos eventos en el historial. El cliente termina en la lista del nuevo agente. |

### 12.8 Decisiones de diseño documentadas

**¿Por qué un nuevo estado `DESCARTADO` en lugar de reusar
`NO_INTERESADO`?**
Porque "no interesado" implica que la conversación ocurrió. El
descarte es una decisión sin llamada (o con llamada vieja, en el
caso de Interesados). Mezclar los dos contamina las estadísticas.

**¿Por qué una ventana de 24 h y no inmediato/permanente?**
- Inmediato (sin deshacer) sería peligroso — un toque equivocado
  pierde un cliente.
- Permanente reversible sería confuso — el agente nunca sabría si
  algo está realmente "fuera".
- 24 h cubre el caso real ("me arrepentí en el día") sin convertir
  a Recientes en una lista interminable.

**¿Por qué el admin puede sin límite y el agente no?**
Por jerarquía de autoridad. El agente tiene poder operativo del
día (descartar lo que claramente no aplica). El admin tiene poder
de gestión (corregir errores históricos, redistribuir). La ventana
del agente lo protege de sus propios errores; la libertad del
admin permite corregirlos cuando se descubren tarde.

---

## 13. Reportes y estadísticas

### 12.1 Lo que NO está en la app móvil

Decisión deliberada: **el agente no ve estadísticas globales en la
tableta**. Esto incluye:
- Ranking entre agentes.
- Total de llamadas del equipo.
- Conversiones del equipo.
- Métricas de tiempo promedio en llamada.

Todo eso vive en el panel web del admin.

### 12.2 Lo que sí veremos en v1.1 (mobile)

Vista de "Mi actividad" — solo del agente que está logueado:
- Llamadas hechas hoy / esta semana.
- Distribución de resultados (pie chart simple).
- Cantidad de interesados conseguidos.
- Tiempo total en llamada.

> **Decisión:** que el agente vea SU desempeño es motivador (gamification
> sutil). Que vea el desempeño de OTROS agentes en la misma pantalla
> donde está intentando trabajar es distractor. La comparación entre
> agentes la hace el supervisor con datos del panel web.

---

## 14. Preguntas frecuentes del owner

**P: ¿Cómo registra el agente una venta cerrada?**
R: Por ahora **no hay flujo dedicado** para registrar la venta
desde el celular — la decisión de dónde y cómo poner ese botón
quedó pendiente para v2 (ver § 11.4 y `PHASE_2_BACKLOG.md`).
Hasta entonces, el admin marca la venta cerrada desde el panel
web cuando confirma con el área de crédito.

**P: ¿Si un agente acumula muchos "Sin agendar", el sistema avisa?**
R: Por ahora no — la sección crece sin límite. Si vemos que en
operación real se acumulan demasiados (señal de mala disciplina del
agente o de admin que no completa follow-ups), sumamos un warning
en la cabecera de la sección y un reporte para el admin. Está
documentado para v2 — ver §13.x del backlog interno.

**P: ¿Qué pasa si un agente pierde la tableta?**
R: La data del cliente está en el servidor. La tableta se reemplaza,
el agente vuelve a hacer login y carga su estado actual. Nada se
pierde excepto las llamadas perdidas locales (que no son críticas).

**P: ¿Y si la tableta se rompe a la mitad del día con cosas sin
sincronizar?**
R: Eso sí se pierde. La ventana de exposición es entre 0 y 20 min
(la sincronización periódica). Después de cada llamada también
intentamos sincronizar inmediatamente, así que en la práctica la
ventana es de minutos, no de horas. Para minimizarlo, recomendamos
asegurar que las tabletas estén con red estable durante operación.

**P: ¿El agente puede ver los datos de otro agente?**
R: No. El servidor solo le devuelve los clientes asignados a él. No
hay forma desde la app de listar clientes de otro agente.

**P: ¿Y si el admin asigna 5,000 clientes a un agente?**
R: Funciona — los probamos con miles de filas. La pantalla pagina
visualmente (lista virtual), no carga todo en pantalla a la vez. El
único costo es el tamaño del primer download.

**P: ¿El agente puede llamar a un número que no esté en su lista?**
R: No desde la app. La app es un marcador "asistido" para los
clientes asignados. Si necesita marcar otro número, el sistema
operativo permite usar otra app (aunque al ser nosotros marcador por
defecto, esto requiere unos pasos extra — eso es intencional).

**P: ¿Qué pasa si el agente rechaza el permiso de marcador por
defecto?**
R: La app no avanza más allá del onboarding. Sin ese permiso no
podemos interceptar las llamadas y la app pierde su razón de ser.
Por eso es bloqueante.

**P: ¿Cuántos agentes soporta el MVP?**
R: El plan inicial es **4 agentes** simultáneos. La arquitectura
soporta crecer a docenas sin cambios — el cuello de botella sería
el panel web del admin si llegáramos a cientos.

**P: ¿En qué se diferencia esta app del marcador estándar de
Android?**
R: El marcador de Android es genérico — sirve para llamar a
cualquiera. Esta app está diseñada **alrededor del cliente** como
unidad de trabajo: cada llamada es parte de un flujo (Pre-Call → In
Call → Post-Call) con datos, notas y follow-up amarrados al cliente.
El marcador estándar no entiende el concepto de "cliente
asignado" ni "interesado".

**P: ¿Qué tan offline-tolerante es?**
R: Totalmente. Un agente puede pasar un día completo sin red y la
app sigue funcionando. La única cosa que NO funciona offline es el
login inicial (necesita validar con el servidor) y la primera carga
de clientes. Una vez logueado, hasta que cierre sesión, todo es
local-first.

---

## 15. Cierre

Este manual es un documento vivo. Cada vez que tomemos una decisión
de producto que afecte cómo el agente usa la app, la documentamos
acá en español, y separadamente queda en los documentos técnicos
(en inglés) que conserva el equipo de desarrollo.

El criterio para cualquier nueva funcionalidad propuesta es siempre
el mismo:

> **¿El agente, en medio de su jornada de llamadas, va a usar esto
> sin tener que pensarlo?**

Si la respuesta es no, la rediseñamos o la dejamos para el panel
web. Esa disciplina es la que mantiene la app rápida y enfocada.

---

## Anexo: glosario rápido

| Término | Significado |
|---|---|
| Agente | Persona que llama a clientes desde la app |
| Admin | Persona que asigna clientes y revisa reportes desde el panel web |
| Cliente | Persona física a llamar, cargada desde Excel |
| PENDING | Cliente asignado aún no llamado / a reintentar |
| INTERESTED | Cliente que dijo "sí, sigamos hablando" |
| REJECTED | Cliente que dijo "no, no me interesa" |
| INVALID_NUMBER | Cliente cuyo número no funciona |
| Interaction | Una llamada específica registrada en el sistema |
| Note | Texto libre que el agente escribe sobre un cliente |
| Follow-up | Compromiso de re-llamar a un INTERESED en una fecha futura |
| Sync / Sincronización | Mover data entre la tableta y el servidor |
| OUTBOUND | Llamada saliente (la hizo el agente) |
| INBOUND | Llamada entrante (la recibió el agente) |
| Auto-call | Modo automático de llamar en serie sobre la lista |
| Pre-Call / In-Call / Post-Call | Las tres pantallas del ciclo de una llamada |
| DISMISSED / Descartado | Cliente que el agente sacó de su lista activa sin necesariamente haberlo llamado |
| Dismissal | Evento de descarte — registro auditable de quién/cuándo/por qué |
| Reactivar | Acción del admin para devolver un cliente descartado a la lista activa, al mismo agente |
| Reasignar (post-descarte) | Acción del admin para mover un cliente descartado a otro agente |
| Deshacer descarte | Acción del agente disponible solo en las primeras 24 h después del descarte |
| CONVERTED | Estado del cliente cuando se cerró la venta. Hoy lo marca solo el admin desde el panel web (ver § 11.4) |
| Sin agendar | Sección de la Agenda con clientes INTERESADOS sin un seguimiento futuro programado |
| Untouched / Retry | Las dos sub-secciones dentro de Pendientes — nunca llamados vs. ya intentados |
