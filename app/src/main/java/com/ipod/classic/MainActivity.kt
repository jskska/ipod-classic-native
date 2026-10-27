package com.ipod.classic

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.*
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media as M
import android.view.SoundEffectConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

data class Song(val id: Long, val title: String, val artist: String, val album: String, val albumId: Long, val uri: android.net.Uri)
data class Scr(val id: String, val arg: String = "")
data class It(val l: String, val r: String = "", val song: Song? = null, val act: () -> Unit = {})

val RU = mapOf(
    "Music" to "Музыка", "Playlists" to "Плейлисты", "Artists" to "Артисты", "Albums" to "Альбомы", "Songs" to "Песни",
    "Shuffle Songs" to "Случайно", "Extras" to "Дополнения", "Settings" to "Настройки", "Now Playing" to "Исполняется",
    "On-The-Go" to "На ходу", "Clear On-The-Go" to "Очистить «На ходу»", "Cleared" to "Очищено", "Clock" to "Часы", "Brick" to "Арканоид",
    "Shuffle" to "Перемешать", "Repeat" to "Повтор", "Vibration" to "Вибрация", "Language" to "Язык", "Off" to "Выкл", "On" to "Вкл",
    "One" to "Один", "All" to "Все", "Soft" to "Слабая", "Normal" to "Средняя", "Strong" to "Сильная", "Empty" to "Пусто",
    "Score" to "Счёт", "You win!" to "Победа!", "Game over" to "Игра окончена", "Select to start" to "Центр: старт",
    "Added to On-The-Go" to "Добавлено в «На ходу»", "Unknown" to "Неизвестно", "Volume" to "Громкость", "Scrub" to "Перемотка"
)

class Brick(val onHit: () -> Unit, val onLose: () -> Unit) {
    var x by mutableFloatStateOf(100f)
    var f by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)
    var run by mutableStateOf(false)
    var over by mutableIntStateOf(0)
    private var px = 120f; private var py = 140f; private var vx = 2.2f; private var vy = -2.2f
    private val b = BooleanArray(32) { true }
    private fun reset() { x = 100f; px = 120f; py = 140f; vx = 2.2f; vy = -2.2f; score = 0; over = 0; run = false; b.fill(true) }
    fun press() { if (over != 0) reset(); run = !run }
    fun tick() {
        if (run) {
            px += vx; py += vy
            if (px < 4 || px > 236) vx = -vx
            if (py < 4) vy = -vy
            if (py in 160f..170f && px > x - 4 && px < x + 44 && vy > 0) { vy = -vy; vx = (px - x - 20) / 8 }
            val i = (py / 14).toInt() * 8 + (px / 30).toInt()
            if (py in 0f..55.9f && i in 0..31 && b[i]) {
                b[i] = false; vy = -vy; score += 10; onHit()
                if (b.none { it }) { over = 1; run = false }
            }
            if (py > 182) { run = false; over = 2; onLose() }
        }
        f++
    }
    fun draw(d: DrawScope) = with(d) {
        val s = minOf(size.width / 240f, size.height / 180f)
        withTransform({ translate((size.width - 240f * s) / 2f, 0f); scale(s, s, Offset.Zero) }) {
            b.forEachIndexed { i, v ->
                if (v) drawRect(Color.hsv((i / 8) * 50f, .8f, .9f), Offset((i % 8) * 30f + 1f, (i / 8) * 14f + 1f), androidx.compose.ui.geometry.Size(28f, 12f))
            }
            drawRect(Color.White, Offset(x, 168f), androidx.compose.ui.geometry.Size(40f, 6f))
            drawCircle(Color.White, 4f, Offset(px, py))
        }
    }
}

