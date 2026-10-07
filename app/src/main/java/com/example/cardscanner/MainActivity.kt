package com.example.cardscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: Vm by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App(vm) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: Vm) {
    val ctx = LocalContext.current
    var camera by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) camera = true else vm.status = "Camera permission was denied"
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.addFromUris(uris)
    }
    fun needKey(): Boolean = false

    if (camera) {
        BackHandler { camera = false }
        CameraScreen(vm) { camera = false }
    } else {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
            Text("Card Scanner FREE (no API key)", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (!needKey()) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
                        else perm.launch(Manifest.permission.CAMERA)
                    }
                }) { Text("Scan with camera") }
                OutlinedButton(onClick = { if (!needKey()) picker.launch("image/*") }) { Text("From gallery") }
                OutlinedButton(onClick = { vm.exportCsv()?.let { ctx.startActivity(it) } }, enabled = vm.cards.isNotEmpty()) { Text("Export CSV") }
                OutlinedButton(onClick = { vm.loadFromSheet() }, enabled = vm.sheetUrl.isNotBlank()) { Text("Load from Sheet") }
                OutlinedButton(onClick = { settings = true }) { Text("Settings") }
                OutlinedButton(onClick = { confirmClear = true }, enabled = vm.cards.isNotEmpty()) { Text("Clear") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.both, onCheckedChange = { vm.both = it })
                Spacer(Modifier.width(8.dp))
                Text("Card has front and back (gallery: select front, back, front, back...)")
            }
            if (vm.status.isNotEmpty()) Text(vm.status, color = if (vm.isError) Color(0xFFB3261E) else Color.Gray)
            Spacer(Modifier.height(8.dp))
            if (vm.cards.isEmpty()) Text("No cards yet.", color = Color.Gray)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.cards) { i, c ->
                    OutlinedCard(onClick = { editing = i }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium)
                            listOf(c.designation, c.organisation, c.phone, c.email, c.website, c.address)
                                .filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }

    if (settings) SettingsDialog(vm) { settings = false }
    editing?.let { i ->
        if (i < vm.cards.size) EditDialog(vm.cards[i], onSave = { vm.update(i, it); editing = null },
            onDelete = { vm.remove(i); editing = null }, onClose = { editing = null })
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all cards?") },
        confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable
fun SettingsDialog(vm: Vm, onClose: () -> Unit) {
    var u by remember { mutableStateOf(vm.sheetUrl) }
    var sec by remember { mutableStateOf(vm.sheetSecret) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cards are read on this phone. Free, no internet or key needed.")
                Text("Optional Google Sheet: paste the Apps Script Web app URL and the secret word. New cards are then added to your sheet automatically.")
                OutlinedTextField(value = u, onValueChange = { u = it }, singleLine = true, label = { Text("Web app URL") })
                OutlinedTextField(value = sec, onValueChange = { sec = it }, singleLine = true, label = { Text("Secret word") })
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveSettings(u, sec); onClose() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun EditDialog(c: Contact, onSave: (Contact) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val vals = remember { mutableStateListOf(*c.values().toTypedArray()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit card") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Contact.LABELS.forEachIndexed { i, l ->
                    OutlinedTextField(value = vals[i], onValueChange = { vals[i] = it }, label = { Text(l) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Contact.of(vals.toList())) }) { Text("Save") } },
        dismissButton = { Row { TextButton(onClick = onDelete) { Text("Delete") }; TextButton(onClick = onClose) { Text("Cancel") } } })
}

@Composable
fun CameraScreen(vm: Vm, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var pend by remember { mutableStateOf<ByteArray?>(null) }
    var shots by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(if (vm.both) "Show the FRONT of the card and press Capture" else "Hold the card flat and press Capture") }

    fun onShot(b: ByteArray) {
        val p = pend
        if (vm.both && p == null) { pend = b; info = "Front captured. Now show the BACK and press Capture" }
        else {
            vm.enqueue(listOfNotNull(p, b)); pend = null; shots++
            info = "$shots card(s) captured. Reading in background. Next card: " + if (vm.both) "show the FRONT" else "press Capture"
        }
    }
    fun finish() { pend?.let { vm.enqueue(listOf(it)) }; pend = null; onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
            PreviewView(c).also { pv ->
                val fut = ProcessCameraProvider.getInstance(c)
                fut.addListener({
                    val prev = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    val p = fut.get(); p.unbindAll()
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, prev, capture)
                }, ContextCompat.getMainExecutor(c))
            }
        })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC000000)).navigationBarsPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(info, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.both, onCheckedChange = { on ->
                    vm.both = on
                    if (!on) pend?.let { vm.enqueue(listOf(it)); pend = null; shots++ }
                    info = if (on) "Show the FRONT of the card and press Capture" else "One side only. Press Capture"
                })
                Spacer(Modifier.width(8.dp)); Text("Front + back", color = Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    capture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val rot = image.imageInfo.rotationDegrees
                            val bmp = image.toBitmap(); image.close()
                            onShot(Img.encode(bmp, rot))
                        }
                        override fun onError(e: ImageCaptureException) { info = "Capture failed, try again" }
                    })
                }) { Text("Capture") }
                if (pend != null) OutlinedButton(onClick = {
                    pend?.let { vm.enqueue(listOf(it)) }; pend = null; shots++
                    info = "Back skipped. Next card: show the FRONT"
                }) { Text("Skip back", color = Color.White) }
                OutlinedButton(onClick = { finish() }) { Text("Done", color = Color.White) }
            }
        }
    }
}
