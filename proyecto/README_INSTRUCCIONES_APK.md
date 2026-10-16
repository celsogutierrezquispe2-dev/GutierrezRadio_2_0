# Gutiérrez Radio 2.0 — Android

Esta es una aplicación **nativa de Android** con diseño oscuro azul y cian, inspirada en la maqueta del reproductor Gutiérrez Radio. Está escrita en Java sin bibliotecas de terceros, orientada a Android 8 o posterior.

## Funciones implementadas

- Añadir una o varias canciones y archivos de audio de tu Samsung mediante el selector de archivos de Android.
- Reproducir, pausar, avanzar, retroceder y detener la música.
- Buscar una posición dentro de la canción usando la barra de progreso.
- Consultar y administrar la playlist; tocar para reproducir y mantener presionada una canción para eliminarla.
- Modo de reproducción aleatoria.
- Control de volumen de la música.
- Anuncios de la hora cada **15, 20 o 30 minutos de reloj**, o desactivados (OFF).
- Botón «Probar voz», y una frase de introducción configurable para el anuncio de la hora.
- Reproducción en segundo plano mediante servicio en primer plano y notificación de música.
- Logo Gutiérrez Radio en color azul / cian integrado.

**IMPORTANTE:** «Locución propia» personaliza la **frase de texto que pronuncia la voz del teléfono** antes de la hora; todavía no permite grabar una voz personalizada ni importar una grabación para sustituir al locutor. Los anuncios usan la voz en español del sistema Android. La aplicación no transmite radio por internet.

## Compilar el APK SIN computadora

La opción preparada para el teléfono es GitHub Actions:

1. Descarga el archivo del proyecto `GutierrezRadio_2_0.zip` y el archivo `Crear_APK_GutierrezRadio_2.yml` que se entregan por separado.
2. Desde Chrome, visita `https://github.com/new`. Inicia sesión o crea una cuenta gratuita en GitHub.
3. Crea un repositorio llamado `GutierrezRadio`, con visibilidad Public o Private. Marca **Add a README file** y crea el repositorio.
4. Dentro del repositorio, toca **Add file → Upload files**, selecciona solamente `GutierrezRadio_2_0.zip` y confirma **Commit changes**. Debe quedar en la raíz del repositorio. Si no encuentras esos botones, activa **Sitio de escritorio** en el navegador del celular.
5. Toca **Add file → Create new file**. Ponle el nombre exacto `.github/workflows/crear-apk.yml`.
6. Abre `Crear_APK_GutierrezRadio_2.yml` con un editor de texto, copia **todo** su contenido y pégalo en el nuevo archivo de GitHub; guarda con **Commit changes**.
7. Ve a la pestaña **Actions**, abre **Crear APK Gutiérrez Radio 2.0** y pulsa **Run workflow** si no se ha iniciado ya.
8. Cuando termine con una marca verde, abre la ejecución y descarga el archivo bajo **Artifacts** llamado `GutierrezRadio-2-APK`.
9. Descomprime ese ZIP de GitHub; dentro está `app-debug.apk`. Instala ese APK desde Mis archivos, autorizando la instalación de esa fuente si Android te lo pide.
10. Abre Gutiérrez Radio, da permiso de notificaciones (si aparece), elige MP3 y pulsa ▶.

Si la ejecución aparece con una X roja, todavía no se ha creado el APK. Envía el mensaje de error de **Actions** para corregirlo. **No se ha ejecutado la compilación en este entorno, por lo que el APK no está probado todavía**.

## Detalles técnicos

- Android Gradle Plugin: 8.7.3
- Gradle en GitHub Actions: 8.9
- Java/JDK: 17
- compileSdk 35, targetSdk 34, minSdk 26
- Nombre de paquete: `com.gutierrez.radio`
- Depende de Google Text-to-Speech o el motor TTS en español que esté configurado en el teléfono.
- No incluye canciones preinstaladas ni necesita internet para reproducir audios ya seleccionados.
- El APK generado es una **versión de prueba firmada**; no es un APK para publicar en Google Play.
- Si tienes instalada otra versión de Gutiérrez Radio y no permite instalar encima por firmas distintas, quizá debas desinstalar la versión anterior (respalda tus ajustes y lista antes de hacerlo).

Si lo deseas, el proyecto también se puede abrir y compilar con Android Studio en una computadora.
