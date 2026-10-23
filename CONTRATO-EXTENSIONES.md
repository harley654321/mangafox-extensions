# CONTRATO DE EXTENSIONES MANGAFOX (.mfx, api v1 + v2)

La APK es un **kernel estable**: no contiene fuentes ni sabe de
sitios. Cada fuente es un **plugin `.mfx`** (ZIP con bytecode
Kotlin) firmado con **ECDSA P-256**; la APK verifica firma +
SHA-256 antes de activarlo y lo rechaza si el `apiVersion` del
manifest no habla un contrato que el kernel conozca (rechazo
explícito con log, jamás `NoSuchMethodError` silencioso).

## Versiones del contrato (coexisten)

| apiVersion | Superficie                | Estado      |
|------------|---------------------------|-------------|
| 1          | `FuenteMfx`               | CONGELADA   |
| 2          | `FuenteMfx2` (hereda v1)  | ACTIVA      |

El kernel carga v1 y v2 en classloaders independientes. Una
fuente v2 es una v1 completa más tres capacidades.

## v1: FuenteMfx (congelada)

- `FuenteMfx : TemaFuente` + `val id`. La fuente implementa
  home (recientes/populares/ranking/secciones), directorio y
  géneros, búsqueda, ficha y lector de páginas.
- `BasePluginMfx.activar(contexto)` es el punto de entrada que
  el manifest nombra en `pluginClassName`.
- `ContextoMfx` regala `clienteWeb(baseUrl, ritmoMs,
  recoveryUrl)` (OkHttp con cortesía por host, cookies
  persistentes, interceptor Cloudflare y escotilla WebView del
  kernel) y `log()` (ley de registro total).

## v2: FuenteMfx2 (Modulo D del kernel)

Todo lo de v1, más:

1. `filtros(): List<Filtro>` — filtros tipados que la UI
   genérica pinta: `Filtro.Texto`, `Seleccion`, `Casilla`,
   `TriEstado` (incluido/excluido/ignorado) y `Orden`.
2. `buscar(filtros, pagina)` — búsqueda con filtros (el modelo
   Mihon: la fuente configurable sin tocar la UI).
3. `cabecerasImagen()` + `hostImagenes()` — la fuente ES dueña
   de las cabeceras HTTP de sus imágenes (lección Ikigai: sin
   `Sec-Fetch`/`Referer` el CDN responde 403/404 disfrazado).
   El cargador de imágenes del kernel las aplica por host.
4. `ContextoMfx2.evaluarJs(etiqueta, codigo)` — puente al Motor
   QuickJS del kernel (Módulo C, hilo único real): tokens de
   sitios Qwik, parseos protegidos, decodificadores. Los
   objetos se devuelven con `JSON.stringify` dentro del script.

**Recomendación viva**: todo plugin v2 debe disparar un canario
JS al activar (ej. `evaluarJs("mi-canario", "6 * 7")`) y logar
el resultado: prueba el circuito plugin→PuenteJs→MotorJs en el
arranque, en producción (patrón ikigaimangas).

## Estructura del repo

```
index.json                 <- catálogo único (una entrada por fuente)
mfx/<id>/                  <- código fuente Kotlin del plugin
  ├─ manifest.json         <- id, name, version, apiVersion, pluginClassName
  ├─ build.gradle.kts      <- módulo del plugin
  └─ src/main/kotlin/.../  <- Fuente*.kt + Plugin*.kt
api/                       <- superficie pública congelada (vendored de la APK)
firmas/                    <- llave pública PEM (la privada SOLO es secret CI)
builds (rama)              <- artefactos .mfx publicados
```

## index.json: campos por extensión

Declarados a mano: `id`, `name`, `lang`, `version`,
`apiVersion`, `status` ("broken|alpha|beta|stable"),
`description`, `manifest`, `icon`, `baseUrl`, `theme`,
`rateLimit`, `recoveryUrl`, y los cuatro de publicación
**vacíos** (`urlMfx`, `fileHashMfx`, `firmaMfx`, `versionMfx`):
el CI los rellena al publicar.

## manifest.json

```json
{
  "id": "ejemplo", "name": "Ejemplo",
  "version": 1, "apiVersion": 2,
  "pluginClassName": "com.harley.mangafox.mfx.ejemplo.PluginEjemplo"
}
```

Todo cambio de código o manifest sube `version` (+1). El int es
el versionCode de comparación OTA (como Android/Mihon); crece
sin límite y es la única llave de actualización.

## Reglas de oro

1. La extensión DESCRIBE y CONSULTA; la APK INTERPRETA y
   PINTA. Cero diseño en el plugin.
2. Cortesía con el sitio SIEMPRE: todo `clienteWeb` nace con
   ritmo (rate limit) medido y honesto.
3. Firma ECDSA obligatoria; sin firma válida no hay relevo.
4. Kotlin de la fábrica = Kotlin de la APK (2.2.10), jamás
   mayor (un Kotlin mayor revienta en runtime).
5. La `api/` cambia solo con este CONTRATO, nunca sola.
6. Estado honesto: si una capacidad no existe aún (ej. búsqueda
   por título pendiente de token Qwik), devolver vacío con log
   explícito, jamás datos inventados.

## Checklist para una extensión nueva

- [ ] Carpeta `mfx/<id>/` (manifest + build.gradle + src)
- [ ] `apiVersion`: 1 (solo scraping) o 2 (filtros + JS + cabeceras)
- [ ] Entrada en `index.json` con los 4 campos de publicación vacíos
- [ ] Icono webp/jpg/png en vivo (nada de .ico)
- [ ] Ritmo/cortesía medido y declarado; recoveryUrl si el dominio es volátil
- [ ] Si es v2: canario JS al activar + filtros + cabecerasImagen si el CDN las exige
- [ ] version=1, status="alpha", probada en la APK congelada
