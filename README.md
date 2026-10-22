# Repo de extensiones MangaFox (sistema .mfx)

Extensiones de **fuentes de manga** para la app [MangaFox](https://github.com/harley654321/MangaFox).

La APK de MangaFox es un **kernel estable**: no contiene fuentes. Cada fuente es
un **plugin `.mfx`** compilado (ZIP con bytecode Kotlin) y **firmado con ECDSA**;
la APK lo verifica (firma + SHA-256) antes de activarlo. Las extensiones de datos
del sistema antiguo fueron retiradas por completo: este repo vive en el sistema
`.mfx` con las extensiones firmadas v1 (**mhscans** y **jeazscans**).

## Enlace único de importación

```
https://raw.githubusercontent.com/harley654321/mangafox-extensions/main/index.json
```

## Estructura

```
index.json                        ← catálogo: una entrada por extensión .mfx
mfx/<id>/                         ← código fuente del plugin (Kotlin, compila a .mfx)
  ├─ manifest.json                ← id, version, apiVersion, pluginClassName
  ├─ build.gradle.kts             ← módulo del plugin
  └─ src/main/kotlin/.../         ← Fuente*.kt + Plugin*.kt (punto de entrada)
api/                              ← superficie API pública congelada (vendored de la APK)
  └─ com/harley/mangafox/...      ← modelos, parser, ClienteWeb, MangaFoxLog, FuenteMfx
CONTRATO-EXTENSIONES.md           ← contrato obligatorio de cada extensión
builds (rama)                     ← artefactos .mfx publicados (rama builds)
```

## Compilar y firmar (un solo comando)

La tarea raíz `construirTodas` **auto-descubre todas las extensiones**
(`mfx/<id>/`) y corre el pipeline completo de cada una: jar → d8 (DEX) →
zip → sha256 → firma ECDSA → auto-verificación → actualización de
index.json. Sin openssl ni python: cripto 100% JDK. Una extensión por
separada: `:mfx-<id>:construirMfx`.

```
./gradlew construirTodas -PsdkAndroid=$ANDROID_HOME -PllaveMfx=/ruta/llave-pkcs8.pem
```

### Agregar una extensión nueva (cero edición de CI)

1. Carpeta `mfx/<id>/`: `build.gradle.kts` (copia del de una extensión
   viva), `manifest.json` (`id`, `version: 1`, `apiVersion: 1`,
   `pluginClassName`) y `src/` (Fuente + Plugin).
2. Entrada en `index.json` con `"urlMfx": ""`, `"fileHashMfx": ""`,
   `"firmaMfx": ""`: el CI los rellena al publicar.
3. Push a main. El CI la descubre, compila, firma, auto-verifica y
   publica: settings y workflow NO se tocan (checklist completo en
   CONTRATO-EXTENSIONES.md).

Resolución automática: SDK por -PsdkAndroid, ANDROID_HOME o
local.properties (sdk.dir); android.jar por -PandroidJar o la
plataforma más nueva del SDK; d8 por -Pd8 o el build-tools más
nuevo. La llave debe ser **PEM PKCS#8** (`-----BEGIN PRIVATE KEY-----`),
ECDSA P-256: es el par de la clave pública pineada en la APK
(ClavesMfx). Nunca se commitea (está en .gitignore).

## Reglas duras

- **Kotlin 2.2.10 = el de la APK**: la nueva APK MangaFox (plantilla
  TemplateIDE, Kotlin integrado 2.2.10, BUILD SUCCESSFUL del dueño oct-2026).
  REGLA INVARIANTABLE: el Kotlin de la fábrica siempre es el mismo que el de
  la APK, jamás mayor (un Kotlin mayor revienta en runtime con
  `NoClassDefFoundError`: clase interna que el stdlib de la APK no tiene).
- **apiVersion congelada**: la `api/` de este repo es la superficie pública
  que la APK expone; cambia solo con el CONTRATO, nunca unilateralmente.
- **Firma ECDSA obligatoria**: un .mfx sin firma válida es rechazado por el
  Instalador de la APK.
- Cada cambio de una extensión sube `version` (+1); `construirMfx` registra
  en `index.json` el nuevo `fileHashMfx` + `firmaMfx` + `versionMfx` (el
  script externo fue eliminado: la tarea Gradle es la única vía).

## Flujo de trabajo (estilo Cloudstream)

El CI del repo (`.github/workflows/build-mfx.yml`) compila, firma y
publica AUTOMÁTICAMENTE en cada push a main que toque una
extensión, la superficie `api/` o el build:

1. `construirTodas` ensambla cada .mfx (auto-descubierto) y lo firma
   con la llave privada del secret `MFX_LLAVE_PRIVADA` del repo.
2. **Auto-verificación**: la firma se valida contra
   `firmas/llave-publica.pem` (la clave pineada en la APK). Si no
   verifica, no se publica NADA.
3. `index.json` se actualiza en main (hash + firma + versión) y el
   .mfx se publica en la rama `builds`.

También se puede construir localmente (misma tarea, mismo
pipeline) para pruebas sin publicar nada.
