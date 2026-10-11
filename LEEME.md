# Bloqueo DNS — guía de instalación

Esta app fija el DNS de NextDNS en tu Android y **bloquea a nivel de sistema**:

- el ajuste de "DNS privado" (aparece en gris y no se puede cambiar),
- las VPN,
- las opciones de desarrollador (para que no se pueda quitar desde el ordenador),
- la creación de otros usuarios,
- el "DNS seguro" propio de Chrome y su modo incógnito.

La app no se puede desinstalar ni desactivar mientras esté activa. **Solo se desbloquea con la contraseña** que se pone al bloquear. Sin ella, la única salida es restablecer el móvil de fábrica, que borra todo.

Requisitos: Android 10 o superior, un ordenador con Windows y un cable USB.

---

## Parte 1 — Compilar la app (gratis, con GitHub)

1. Crea una cuenta en **github.com**.
2. Arriba a la derecha pulsa **+ → New repository**. Nombre: `bloqueo-dns`. Marca **Private** y pulsa **Create repository**.
3. En la página del repositorio pulsa el enlace **"uploading an existing file"**.
4. Descomprime el zip y **arrastra todo lo que hay dentro de la carpeta `bloqueo-dns`** a la web, incluida la carpeta `.github`. Pulsa **Commit changes**.
5. Ve a la pestaña **Actions**. Verás "Compilar APK" en marcha. Tarda unos 3–5 minutos.
   - Si no arranca solo: pulsa **Compilar APK → Run workflow**.
6. Cuando salga el tick verde, entra en esa ejecución. Abajo, en **Artifacts**, descarga **bloqueo-dns**. Es un zip que contiene `bloqueo-dns.apk`.

## Parte 2 — Preparar el móvil y el ordenador

**En el móvil:**
1. Ajustes → Acerca del teléfono (en Samsung: Información de software) → toca **7 veces "Número de compilación"**.
2. Ajustes → **Opciones de desarrollador** → activa **Depuración USB**.
   - En Xiaomi activa también "Depuración USB (ajustes de seguridad)".

**En el ordenador:**
1. Descarga **"SDK Platform-Tools for Windows"** desde `developer.android.com/tools/releases/platform-tools`.
2. Descomprímelo, por ejemplo en `C:\platform-tools`, y copia ahí `bloqueo-dns.apk`.
3. Abre esa carpeta, escribe `cmd` en la barra de direcciones y pulsa Enter. Se abre una consola.
4. Conecta el móvil por USB y acepta el aviso de "¿Permitir depuración USB?" que aparece en el móvil.
5. En la consola escribe `adb devices`. Debe salir tu móvil con la palabra `device`.

## Parte 3 — Activar la app

Android solo deja activar una app como "propietaria del dispositivo" si **no hay ninguna cuenta** en el móvil.

1. Ajustes → **Cuentas** → elimina todas: Google, Samsung, Xiaomi, Microsoft... No se borran tus datos y las vuelves a añadir al final. **Asegúrate de saber tu contraseña de Google.**
2. Si tienes **Carpeta segura** (Samsung), "Segundo espacio" (Xiaomi) o un usuario invitado, elimínalos también.
3. En la consola:

```
adb install bloqueo-dns.apk
adb shell dpm set-device-owner com.gerard.bloqueodns/.AdminReceiver
```

Tiene que responder `Success: Device owner set to package...`.

- Si dice que hay cuentas: escribe `adb shell dumpsys account` para ver cuál queda, quítala y repite el comando.
- Si dice que hay varios usuarios o que ya está aprovisionado: queda la Carpeta segura, el Segundo espacio o un usuario invitado.

4. Vuelve a añadir tu cuenta de Google y las demás.

## Parte 4 — Bloquear

1. Abre la app **Bloqueo DNS** con internet activado.
2. Escribe tu dirección de NextDNS: `TUID.dns.nextdns.io` (está en la pestaña Setup de NextDNS).
3. Que **otra persona** escriba la contraseña dos veces, una que tú no sepas.
4. Pulsa **BLOQUEAR**. Pasará a "Estado: BLOQUEADO".
5. Comprueba que funciona: el ajuste de DNS privado debe aparecer en gris, y en **test.nextdns.io** debe salir "status: ok".

## Desbloquear o quitar la app

Abre la app, escribe la contraseña y pulsa **DESBLOQUEAR**. Para quitarla del todo, después pulsa **"Desactivar y desinstalar la app"**.

Si se escribe mal la contraseña, el botón se bloquea 30 segundos.

## A tener en cuenta

- **Si se pierde la contraseña**, la única forma de quitarla es restablecer de fábrica.
- **Algunas wifis públicas** (hoteles, universidades) bloquean el DNS privado. En ellas no tendrías internet mientras esté bloqueado; usa datos móviles.
- Navegadores como Firefox o Brave pueden traer su propio DNS. Mantén **Block Bypass Methods** activado en NextDNS para cubrirlos.