class Ipod(val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("ipod", 0)
    var lang by mutableIntStateOf(prefs.getInt("lang", 0))
    var vib by mutableIntStateOf(prefs.getInt("vib", 2))
    var shuffle by mutableStateOf(prefs.getBoolean("shuf", false))
    var repeat by mutableIntStateOf(prefs.getInt("rep", 0))
    var songs by mutableStateOf(listOf<Song>())
    val stack = mutableStateListOf(Scr("main"))
    val sels = mutableStateListOf(0)
    var queue = listOf<Song>()
    var cur by mutableStateOf<Song?>(null)
    var playing by mutableStateOf(false)
    var pos by mutableIntStateOf(0)
    var dur by mutableIntStateOf(1)
    var scrub by mutableStateOf(false)
    var vol by mutableFloatStateOf(0.7f)
    var toast by mutableStateOf("")
    var now by mutableLongStateOf(0L)
    val otg = mutableStateListOf<Long>().apply { addAll(prefs.getString("otg", "")!!.split(",").mapNotNull { it.toLongOrNull() }) }
    val brick = Brick({ buzz("click") }, { buzz("heavy") })
    val mp = MediaPlayer().apply { setOnCompletionListener { ended() } }
    private val vr = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val artCache = HashMap<Long, ImageBitmap?>()
    val top: Scr get() = stack.last()

    fun tr(s: String): String = (if (lang == 1) RU[s] else null) ?: s
    fun save() { prefs.edit().putInt("lang", lang).putInt("vib", vib).putBoolean("shuf", shuffle).putInt("rep", repeat).putString("otg", otg.joinToString(",")).apply() }
    fun say(m: String) { toast = tr(m); Handler(Looper.getMainLooper()).postDelayed({ toast = "" }, 1200) }
    fun release() { mp.release() }

    /** Haptics for linear motors: composition primitives, falling back to predefined effects */
    fun buzz(k: String = "click") {
        if (vib == 0 || !vr.hasVibrator()) return
        val g = floatArrayOf(0f, .5f, .8f, 1f)[vib]
        val e: VibrationEffect =
            if (Build.VERSION.SDK_INT >= 30 && vr.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_THUD)) {
                val c = VibrationEffect.startComposition()
                when (k) {
                    "tick" -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, min(1f, g * .9f))
                    "heavy" -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, g)
                    "double" -> { c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, g); c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, g, 60) }
                    else -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, g)
                }
                c.compose()
            } else VibrationEffect.createPredefined(when (k) { "tick" -> VibrationEffect.EFFECT_TICK; "heavy" -> VibrationEffect.EFFECT_HEAVY_CLICK; else -> VibrationEffect.EFFECT_CLICK })
        vr.vibrate(e)
    }

    fun load() {
        val l = mutableListOf<Song>()
        ctx.contentResolver.query(M.EXTERNAL_CONTENT_URI, arrayOf(M._ID, M.TITLE, M.ARTIST, M.ALBUM, M.ALBUM_ID),
            "${M.IS_MUSIC}!=0 AND ${M.DURATION}>30000", null, "${M.TITLE} COLLATE NOCASE")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                l += Song(id, c.getString(1) ?: "", c.getString(2)?.takeIf { it != "<unknown>" } ?: "", c.getString(3) ?: "", c.getLong(4), ContentUris.withAppendedId(M.EXTERNAL_CONTENT_URI, id))
            }
        }
        songs = l
    }

    fun art(albumId: Long): ImageBitmap? = artCache.getOrPut(albumId) {
        try { ctx.contentResolver.loadThumbnail(ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId), android.util.Size(320, 320), null).asImageBitmap() } catch (e: Exception) { null }
    }

    // ---- playback
    fun play(q: List<Song>, s: Song) {
        queue = q; cur = s
        try { mp.reset(); mp.setDataSource(ctx, s.uri); mp.prepare(); mp.setVolume(vol, vol); mp.start(); dur = mp.duration; pos = 0; playing = true; scrub = false } catch (e: Exception) { playing = false }
    }
    fun next(d: Int) {
        if (queue.isEmpty()) return
        val i = queue.indexOf(cur)
        val n = if (shuffle && d > 0 && queue.size > 1) queue.filter { it != cur }.random() else queue[(i + d + queue.size) % queue.size]
        play(queue, n)
    }
    private fun ended() { if (repeat == 1) cur?.let { play(queue, it) } else if (repeat == 2 || shuffle || queue.indexOf(cur) < queue.size - 1) next(1) else playing = false }
    fun toggle() {
        buzz()
        if (cur == null) { if (songs.isNotEmpty()) play(songs, songs[0]); return }
        if (playing) mp.pause() else mp.start()
        playing = !playing
    }
    fun skip(d: Int) { buzz(); next(d) }
    fun prev() { buzz(); if (mp.currentPosition > 3000) mp.seekTo(0) else next(-1) }

    // ---- navigation / input
    fun push(id: String, arg: String = "") { stack.add(Scr(id, arg)); sels.add(0) }
    fun back() { if (stack.size > 1) { stack.removeAt(stack.lastIndex); sels.removeAt(sels.lastIndex) } }
    fun menu() { buzz(); back() }
    fun albums() = songs.distinctBy { it.albumId }

    fun step(d: Int) {
        buzz("tick")
        when (top.id) {
            "np" -> if (scrub) mp.seekTo((mp.currentPosition + d * 5000).coerceIn(0, dur)) else { vol = (vol + d * .05f).coerceIn(0f, 1f); mp.setVolume(vol, vol) }
            "brick" -> brick.x = (brick.x + d * 14f).coerceIn(0f, 200f)
            "clock" -> {}
            else -> { val n = if (top.id == "cf") albums().size else items(top).size; sels[sels.lastIndex] = (sels.last() + d).coerceIn(0, maxOf(0, n - 1)) }
        }
    }
    fun select() {
        buzz()
        when (top.id) {
            "np" -> scrub = !scrub
            "brick" -> brick.press()
            "clock" -> {}
            "cf" -> albums().getOrNull(sels.last())?.let { a -> val l = songs.filter { it.albumId == a.albumId }; play(l, l[0]); push("np") }
            else -> items(top).getOrNull(sels.last())?.let { it.act() }
        }
    }
    fun hold() {
        val s = if (top.id == "list") items(top).getOrNull(sels.last())?.song else cur
        if (s != null) { if (s.id !in otg) otg.add(s.id); save(); buzz("heavy"); say("Added to On-The-Go") }
    }

    // ---- menus
    private fun songIts(l: List<Song>) = l.map { s -> It(s.title, if (s == cur) "♪" else "", s) { play(l, s); push("np") } }
    fun items(s: Scr): List<It> = when (s.id) {
        "main" -> buildList {
            add(It("Music", ">") { push("music") })
            add(It("Shuffle Songs") { if (songs.isNotEmpty()) { shuffle = true; save(); play(songs, songs.random()); push("np") } })
            add(It("Extras", ">") { push("extras") })
            add(It("Settings", ">") { push("settings") })
            if (cur != null) add(It("Now Playing", ">") { push("np") })
        }
        "music" -> listOf(It("Cover Flow", ">") { push("cf") }, It("Playlists", ">") { push("pl") }, It("Artists", ">") { push("artists") }, It("Albums", ">") { push("albums") }, It("Songs", ">") { push("list", "all") })
        "pl" -> listOf(It("On-The-Go", ">") { push("list", "otg") }, It("Clear On-The-Go") { otg.clear(); save(); say("Cleared") })
        "artists" -> songs.map { it.artist.ifEmpty { "Unknown" } }.distinct().sorted().map { a -> It(a, ">") { push("list", "a:$a") } }
        "albums" -> albums().sortedBy { it.album }.map { a -> It(a.album.ifEmpty { "Unknown" }, ">") { push("list", "b:${a.albumId}") } }
        "list" -> songIts(when {
            s.arg == "all" -> songs.sortedBy { it.title.lowercase() }
            s.arg == "otg" -> otg.mapNotNull { id -> songs.find { it.id == id } }
            s.arg.startsWith("a:") -> songs.filter { it.artist.ifEmpty { "Unknown" } == s.arg.drop(2) }
            else -> songs.filter { it.albumId.toString() == s.arg.drop(2) }
        })
        "extras" -> listOf(It("Clock", ">") { push("clock") }, It("Brick", ">") { push("brick") })
        "settings" -> listOf(
            It("Shuffle", if (shuffle) "On" else "Off") { shuffle = !shuffle; save() },
            It("Repeat", listOf("Off", "One", "All")[repeat]) { repeat = (repeat + 1) % 3; save() },
            It("Vibration", listOf("Off", "Soft", "Normal", "Strong")[vib]) { vib = (vib + 1) % 4; save(); buzz("double") },
            It("Language", listOf("English", "Русский")[lang]) { lang = (lang + 1) % 2; save() })
        else -> listOf()
    }
    fun title(s: Scr) = tr(when (s.id) {
        "main" -> "iPod"; "music" -> "Music"; "pl" -> "Playlists"; "artists" -> "Artists"; "albums" -> "Albums"; "extras" -> "Extras"
        "settings" -> "Settings"; "np" -> "Now Playing"; "cf" -> "Cover Flow"; "clock" -> "Clock"; "brick" -> "Brick"
        else -> when {
            s.arg == "all" -> "Songs"; s.arg == "otg" -> "On-The-Go"; s.arg.startsWith("a:") -> s.arg.drop(2)
            else -> songs.find { it.albumId.toString() == s.arg.drop(2) }?.album ?: ""
        }
    })
}

