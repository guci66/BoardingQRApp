package com.example.boardingqr

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.BinaryBitmap
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val dao by lazy { (application as BoardingApp).db.recordDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = brandLightColors()) {
                AppScaffold(dao)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(dao: BoardingRecordDao) {
    var screen by remember { mutableStateOf("home") }
    var lastRaw by remember { mutableStateOf<String?>(null) }
    var lastInfo by remember { mutableStateOf<PermitInfo?>(null) }
    var validation by remember { mutableStateOf<ValidationResult?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleRaw(result.contents!!, onParsed = { info, vr ->
                lastRaw = result.contents
                lastInfo = info
                validation = vr
            }, onInvalid = {
                lastRaw = result.contents
                lastInfo = null
                validation = ValidationResult(false, listOf("QR code is not valid JSON or missing fields."))
            })
            screen = "detail"
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val decoded = decodeQrFromImage(LocalContext.current.contentResolver, uri)
            if (decoded != null) {
                handleRaw(decoded, onParsed = { info, vr ->
                    lastRaw = decoded
                    lastInfo = info
                    validation = vr
                }, onInvalid = {
                    lastRaw = decoded
                    lastInfo = null
                    validation = ValidationResult(false, listOf("Selected image does not contain a valid QR payload."))
                })
            } else {
                lastRaw = null
                lastInfo = null
                validation = ValidationResult(false, listOf("Failed to detect QR from the selected image."))
            }
            screen = "detail"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Boarding QR") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == "home",
                    onClick = { screen = "home" },
                    label = { Text("Home") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = screen == "history",
                    onClick = { screen = "history" },
                    label = { Text("History") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = screen == "sample",
                    onClick = { screen = "sample" },
                    label = { Text("Sample QR") },
                    icon = {}
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                "home" -> HomeScreen(
                    onScan = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Align the QR code within the frame")
                            setBeepEnabled(true)
                            setOrientationLocked(true)
                            captureActivity = CaptureActivity::class.java
                        }
                        scanLauncher.launch(options)
                    },
                    onImportImage = { pickImage.launch("image/*") }
                )
                "detail" -> DetailScreen(
                    raw = lastRaw,
                    info = lastInfo,
                    validation = validation,
                    dao = dao,
                    onDone = { screen = "home" }
                )
                "history" -> HistoryScreen(dao = dao)
                "sample" -> SampleQrScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(onScan: () -> Unit, onImportImage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("Scan QR")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onImportImage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(id = R.string.import_image))
        }
    }
}

@Composable
fun DetailScreen(
    raw: String?,
    info: PermitInfo?,
    validation: ValidationResult?,
    dao: BoardingRecordDao,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val nowIso = remember { Instant.now().toString() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Scan Result", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (info != null) {
            InfoCard(info = info, validation = validation)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val canAccept = validation?.ok == true
                Button(
                    onClick = {
                        scope.launch {
                            val record = BoardingRecord(
                                permitNo = info.permit_no,
                                name = info.name,
                                zones = info.zones.joinToString(","),
                                status = info.status,
                                validToIso = info.valid_to,
                                scannedAtIso = nowIso,
                                result = if (canAccept) "ACCEPT" else "REJECT",
                                reason = if (canAccept) null else validation?.reasons?.joinToString("; ")
                            )
                            dao.insert(record)
                            onDone()
                        }
                    },
                    enabled = canAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) { Text("Accept") }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val reason = validation?.reasons?.joinToString("; ")
                                ?: "Rejected by operator"
                            val record = BoardingRecord(
                                permitNo = info.permit_no,
                                name = info.name,
                                zones = info.zones.joinToString(","),
                                status = info.status,
                                validToIso = info.valid_to,
                                scannedAtIso = nowIso,
                                result = "REJECT",
                                reason = reason
                            )
                            dao.insert(record)
                            onDone()
                        }
                    },
                ) { Text("Reject") }
            }
        } else {
            Text("Invalid QR payload.", color = ErrorRed)
            Spacer(Modifier.height(12.dp))
            raw?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone) { Text("Back") }
        }
    }
}

