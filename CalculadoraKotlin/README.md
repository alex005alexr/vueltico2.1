# Vueltico — Android (Kotlin + Jetpack Compose)

Aplicación Android para convertir USD BCV, EUR BCV y USDT paralelo a bolívares y viceversa, guardar perfiles de pago y compartir comprobantes personalizados.

## Cambios incluidos

- Nombre visible de la aplicación: **Vueltico**.
- Icono de aplicación personalizado.
- Tasas automáticas:
  - USD BCV: `https://ve.dolarapi.com/v1/dolares/oficial`.
  - Segunda fuente de verificación USD BCV: `https://pydolarve.org/api/v1/dollar?page=bcv`.
  - EUR BCV: `https://ve.dolarapi.com/v1/euros/oficial`.
  - USDT paralelo: `https://ve.dolarapi.com/v1/dolares/paralelo` (campo `promedio`).
- El selector de moneda está en la parte superior y abre por defecto en USD BCV.
- La pantalla principal ya no muestra la fecha de actualización ni mensajes rutinarios de verificación; solo presenta una advertencia roja cuando una tasa no puede verificarse o una fuente no está disponible.
- Perfiles de pago: Pago Móvil, transferencia bancaria, Zelle y Binance.
- Ajustes para elegir un logo de la galería. La imagen se reduce y comprime para guardarse localmente.
- El comprobante compartido se genera como imagen, con el logo arriba y los detalles debajo. También se incluye el texto como caption al compartir, cuando la aplicación de destino lo admite.
- Los comprobantes incluyen fecha/hora, importe, tasa aplicada y los datos de pago junto con el monto asociado. No incluyen la frase “tasa verificada” ni el nombre de las fuentes.

## Abrir en Android Studio

1. Descomprime `Vueltico_Kotlin_Android.zip`.
2. Abre la carpeta `CalculadoraKotlin` en Android Studio.
3. Acepta la sincronización de Gradle y espera a que descargue las dependencias.
4. Ejecuta la aplicación en un dispositivo o emulador Android.

## Requisitos

- Android Studio reciente.
- JDK 17.
- Conexión a Internet para descargar dependencias y consultar tasas.
- `minSdk = 26`, `compileSdk = 35`, Java/Kotlin JVM target 17.

## Notas

- Si una API no responde, Vueltico conserva la última tasa guardada cuando existe y muestra una advertencia roja.
- El valor de USDT procede del campo `promedio` del endpoint paralelo solicitado; es una referencia de mercado y no una tasa oficial del BCV.
- Las cuentas y los ajustes se guardan localmente en el dispositivo.
- El proyecto se ha actualizado, pero no se pudo ejecutar una compilación Android real en este entorno; debe sincronizarse y compilarse en Android Studio para verificarlo con el SDK instalado.


## Últimos cambios de Vueltico

- Icono de la aplicación reemplazado por el logo proporcionado por el usuario.
- La tasa USDT se consulta desde `https://ve.dolarapi.com/v1/dolares/paralelo`, campo `promedio`.
- En Ajustes, el usuario puede activar o desactivar la opción separada de compartir/copiar datos de pago.
- Al seleccionar una cuenta desde «Compartir Cobro», se puede compartir el comprobante con imagen y texto, compartir solo los datos bancarios o copiarlos.
- El texto de datos de pago es compacto, sin etiquetas decorativas, e incluye el monto en bolívares.
- El comprobante muestra siempre los montos en divisa y en bolívares.
