package com.calculadora.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val PREFS = "calculadora_prefs"
private const val KEY_ACCOUNTS = "accounts_json"
private const val KEY_SETTINGS = "settings_json"
private const val KEY_RATES = "rates_json"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF1769AA))) { CalculadoraApp() } }
    }
}

private data class Rates(
    val usd: Double = 0.0,
    val eur: Double = 0.0,
    val usdt: Double = 0.0,
    val usdSource2: Double = 0.0,
    val updatedAt: Long = 0L,
    val status: String = "Sin verificar",
    val warning: Boolean = false
)

private data class PaymentAccount(
    val id: String = System.currentTimeMillis().toString(),
    val method: String,
    val name: String = "",
    val bank: String = "",
    val identity: String = "",
    val phone: String = "",
    val holder: String = "",
    val accountNumber: String = "",
    val accountType: String = "Corriente",
    val email: String = "",
    val binanceId: String = ""
) {
    fun copyText(): String = when (method) {
        "Pago Móvil" -> listOfNotNull(
            name.takeIf { it.isNotBlank() }?.let { "Nombre: $it" },
            "Banco: $bank", "Cédula/RIF: $identity", "Teléfono: $phone"
        ).joinToString("\n")
        "Transferencia Bancaria" -> "Titular: $holder\nBanco: $bank\nTipo de cuenta: $accountType\nNúmero de cuenta: $accountNumber\nCédula/RIF: $identity"
        "Zelle" -> "Titular Zelle: $holder\nCorreo: $email"
        else -> "Titular: $holder\nBinance Pay ID / correo: $binanceId"
    }
}

private data class AppSettings(
    val companyName: String = "Mi Empresa",
    val receiptType: String = "Solicitud de Pago",
    val customReceiptType: String = "",
    val footer: String = "¡Gracias por su preferencia!",
    val manualUsdt: String = "",
    val companyImageBase64: String = "",
    val enablePaymentDataMessage: Boolean = true
)