@Composable
fun InfoCard(info: PermitInfo, validation: ValidationResult?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            RowItem("Permit No", info.permit_no)
            RowItem("Name", info.name)
            RowItem("Zones", info.zones.joinToString(","))
            RowItem("Status", info.status)
            RowItem("Valid To", info.valid_to)
            if (validation != null) {
                Spacer(Modifier.height(8.dp))
                if (validation.ok) {
                    Text("Ready to board", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Not eligible:", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                    validation.reasons.forEach {
                        Text("• " + it, color = ErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
fun RowItem(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(dao: BoardingRecordDao) {
    val allRecords by dao.all().collectAsState(initial = emptyList())

    var status by remember { mutableStateOf("All") } // All / ACCEPT / REJECT
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    // DatePicker states
    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val filtered = remember(allRecords, status, startDate, endDate) {
        allRecords.filter { r ->
            val keepStatus = when (status) {
                "All" -> true
                else -> r.result == status
            }
            val date = Instant.parse(r.scannedAtIso).atZone(ZoneId.systemDefault()).toLocalDate()
            val afterStart = startDate?.let { !date.isBefore(it) } ?: true
            val beforeEnd = endDate?.let { !date.isAfter(it) } ?: true
            keepStatus && afterStart && beforeEnd
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // Filters bar
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(id = R.string.filters), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Status dropdown
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        TextField(
                            readOnly = true,
                            value = status,
                            onValueChange = {},
                            label = { Text(stringResource(id = R.string.status)) },
                            modifier = Modifier.menuAnchor().weight(1f)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("All", "ACCEPT", "REJECT").forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = {
                                    status = opt
                                    expanded = false
                                })
                            }
                        }
                    }

                    // Start date
                    OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(startDate?.toString() ?: "Start")
                    }
                    if (showStartPicker) {
                        DatePickerDialog(
                            onDismissRequest = { showStartPicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    startPickerState.selectedDateMillis?.let {
                                        startDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                    }
                                    showStartPicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
                            }
                        ) {
                            DatePicker(state = startPickerState)
                        }
                    }

                    // End date
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(endDate?.toString() ?: "End")
                    }
                    if (showEndPicker) {
                        DatePickerDialog(
                            onDismissRequest = { showEndPicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    endPickerState.selectedDateMillis?.let {
                                        endDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                    }
                                    showEndPicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
                            }
                        ) {
                            DatePicker(state = endPickerState)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        // Export filtered CSV
                        val uri = exportCsv(context, filtered)
                        if (uri != null) {
                            shareFile(context, uri)
                        }
                    }) {
                        Text(stringResource(id = R.string.export_filtered_csv))
                    }
                    OutlinedButton(onClick = {
                        status = "All"; startDate = null; endDate = null
                    }) { Text(stringResource(id = R.string.clear_filters)) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Text("No records for the current filter.")
        } else {
            filtered.forEach { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.result + " • " + r.permitNo, fontWeight = FontWeight.SemiBold,
                            color = if (r.result == "ACCEPT") SuccessGreen else ErrorRed)
                        Text("Name: " + r.name)
                        Text("Zones: " + r.zones + " | Status: " + r.status)
                        Text("Valid to: " + r.validToIso)
                        Text("Scanned at: " + r.scannedAtIso)
                        r.reason?.let { Text("Reason: " + it, color = ErrorRed) }
                    }
                }
            }
        }
    }
}

private fun exportCsv(context: Context, items: List<BoardingRecord>): Uri? {
    return try {
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val file = File(context.getExternalFilesDir(null), "boarding_history_" + ts + ".csv")
        FileOutputStream(file).use { out ->
            val sb = StringBuilder()
            sb.append("id,permit_no,name,zones,status,valid_to,scanned_at,result,reason\n")
            fun esc(s: String?): String {
                val v = s ?: ""
                val q = v.replace(""", """")
                return """ + q + """
            }
            items.forEach { r ->
                sb.append(r.id).append(",")
                    .append(esc(r.permitNo)).append(",")
                    .append(esc(r.name)).append(",")
                    .append(esc(r.zones)).append(",")
                    .append(esc(r.status)).append(",")
                    .append(esc(r.validToIso)).append(",")
                    .append(esc(r.scannedAtIso)).append(",")
                    .append(esc(r.result)).append(",")
                    .append(esc(r.reason))
                    .append("\n")
            }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        FileProvider.getUriForFile(context, "com.example.boardingqr.fileprovider", file)
    } catch (e: Exception) {
        null
    }
}

private fun shareFile(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}

private fun decodeQrFromImage(resolver: ContentResolver, uri: Uri): String? {
    return try {
        resolver.openInputStream(uri).use { input ->
            val bitmap = BitmapFactory.decodeStream(input) ?: return null
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val result = QRCodeReader().decode(binary)
            result.text
        }
    } catch (e: Exception) {
        null
    }
}

// ----- QR rendering (sample) -----
@Composable
fun SampleQrScreen() {
    val sampleJson = """{ "permit_no":"HFTP-RAAP-2025-008901", "name":"Yang Min", "zones":["B"], "status":"active", "valid_to":"2025-11-02T23:59:00+08:00" }"""
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Sample QR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("You can scan this QR from another device to test.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        val bmp = remember(sampleJson) { generateQrBitmap(sampleJson, 720, 720) }
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Sample QR",
                modifier = Modifier.size(240.dp)
            )
        } else {
            Text("Failed to render QR")
        }
        Spacer(Modifier.height(12.dp))
        Text(sampleJson, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun generateQrBitmap(text: String, width: Int, height: Int): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        asImageBitmap(bmp)
    } catch (e: Exception) {
        null
    }
}

// ZXing RGBLuminanceSource port for bitmap decoding
class RGBLuminanceSource(private val width: Int, private val height: Int, pixels: IntArray) :
    com.google.zxing.LuminanceSource(width, height) {

    private val luminances: ByteArray = ByteArray(width * height)

    init {
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                val pixel = pixels[offset + x]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                // Efficient luminance conversion
                luminances[offset + x] = ((r + g + g + b) shr 2).toByte()
            }
        }
    }

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val start = y * width
        val r = row ?: ByteArray(width)
        System.arraycopy(luminances, start, r, 0, width)
        return r
    }

    override fun getMatrix(): ByteArray = luminances
}

// ----- Parsing & Validation helpers -----
private fun handleRaw(
    raw: String,
    onParsed: (PermitInfo, ValidationResult) -> Unit,
    onInvalid: () -> Unit
) {
    val parsed = PermitParser.fromJson(raw)
    if (parsed.isSuccess) {
        val info = parsed.getOrThrow()
        val vr = PermitValidator.validate(info)
        onParsed(info, vr)
    } else {
        onInvalid()
    }
}