class MainActivity : ComponentActivity() {
    private lateinit var ip: Ipod
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ip = Ipod(this)
        setContent {
            val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            val l = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) ip.load() }
            LaunchedEffect(Unit) { if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) ip.load() else l.launch(perm) }
            IPodUi(ip)
        }
    }
    override fun onDestroy() { super.onDestroy(); ip.release() }
}

@Composable
fun IPodUi(ip: Ipod) {
    LaunchedEffect(Unit) { while (true) { if (ip.playing) try { ip.pos = ip.mp.currentPosition } catch (e: Exception) {}; ip.now = System.currentTimeMillis(); delay(100) } }
    BackHandler(ip.stack.size > 1) { ip.back() }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF505560), Color(0xFF1B1C20), Color(0xFF09090B)))), Alignment.Center) {
        Column(
            Modifier.systemBarsPadding().padding(12.dp).widthIn(max = 380.dp).fillMaxWidth().aspectRatio(.6f)
                .shadow(24.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFBFBFC), Color(0xFFD4D5D9), Color(0xFFF2F2F4), Color(0xFFBCBDC2))))
                .metal().border(3.dp, Brush.linearGradient(listOf(Color(0xFFD8D9DC), Color(0xFF8D8E93), Color(0xFFEDEDEF), Color(0xFF6E7076))), RoundedCornerShape(28.dp))
                .padding(3.dp).border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(25.dp)).padding(horizontal = 19.dp, vertical = 21.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(4 / 3f).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF1D1D1F)))).border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)).padding(6.dp)) {
                Column(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(Color.White)) {
                    Row(
                        Modifier.fillMaxWidth().height(24.dp).background(Brush.verticalGradient(0f to Color(0xFFFAFBFC), .48f to Color(0xFFDFE5EC), .52f to Color(0xFFB3BEC9), 1f to Color(0xFFCDD6DF))).drawBehind { drawRect(Color(0xFF7B8794), Offset(0f, size.height - 1f), GSize(size.width, 1f)) }.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (ip.playing) "▶" else "", fontSize = 11.sp, color = Color.Black)
                        Text(ip.toast.ifEmpty { ip.title(ip.top) }, Modifier.weight(1f), color = Color.Black, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("▮▮▮", fontSize = 9.sp, color = Color.Black)
                    }
                    AnimatedContent(
                        Pair(ip.stack.size, ip.top), Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (targetState.first >= initialState.first)
                                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)))
                            else
                                (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)))
                        }, label = "nav"
                    ) { (depth, scr) -> ScreenBody(ip, depth, scr) }
                }
                Box(Modifier.matchParentSize().padding(6.dp).glass())
            }
            Spacer(Modifier.weight(1f))
            Wheel(ip, Modifier.fillMaxWidth(.8f))
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ScreenBody(ip: Ipod, depth: Int, s: Scr) {
    val sel = ip.sels.getOrElse(depth - 1) { 0 }
    when (s.id) {
        "np" -> NowPlaying(ip)
        "cf" -> CoverFlow(ip, sel)
        "clock" -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ip.now)), fontSize = 34.sp, fontWeight = FontWeight.Light, color = Color.Black) }
        "brick" -> BrickView(ip)
        else -> MenuList(ip, s, sel)
    }
}