@Composable
private fun CalculadoraApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Calculadora") }
    var paymentTab by remember { mutableStateOf("Pago Móvil") }
    var rates by remember { mutableStateOf(Rates()) }
    val latestRates by rememberUpdatedState(rates)
    var settings by remember { mutableStateOf(AppSettings()) }
    val accounts = remember { mutableStateListOf<PaymentAccount>() }
    var loading by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("1") }
    var currency by remember { mutableStateOf("USD") }
    var toVes by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<PaymentAccount?>(null) }
    var showAccountForm by remember { mutableStateOf(false) }
    var showSharePicker by remember { mutableStateOf(false) }
    var selectedForShare by remember { mutableStateOf<PaymentAccount?>(null) }
    var showShareActions by remember { mutableStateOf(false) }
    var companyDraft by remember { mutableStateOf(settings.companyName) }
    var receiptDraft by remember { mutableStateOf(settings.receiptType) }
    var customTitleDraft by remember { mutableStateOf(settings.customReceiptType) }
    var footerDraft by remember { mutableStateOf(settings.footer) }
    var usdtDraft by remember { mutableStateOf(settings.manualUsdt) }
    var companyImageDraft by remember { mutableStateOf(settings.companyImageBase64) }
    var enablePaymentDataMessageDraft by remember { mutableStateOf(settings.enablePaymentDataMessage) }
    val pickCompanyImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (original != null) {
                        val scale = minOf(1.0, 1200.0 / maxOf(original.width, original.height))
                        val resized = Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true)
                        val out = java.io.ByteArrayOutputStream()
                        resized.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        companyImageDraft = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        Toast.makeText(context, "Logo cargado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) { Toast.makeText(context, "No se pudo cargar la imagen", Toast.LENGTH_LONG).show() }
        }
    }
    var errorMessage by remember { mutableStateOf("") }

    fun persistAccounts() {
        val array = JSONArray()
        accounts.forEach { a -> array.put(JSONObject().apply {
            put("id", a.id); put("method", a.method); put("name", a.name); put("bank", a.bank)
            put("identity", a.identity); put("phone", a.phone); put("holder", a.holder)
            put("accountNumber", a.accountNumber); put("accountType", a.accountType); put("email", a.email); put("binanceId", a.binanceId)
        }) }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    fun persistSettings() {
        prefs.edit().putString(KEY_SETTINGS, JSONObject().apply {
            put("companyName", settings.companyName); put("receiptType", settings.receiptType)
            put("customReceiptType", settings.customReceiptType); put("footer", settings.footer)
            put("manualUsdt", settings.manualUsdt); put("companyImageBase64", settings.companyImageBase64)
            put("enablePaymentDataMessage", settings.enablePaymentDataMessage)
        }.toString()).apply()
    }

    fun persistRates(r: Rates) {
        prefs.edit().putString(KEY_RATES, JSONObject().apply {
            put("usd", r.usd); put("eur", r.eur); put("usdt", r.usdt); put("usdSource2", r.usdSource2)
            put("updatedAt", r.updatedAt); put("status", r.status); put("warning", r.warning)
        }.toString()).apply()
    }

    fun refreshRates() {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                val result = fetchRates()
                val merged = result.copy(
                    usd = if (result.usd > 0.0) result.usd else latestRates.usd,
                    eur = if (result.eur > 0.0) result.eur else latestRates.eur,
                    usdt = if (result.usdt > 0.0) result.usdt else latestRates.usdt,
                    updatedAt = if (result.usd > 0.0 || result.eur > 0.0 || result.usdt > 0.0) result.updatedAt else latestRates.updatedAt,
                    status = result.status, warning = result.warning || (result.usd <= 0.0) || (result.eur <= 0.0) || (result.usdt <= 0.0)
                )
                rates = merged
                persistRates(merged)
                if (result.usd <= 0.0 && result.eur <= 0.0) errorMessage = "No se pudieron obtener tasas nuevas. Se conservan las últimas tasas guardadas."
            } catch (e: Exception) {
                errorMessage = "No se pudo actualizar. Comprueba tu conexión; se conservan las tasas guardadas."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val cachedRates = prefs.getString(KEY_RATES, null)
            if (cachedRates != null) {
                val j = JSONObject(cachedRates)
                rates = Rates(j.optDouble("usd"), j.optDouble("eur"), j.optDouble("usdt"), j.optDouble("usdSource2"), j.optLong("updatedAt"), j.optString("status", "Sin verificar"), j.optBoolean("warning"))
            }
            val savedSettings = prefs.getString(KEY_SETTINGS, null)
            if (savedSettings != null) {
                val j = JSONObject(savedSettings)
                settings = AppSettings(j.optString("companyName", "Mi Empresa"), j.optString("receiptType", "Solicitud de Pago"), j.optString("customReceiptType", ""), j.optString("footer", "¡Gracias por su preferencia!"), j.optString("manualUsdt", ""), j.optString("companyImageBase64", ""), j.optBoolean("enablePaymentDataMessage", true))
                companyDraft = settings.companyName; receiptDraft = settings.receiptType
                customTitleDraft = settings.customReceiptType; footerDraft = settings.footer; usdtDraft = settings.manualUsdt; companyImageDraft = settings.companyImageBase64; enablePaymentDataMessageDraft = settings.enablePaymentDataMessage
            }
            val savedAccounts = prefs.getString(KEY_ACCOUNTS, null)
            if (savedAccounts != null) {
                val arr = JSONArray(savedAccounts)
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    accounts.add(PaymentAccount(j.optString("id"), j.optString("method"), j.optString("name"), j.optString("bank"), j.optString("identity"), j.optString("phone"), j.optString("holder"), j.optString("accountNumber"), j.optString("accountType", "Corriente"), j.optString("email"), j.optString("binanceId")))
                }
            }
        } catch (_: Exception) { }
        refreshRates()
    }

    val rate = when (currency) { "EUR" -> rates.eur; "USDT" -> rates.usdt; else -> rates.usd }
    val amountNumber = amount.replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
    val rateDecimal = BigDecimal.valueOf(rate.coerceAtLeast(0.0))
    val result = if (rate > 0.0) {
        if (toVes) amountNumber.multiply(rateDecimal).setScale(2, RoundingMode.HALF_UP)
        else amountNumber.divide(rateDecimal, 2, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO.setScale(2)
    val formatter = remember { java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale("es", "VE"))) }

    Scaffold(
        topBar = { TopAppBarCompat(title = "Vueltico", onRefresh = { refreshRates() }, loading = loading) },
        bottomBar = {
            NavigationBar {
                listOf("Calculadora", "Cuentas", "Ajustes").forEach { item ->
                    NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Text(when (item) { "Calculadora" -> "⇄"; "Cuentas" -> "▣"; else -> "⚙" }) }, label = { Text(item) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                "Calculadora" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        RateCard("🇻🇪", "USD BCV", rates.usd, currency == "USD", { currency = "USD" }, formatter, Modifier.weight(1f))
                        RateCard("🇪🇺", "EUR BCV", rates.eur, currency == "EUR", { currency = "EUR" }, formatter, Modifier.weight(1f))
                        RateCard("₮", "USDT paralelo", rates.usdt, currency == "USDT", { currency = "USDT" }, formatter, Modifier.weight(1f))
                    }
                    if (rates.warning || errorMessage.isNotBlank()) {
                        Text("⚠ No se pudo verificar una o más tasas. Se muestra la última tasa disponible cuando existe.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Conversión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Moneda seleccionada: $currency")
                            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } }, label = { Text(if (toVes) "Monto en $currency" else "Monto en bolívares") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { toVes = !toVes }, modifier = Modifier.fillMaxWidth()) { Text(if (toVes) "⇅  Cambiar a Bolívares → $currency" else "⇅  Cambiar a $currency → Bolívares") }
                            Text("Resultado estimado", style = MaterialTheme.typography.bodyMedium)
                            Text(if (toVes) "Bs. ${formatter.format(result)}" else "${formatter.format(result)} $currency", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(if (rate > 0) "Tasa aplicada: 1 $currency = ${formatter.format(rate)} Bs." else "No hay tasa disponible para $currency", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { showSharePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Compartir Cobro") }
                        }
                    }
                }
                "Cuentas" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf("Pago Móvil", "Transferencia Bancaria", "Zelle", "Binance").forEach { method ->
                            FilterChip(selected = paymentTab == method, onClick = { paymentTab = method }, label = { Text(when (method) { "Transferencia Bancaria" -> "Transferencia"; else -> method }) })
                        }
                    }
                    Button(onClick = { editing = PaymentAccount(method = paymentTab); showAccountForm = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar cuenta") }
                    val visibleAccounts = accounts.filter { it.method == paymentTab }
                    if (visibleAccounts.isEmpty()) Text("Todavía no tienes cuentas registradas para $paymentTab.")
                    visibleAccounts.forEach { account ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(account.name.ifBlank { account.holder.ifBlank { account.method } }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(account.copyText(), style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { copyText(context, account.copyText()); Toast.makeText(context, "Datos copiados", Toast.LENGTH_SHORT).show() }) { Text("Copiar") }
                                    OutlinedButton(onClick = { editing = account; showAccountForm = true }) { Text("Editar") }
                                    TextButton(onClick = { accounts.removeAll { it.id == account.id }; persistAccounts() }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Personalización de recibos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            OutlinedTextField(companyDraft, { companyDraft = it }, label = { Text("Nombre de empresa o negocio") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("Título del comprobante")
                            listOf("Solicitud de Pago", "Recibo de Cobro", "Nota de Venta", "Factura Proforma", "Personalizado").forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = receiptDraft == option, onClick = { receiptDraft = option })
                                    Text(option)
                                }
                            }
                            if (receiptDraft == "Personalizado") OutlinedTextField(customTitleDraft, { customTitleDraft = it }, label = { Text("Título personalizado") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(footerDraft, { footerDraft = it }, label = { Text("Mensaje final opcional") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            Divider()
                            Text("Logo de la empresa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (companyImageDraft.isNotBlank()) {
                                val logoBitmap = remember(companyImageDraft) { try { BitmapFactory.decodeByteArray(Base64.decode(companyImageDraft, Base64.DEFAULT), 0, Base64.decode(companyImageDraft, Base64.DEFAULT).size)?.asImageBitmap() } catch (_: Exception) { null } }
                                if (logoBitmap != null) Image(bitmap = logoBitmap, contentDescription = "Logo de la empresa", modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp))
                                TextButton(onClick = { companyImageDraft = "" }) { Text("Quitar imagen") }
                            }
                            OutlinedButton(onClick = { pickCompanyImage.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Elegir imagen de la galería") }
                            Text("El logo aparecerá en la parte superior del comprobante compartido en las aplicaciones compatibles.", style = MaterialTheme.typography.bodySmall)
                            Divider()
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Opción de datos de pago", fontWeight = FontWeight.SemiBold)
                                    Text("Permite compartir o copiar un texto separado con los datos bancarios y el monto.", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = enablePaymentDataMessageDraft, onCheckedChange = { enablePaymentDataMessageDraft = it })
                            }
                            Button(onClick = {
                                settings = AppSettings(companyDraft, receiptDraft, customTitleDraft, footerDraft, "", companyImageDraft, enablePaymentDataMessageDraft)
                                persistSettings()
                                Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar configuración") }
                        }
                    }
                }
            }
        }
    }

    if (showAccountForm && editing != null) {
        AccountEditor(account = editing!!, onDismiss = { showAccountForm = false; editing = null }, onSave = { updated ->
            val valid = when (updated.method) {
                "Pago Móvil" -> updated.bank.isNotBlank() && updated.identity.isNotBlank() && updated.phone.isNotBlank()
                "Transferencia Bancaria" -> updated.bank.isNotBlank() && updated.holder.isNotBlank() && updated.identity.isNotBlank() && updated.accountNumber.length == 20 && updated.accountNumber.all(Char::isDigit)
                "Zelle" -> updated.holder.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(updated.email).matches()
                else -> updated.binanceId.isNotBlank()
            }
            if (!valid) {
                Toast.makeText(context, if (updated.method == "Transferencia Bancaria") "Completa los campos y verifica que la cuenta tenga 20 dígitos." else "Revisa los campos obligatorios.", Toast.LENGTH_LONG).show()
            } else {
                val index = accounts.indexOfFirst { it.id == updated.id }
                if (index >= 0) accounts[index] = updated else accounts.add(updated)
                persistAccounts(); showAccountForm = false; editing = null
            }
        })
    }

    if (showSharePicker) {
        AlertDialog(
            onDismissRequest = { showSharePicker = false },
            title = { Text("Seleccionar cuenta de pago") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    accounts.forEach { account ->
                        TextButton(onClick = {
                            selectedForShare = account
                            showSharePicker = false
                            showShareActions = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text("${account.method}: ${account.name.ifBlank { account.holder.ifBlank { account.bank } }}", fontWeight = FontWeight.SemiBold); Text(account.copyText(), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    TextButton(onClick = {
                        showSharePicker = false
                        val rateNow = when (currency) { "EUR" -> rates.eur; "USDT" -> rates.usdt; else -> rates.usd }
                        val inputAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
                        val converted = if (toVes) inputAmount * rateNow else if (rateNow > 0) inputAmount / rateNow else 0.0
                        val title = if (settings.receiptType == "Personalizado") settings.customReceiptType.ifBlank { "Recibo" } else settings.receiptType
                        val money = java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale("es", "VE")))
                        val message = "${settings.companyName}\n$title\n${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\nMonto: ${money.format(inputAmount)} ${if (toVes) currency else "Bs."}\nEquivalente: ${money.format(converted)} ${if (toVes) "Bs." else currency}\nTasa: ${money.format(rateNow)} Bs.\n${settings.footer}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }, "Compartir conversión"))
                    }) { Text("Compartir solo la conversión") }
                }
            },
            confirmButton = { TextButton(onClick = { showSharePicker = false }) { Text("Cerrar") } }
        )
    }

    if (showShareActions && selectedForShare != null) {
        val account = selectedForShare!!
        val rateNow = when (currency) { "EUR" -> rates.eur; "USDT" -> rates.usdt; else -> rates.usd }
        val inputAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
        val converted = if (toVes) inputAmount * rateNow else if (rateNow > 0) inputAmount / rateNow else 0.0
        val amountVes = if (toVes) converted else inputAmount
        val money = java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale("es", "VE")))
        val title = if (settings.receiptType == "Personalizado") settings.customReceiptType.ifBlank { "Recibo" } else settings.receiptType
        val dateTime = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date())
        val paymentData = paymentDataText(account, amountVes, money)
        val details = buildString {
            append("${settings.companyName}\n${title.uppercase()}\nFecha: $dateTime\n\n")
            val amountCurrency = if (toVes) inputAmount else converted
            append("Monto en divisa: ${money.format(amountCurrency)} $currency\n")
            append("Monto en bolívares: ${money.format(amountVes)} Bs.\n")
            append("Tasa aplicada: 1 $currency = ${money.format(rateNow)} Bs.\n\n")
            append("DATOS DE PAGO\n${account.method}\n")
            append(account.copyText())
            append("\nMonto: ${money.format(amountVes)} Bs.")
            if (settings.footer.isNotBlank()) append("\n\n${settings.footer}")
        }
        AlertDialog(
            onDismissRequest = { showShareActions = false; selectedForShare = null },
            title = { Text("Compartir cobro") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cuenta seleccionada: ${account.name.ifBlank { account.holder.ifBlank { account.method } }}")
                Button(onClick = {
                    showShareActions = false
                    shareReceipt(context, details, settings.companyImageBase64)
                    selectedForShare = null
                }, modifier = Modifier.fillMaxWidth()) { Text("Compartir comprobante") }
                if (settings.enablePaymentDataMessage) {
                    OutlinedButton(onClick = {
                        showShareActions = false
                        sharePlainText(context, paymentData, "Compartir datos de pago")
                        selectedForShare = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("Compartir datos de pago") }
                    OutlinedButton(onClick = {
                        copyText(context, paymentData)
                        Toast.makeText(context, "Datos de pago copiados", Toast.LENGTH_SHORT).show()
                        showShareActions = false
                        selectedForShare = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("Copiar datos de pago") }
                }
            } },
            confirmButton = { TextButton(onClick = { showShareActions = false; selectedForShare = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TopAppBarCompat(title: String, onRefresh: () -> Unit, loading: Boolean) {
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "Actualizando…" else "↻ Actualizar") }
        }
    }
}

@Composable
private fun RateCard(icon: String, label: String, value: Double, selected: Boolean, onClick: () -> Unit, formatter: java.text.DecimalFormat, modifier: Modifier = Modifier) {
    Card(modifier = modifier, onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(if (value > 0) formatter.format(value) else "N/D", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Bs.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AccountEditor(account: PaymentAccount, onDismiss: () -> Unit, onSave: (PaymentAccount) -> Unit) {
    var name by remember(account.id) { mutableStateOf(account.name) }
    var bank by remember(account.id) { mutableStateOf(account.bank) }
    var identity by remember(account.id) { mutableStateOf(account.identity) }
    var phone by remember(account.id) { mutableStateOf(account.phone) }
    var holder by remember(account.id) { mutableStateOf(account.holder) }
    var number by remember(account.id) { mutableStateOf(account.accountNumber) }
    var type by remember(account.id) { mutableStateOf(account.accountType) }
    var email by remember(account.id) { mutableStateOf(account.email) }
    var binanceId by remember(account.id) { mutableStateOf(account.binanceId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datos de ${account.method}") },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (account.method) {
                    "Pago Móvil" -> {
                        OutlinedTextField(name, { name = it }, label = { Text("Nombre (opcional)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(bank, { bank = it }, label = { Text("Banco") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(identity, { identity = it }, label = { Text("Cédula/RIF") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    }
                    "Transferencia Bancaria" -> {
                        OutlinedTextField(holder, { holder = it }, label = { Text("Titular") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(bank, { bank = it }, label = { Text("Banco") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(number, { number = it.filter(Char::isDigit).take(20) }, label = { Text("Número de cuenta (20 dígitos)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(identity, { identity = it }, label = { Text("Cédula/RIF") }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf("Corriente", "Ahorro").forEach { option -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(type == option, { type = option }); Text(option) } }
                        }
                    }
                    "Zelle" -> {
                        OutlinedTextField(holder, { holder = it }, label = { Text("Nombre del titular") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        OutlinedTextField(holder, { holder = it }, label = { Text("Nombre o usuario (opcional)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(binanceId, { binanceId = it }, label = { Text("Binance Pay ID, correo o teléfono") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(account.copy(name = name.trim(), bank = bank.trim(), identity = identity.trim(), phone = phone.trim(), holder = holder.trim(), accountNumber = number.trim(), accountType = type, email = email.trim(), binanceId = binanceId.trim())) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun paymentDataText(account: PaymentAccount, amountVes: Double, money: java.text.DecimalFormat): String {
    val amount = money.format(amountVes)
    return when (account.method) {
        "Pago Móvil" -> listOf(account.bank, account.identity, account.phone, amount).joinToString("\n")
        "Transferencia Bancaria" -> listOf(account.bank, account.accountNumber, account.identity, amount).joinToString("\n")
        "Zelle" -> listOf(account.holder, account.email, amount).joinToString("\n")
        else -> listOf(account.holder.ifBlank { "Binance" }, account.binanceId, amount).joinToString("\n")
    }
}

private fun sharePlainText(context: Context, text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun shareReceipt(context: Context, receiptText: String, logoBase64: String) {
    try {
        val bitmap = createReceiptBitmap(receiptText, logoBase64)
        val file = File(context.cacheDir, "vueltico_comprobante.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, receiptText)
            clipData = android.content.ClipData.newUri(context.contentResolver, "Comprobante Vueltico", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir comprobante"))
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, receiptText)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir comprobante"))
    }
}

private fun createReceiptBitmap(receiptText: String, logoBase64: String): Bitmap {
    val width = 1080
    val padding = 64f
    val logo: Bitmap? = try {
        if (logoBase64.isBlank()) null else {
            val bytes = Base64.decode(logoBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    } catch (_: Exception) { null }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(28, 39, 48)
        textSize = 38f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(12, 77, 75)
        textSize = 42f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    val lines = mutableListOf<Pair<String, Boolean>>()
    receiptText.split("\n").forEachIndexed { index, raw ->
        val line = raw.replace("*", "").replace("_", "")
        if (line.isBlank()) {
            lines.add("" to false)
        } else {
            val chosen = if (index == 0 || line.contains("DATOS DE PAGO") || line.contains("SOLICITUD") || line.contains("RECIBO") || line.contains("NOTA DE VENTA")) bold else paint
            var current = ""
            line.split(Regex("\\s+")).forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (chosen.measureText(candidate) > width - 2 * padding && current.isNotEmpty()) {
                    lines.add(current to (chosen === bold))
                    current = word
                } else current = candidate
            }
            if (current.isNotEmpty()) lines.add(current to (chosen === bold))
        }
    }
    val logoHeight = if (logo != null) 300f else 70f
    val lineHeight = 54f
    val height = (padding + logoHeight + 32f + lines.size * lineHeight + padding).toInt().coerceAtLeast(500)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(AndroidColor.WHITE)
    var y = padding
    if (logo != null) {
        val scale = minOf((width - 2 * padding) / logo.width.toFloat(), (logoHeight - 24f) / logo.height.toFloat())
        val w = logo.width * scale
        val h = logo.height * scale
        val left = (width - w) / 2f
        canvas.drawBitmap(logo, null, android.graphics.RectF(left, y, left + w, y + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
    y += logoHeight + 32f
    canvas.drawLine(padding, y - 14f, width - padding, y - 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.LTGRAY; strokeWidth = 2f })
    lines.forEach { (line, isBold) ->
        val selectedPaint = if (isBold) bold else paint
        canvas.drawText(line, padding, y, selectedPaint)
        y += if (line.isEmpty()) 22f else lineHeight
    }
    return output
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Datos de pago", text))
}

private suspend fun fetchRates(): Rates = withContext(Dispatchers.IO) {
    coroutineScope {
        val primaryUsd = async { requestJson("https://ve.dolarapi.com/v1/dolares/oficial") }
        val secondaryUsd = async { requestJson("https://pydolarve.org/api/v1/dollar?page=bcv") }
        val euroJson = async { requestJson("https://ve.dolarapi.com/v1/euros/oficial") }
        val parallelUsdtJson = async { requestJson("https://ve.dolarapi.com/v1/dolares/paralelo") }
        val p1 = primaryUsd.await()
        val p2 = secondaryUsd.await()
        val e = euroJson.await()
        val parallel = parallelUsdtJson.await()
        val usd1 = p1?.let { numberAt(it, "promedio") } ?: 0.0
        val usd2 = p2?.let { nestedNumber(it, "monitors", "bcv", "price") } ?: 0.0
        val eur = e?.let { numberAt(it, "promedio") } ?: 0.0
        val usdt = parallel?.let { numberAt(it, "promedio") } ?: 0.0
        val finalUsd = if (usd1 > 0) usd1 else usd2
        val discrepancy = usd1 > 0 && usd2 > 0 && abs(usd1 - usd2) > 0.05
        val status = when {
            usd1 > 0 && usd2 > 0 && !discrepancy -> "Verificada entre fuentes"
            usd1 > 0 && usd2 > 0 && discrepancy -> "Diferencia detectada"
            finalUsd > 0 -> "Sin verificar: una fuente no respondió"
            else -> "Sin conexión: usando última tasa guardada"
        }
        val warning = discrepancy || finalUsd <= 0.0 || eur <= 0.0 || usdt <= 0.0 || (usd1 <= 0.0 && usd2 <= 0.0)
        Rates(usd = finalUsd, eur = eur, usdt = usdt, usdSource2 = usd2, updatedAt = if (finalUsd > 0 || eur > 0 || usdt > 0) System.currentTimeMillis() else 0L, status = status, warning = warning)
    }
}

private fun requestJson(url: String): JSONObject? {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CalculadoraAndroid/1.0")
        }
        if (connection.responseCode !in 200..299) return null
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } catch (_: Exception) { null } finally { connection?.disconnect() }
}

private fun numberAt(json: JSONObject, key: String): Double {
    val value = json.opt(key)
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.replace(',', '.').toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}

private fun nestedNumber(json: JSONObject, first: String, second: String, third: String): Double {
    return try { numberAt(json.getJSONObject(first).getJSONObject(second), third) } catch (_: Exception) { 0.0 }
}