@Composable
fun MenuList(ip: Ipod, s: Scr, sel: Int) {
    val its = ip.items(s).ifEmpty { listOf(It("Empty")) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val rowH = maxHeight / 7
        var off by remember { mutableIntStateOf(0) }
        LaunchedEffect(sel) { if (sel < off) off = sel else if (sel >= off + 7) off = sel - 6 }
        val scroll by animateDpAsState(rowH * off, spring(stiffness = 450f), label = "scroll")
        val hl by animateDpAsState(rowH * sel, spring(stiffness = 600f), label = "hl")
        Box(Modifier.offset(y = hl - scroll).fillMaxWidth().height(rowH).background(Brush.verticalGradient(0f to Color(0xFF8CC3FF), .48f to Color(0xFF4A90E2), .52f to Color(0xFF1766C8), 1f to Color(0xFF2A7AE0))).drawBehind { drawRect(Color(0x66FFFFFF), size = GSize(size.width, 1.5f)) })
        Column(Modifier.offset(y = -scroll)) {
            its.forEachIndexed { i, x ->
                val c by animateColorAsState(if (i == sel) Color.White else Color.Black, tween(150), label = "c")
                Row(Modifier.fillMaxWidth().height(rowH).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ip.tr(x.l), Modifier.weight(1f), color = c, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ip.tr(x.r), color = c, fontSize = 13.sp)
                }
            }
        }
    }
}

fun fmt(ms: Int) = "${ms / 60000}:${(ms / 1000 % 60).toString().padStart(2, '0')}"

@Composable
fun Bar(p: Float) {
    val a by animateFloatAsState(p, tween(250, easing = LinearEasing), label = "bar")
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE2E2E2)).border(1.dp, Color.Gray, RoundedCornerShape(5.dp))) {
        Box(Modifier.fillMaxWidth(a.coerceIn(0f, 1f)).fillMaxHeight().background(Brush.verticalGradient(listOf(Color(0xFF78B7FF), Color(0xFF1B6FD6)))))
    }
}

@Composable
fun Cover(ip: Ipod, id: Long, mod: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, id) { value = withContext(Dispatchers.IO) { ip.art(id) } }
    val sc by animateFloatAsState(if (bmp != null) 1f else .8f, spring(Spring.DampingRatioMediumBouncy), label = "pop")
    Box(mod.graphicsLayer { scaleX = sc; scaleY = sc }.background(Color(0xFF556070)), Alignment.Center) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("♫", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
fun NowPlaying(ip: Ipod) {
    val s = ip.cur
    if (s == null) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(ip.tr("Empty"), color = Color.Black) }; return }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(ip, s.albumId, Modifier.size(70.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(s.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, color = Color.Black)
                Text(s.artist.ifEmpty { ip.tr("Unknown") }, fontSize = 12.sp, color = Color.DarkGray, maxLines = 1)
                Text(s.album, fontSize = 12.sp, color = Color.DarkGray, maxLines = 1)
            }
        }
        Column {
            Bar(ip.pos.toFloat() / ip.dur.coerceAtLeast(1))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(fmt(ip.pos), fontSize = 11.sp, color = Color.Black); Text("-" + fmt(ip.dur - ip.pos), fontSize = 11.sp, color = Color.Black) }
        }
        Column {
            Text(ip.tr(if (ip.scrub) "Scrub" else "Volume"), fontSize = 11.sp, color = Color.DarkGray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Bar(ip.vol)
        }
    }
}

@Composable
fun CoverFlow(ip: Ipod, sel: Int) {
    val al = ip.albums()
    if (al.isEmpty()) { Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) { Text(ip.tr("Empty"), color = Color.White) }; return }
    val i = sel.coerceIn(0, al.size - 1)
    val pos by animateFloatAsState(i.toFloat(), spring(dampingRatio = .75f, stiffness = 120f), label = "cf")
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black, Color(0xFF2B2B2B), Color.Black)))) {
        val sz = maxHeight * .42f
        al.forEachIndexed { k, a ->
            val d = k - pos
            if (abs(d) < 4.5f) Column(Modifier.align(Alignment.Center).offset(y = -maxHeight * .1f).zIndex(10f - abs(d)).graphicsLayer {
                val ad = min(abs(d), 1f); val side = sign(d)
                translationX = side * (ad * sz.toPx() * .8f + (abs(d) - ad) * sz.toPx() * .4f)
                rotationY = -side * ad * 62f
                cameraDistance = 12f * density
                scaleX = 1f + .1f * (1f - ad); scaleY = scaleX
                alpha = (1f - max(abs(d) - 3f, 0f)).coerceIn(0f, 1f)
            }) { Cover(ip, a.albumId, Modifier.size(sz)); Reflect(ip, a.albumId, sz) }
        }
        Text(al[i].album + "\n" + al[i].artist, Modifier.align(Alignment.BottomCenter).padding(4.dp), color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun BrickView(ip: Ipod) {
    val b = ip.brick
    LaunchedEffect(Unit) { while (true) { withFrameNanos { }; b.tick() } }
    Box(Modifier.fillMaxSize().background(Color(0xFF111111)), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { b.f; b.draw(this) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (b.over != 0) Text(ip.tr(if (b.over == 1) "You win!" else "Game over"), color = Color.White, fontWeight = FontWeight.Bold)
            if (!b.run) Text(ip.tr("Select to start"), color = Color.White, fontSize = 12.sp)
        }
        Text("${ip.tr("Score")} ${b.score}", Modifier.align(Alignment.BottomStart).padding(4.dp), color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun Wheel(ip: Ipod, modifier: Modifier) {
    val view = LocalView.current
    val lab = Color(0xFF8A8B90)
    Box(
        modifier.aspectRatio(1f).shadow(6.dp, CircleShape).clip(CircleShape)
            .background(Brush.sweepGradient(listOf(Color(0xFFF4F4F6), Color(0xFFD5D6DA), Color(0xFFFAFAFB), Color(0xFFCBCCD1), Color(0xFFF4F4F6), Color(0xFFD5D6DA), Color(0xFFFAFAFB), Color(0xFFCBCCD1), Color(0xFFF4F4F6))))
            .drawBehind {
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(.52f to Color.Transparent, 1f to Color(0x2A000000), center = center, radius = r))
                drawCircle(Color(0xA0FFFFFF), r - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
                drawCircle(Color(0x28000000), r * .62f, style = Stroke(1.dp.toPx()))
            }
            .border(1.dp, Color(0xFF9B9CA1), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val dx = o.x - size.width / 2f; val dy = o.y - size.height / 2f
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    if (abs(dx) > abs(dy)) { if (dx > 0) ip.skip(1) else ip.prev() } else if (dy < 0) ip.menu() else ip.toggle()
                }
            }
            .pointerInput(Unit) {
                var last = 0f; var acc = 0f
                fun ang(o: Offset) = atan2(o.y - size.height / 2f, o.x - size.width / 2f) * 180f / PI.toFloat()
                detectDragGestures(onDragStart = { last = ang(it); acc = 0f }) { ch, _ ->
                    val a = ang(ch.position); var d = a - last
                    if (d > 180) d -= 360; if (d < -180) d += 360
                    last = a; acc += d
                    while (acc >= 22) { acc -= 22; ip.step(1) }
                    while (acc <= -22) { acc += 22; ip.step(-1) }
                }
            }
    ) {
        Text("MENU", Modifier.align(Alignment.TopCenter).padding(top = 14.dp), color = lab, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("◀◀", Modifier.align(Alignment.CenterStart).padding(start = 16.dp), color = lab, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("▶▶", Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), color = lab, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("▶❚❚", Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), color = lab, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        var pressed by remember { mutableStateOf(false) }
        val sc by animateFloatAsState(if (pressed) .93f else 1f, tween(80), label = "press")
        Box(
            Modifier.align(Alignment.Center).fillMaxSize(.34f).graphicsLayer { scaleX = sc; scaleY = sc }.drawBehind { val r = size.minDimension / 2; drawCircle(Color(0xFF9D9EA3), r + 5.dp.toPx()); drawCircle(Color(0xFFE7E7EA), r + 4.dp.toPx()) }.clip(CircleShape)
                .drawBehind { drawCircle(Brush.radialGradient(0f to Color.White, .45f to Color(0xFFECECEF), 1f to Color(0xFFC9CACF), center = Offset(size.width * .38f, size.height * .28f), radius = size.width * .8f)) }
                .border(1.dp, Color(0xFFA1A2A7), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onLongPress = { ip.hold() }, onTap = { view.playSoundEffect(SoundEffectConstants.CLICK); ip.select() })
                }
        )
    }
}

/** Brushed aluminium: fine vertical grain + speckle noise */
fun Modifier.metal(): Modifier = drawWithCache {
    val rnd = java.util.Random(7)
    val pts = List(900) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) to rnd.nextFloat() }
    onDrawBehind {
        var x = 0f
        while (x < size.width) { drawLine(Color(0x0D000000), Offset(x, 0f), Offset(x, size.height), 1f); x += 3f }
        pts.forEach { (o, a) -> drawCircle(Color.Black.copy(alpha = .06f * a), 1f, o) }
    }
}

/** LCD glass: diagonal glare, scanline grid, inner edge shadow */
fun Modifier.glass(): Modifier = drawBehind {
    drawRect(Brush.linearGradient(0f to Color(0x44FFFFFF), .36f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height)))
    var y = 0f
    while (y < size.height) { drawRect(Color(0x0A000000), Offset(0f, y), GSize(size.width, 1f)); y += 3f }
    drawRect(Color(0x33000000), style = Stroke(3f))
}

/** Mirror reflection under a Cover Flow cover, fading out downwards */
@Composable
fun Reflect(ip: Ipod, id: Long, sz: Dp) {
    val bmp by produceState<ImageBitmap?>(null, id) { value = withContext(Dispatchers.IO) { ip.art(id) } }
    Box(
        Modifier.padding(top = 2.dp).size(sz, sz * .5f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.clipToBounds()
            .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .5f), Color.Transparent)), blendMode = BlendMode.DstIn) }
    ) {
        val m = Modifier.wrapContentHeight(Alignment.Top, unbounded = true).fillMaxWidth().height(sz).graphicsLayer { scaleY = -1f }
        val b = bmp
        if (b != null) Image(b, null, m, contentScale = ContentScale.Crop) else Box(m.background(Color(0xFF556070)))
    }
}
