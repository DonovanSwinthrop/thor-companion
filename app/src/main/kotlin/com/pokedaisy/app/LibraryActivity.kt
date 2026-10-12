package com.pokedaisy.app

import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.DpSize
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image as CoverImage
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import com.pokedaisy.app.companion.ui.LOGO_PALETTE
import com.pokedaisy.app.companion.ui.LOGO_ROWS
import com.pokedaisy.app.companion.ui.PixelArt
import com.pokedaisy.app.companion.ui.AppBackdrop
import com.pokedaisy.app.companion.ui.BackdropText
import com.pokedaisy.app.companion.ui.GbaText
import com.pokedaisy.app.companion.ui.GbaTextMetrics
import com.pokedaisy.app.companion.ui.OptionBadge
import com.pokedaisy.app.companion.ui.OptionButton
import com.pokedaisy.app.companion.ui.OptionColors
import com.pokedaisy.app.companion.ui.OptionConfirm
import com.pokedaisy.app.companion.ui.OptionLine
import com.pokedaisy.app.companion.ui.OptionListWindow
import com.pokedaisy.app.companion.ui.OptionOverlay
import com.pokedaisy.app.companion.ui.OptionSelector
import com.pokedaisy.app.companion.ui.OptionTextField
import com.pokedaisy.app.companion.ui.OptionTitleWindow
import com.pokedaisy.app.companion.ui.drawLayeredFrame
import com.pokedaisy.app.companion.ui.rememberGbaTextMetrics
import com.pokedaisy.app.companion.ui.theme.QolTheme
import com.pokedaisy.app.companion.ui.theme.gbaFocusRing
import java.io.File
import com.pokedaisy.app.companion.ui.PixelRoundedShape

/** Square ratio matching SteamGridDB's icons (not its portrait "grid" box
 * art — those looked wrong stretched/cropped into a list row, and icons are
 * both simpler to fetch and a better fit for a compact list/grid of ROMs
 * anyway) — every cover, fetched or manually picked, is normalized to this. */
private const val ICON_ASPECT = 1f

/**
 * Launcher screen: pick a ROM to play, or import one. ROMs live in
 * `Android/data/<pkg>/files/roms/`; a save + savestates folder is keyed per ROM
 * by CRC32, so importing/removing ROMs never disturbs progress.
 */
class LibraryActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var romsDir: File
    private lateinit var coversDir: File

    private val importRom = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { startImport(it, play = false) }
    }

    /** LOAD SAVE: the save file picked for a game, waiting on its confirm (internal for ui-preview). */
    internal class PendingSaveLoad(val rom: File, val bytes: ByteArray, val fileName: String)
    internal var saveLoad by mutableStateOf<PendingSaveLoad?>(null)
    private var saveTarget: File? = null
    /** RESTORE BACKUP: [rom]'s save backups to pick one from, newest first. */
    internal class BackupPick(val rom: File, val files: List<File>)
    internal var backupPick by mutableStateOf<BackupPick?>(null)
    private val pickSaveFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val rom = saveTarget
        saveTarget = null
        if (uri != null && rom != null) readPickedSave(rom, uri)
    }

    /** The GitHub update check / offer (internal for the ui-preview harness). */
    internal val updates by lazy { AppUpdateFlow(this) }

    /** INFO's modal while non-null (internal for the ui-preview harness). */
    internal var gameInfo by mutableStateOf<GameInfo?>(null)

    /** First-time setup over the library while non-null (internal for the ui-preview harness). */
    internal var setup by mutableStateOf<SetupState?>(null)

    /** The one-time "send crash reports?" ask (CrashReports.shouldAsk) is up. Internal for ui-preview. */
    internal var crashAsk by mutableStateOf(false)
    private val pickRomsFolder = registerForActivityResult(StorageAccess.PickFolder()) { uri ->
        pickedFolder(uri)?.let(::linkRomsFolder)
    }
    private val pickSavesFolder = registerForActivityResult(StorageAccess.PickFolder()) { uri ->
        pickedFolder(uri)?.let(::useSavesFolder)
    }

    /** SAVE FOLDER's game, while its folder picker is up. */
    private var saveFolderTarget: File? = null
    private val pickGameSaveFolder = registerForActivityResult(StorageAccess.PickFolder()) { uri ->
        val rom = saveFolderTarget ?: return@registerForActivityResult
        saveFolderTarget = null
        pickedFolder(uri)?.let { setGameSaveFolder(rom, it) }
    }

    /** A copied ROM the second screen can't read, waiting on the "add anyway?"
     * confirm - still in [importDir], not the library. Internal for the
     * ui-preview harness. */
    internal class PendingImport(val tmp: File, val name: String, val sourcePath: String?, val play: Boolean)
    internal var pendingImport by mutableStateOf<PendingImport?>(null)
    private lateinit var importDir: File

    // "Set Cover" picker — GetContent (not OpenDocument) since any image source
    // (gallery, Files, a saved screenshot of a fan cover) should work, and this
    // is a one-shot read (copied into our own cover file immediately), not a
    // persistent content:// reference that needs OpenDocument's URI permission.
    private var pendingCoverTarget: File? = null
    private val pickCoverImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val rom = pendingCoverTarget
        pendingCoverTarget = null
        if (uri != null && rom != null) setManualCover(rom, uri)
        bump()
    }

    private var revision by mutableStateOf(0)
    private fun bump() {
        unsupportedRoms = RomFolder.unsupported(this)
        partialRoms = RomFolder.partial(this)
        revision++
    }

    /** ROMs the second screen can't read, per [RomFolder]'s cache: their NOT SUPPORTED tag (internal for ui-preview). */
    internal var unsupportedRoms by mutableStateOf(emptySet<String>())

    /** ROMs read through a PARTIAL best-effort match: their PARTIALLY SUPPORTED tag (internal for ui-preview). */
    internal var partialRoms by mutableStateOf(emptySet<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RomFolder.useBestEffortStore(this)
        BestEffortShare.flush(this)   // a shared best-effort match that couldn't go out last time
        prefs = Prefs(this)
        // No ROM here: AUTO = the device's language.
        com.pokedaisy.app.companion.i18n.L10n.apply(prefs.appLanguage, null)
        // Signs the saved RetroAchievements account in (INFO shows its progress per game).
        com.pokedaisy.app.achievements.RetroAchievements.init(this)
        romsDir = File(getExternalFilesDir(null), "roms").apply { mkdirs() }
        coversDir = File(getExternalFilesDir(null), "covers").apply { mkdirs() }
        // Next to roms/ (same volume, so accepting an import is a rename).
        importDir = File(getExternalFilesDir(null), "import").apply { mkdirs() }

        // If launched via VIEW (open a .gba with the app), import + play it.
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { startImport(it, play = true) }
        } else if (LaunchActivity.romUri(intent) != null) {
            // A frontend that only knows the app (its launcher intent) plus a ROM:
            // straight to the game, as if it had started LaunchActivity itself.
            startActivity(
                Intent(intent).setClass(this, LaunchActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            finish()
            return
        }

        if (intent?.action != Intent.ACTION_VIEW && !prefs.setupDone && listRoms().isEmpty()) startSetup()
        // EXPAND's introduction, once: to everyone not already on it (first-time setup goes first).
        expandIntro = !prefs.expandIntroSeen && prefs.gameAspect != com.pokedaisy.app.companion.GameAspect.WIDE

        setContent {
            QolTheme {
                val s = setup
                if (s == null) LibraryScreen() else SetupHost(s)
            }
        }

        // Once ever, from the third open on and two days after the install: may we send crash reports?
        if (savedInstanceState == null && setup == null && CrashReports.shouldAsk(this, prefs)) crashAsk = true

        // Every app open looks for a newer GitHub release. Not in debug builds: a
        // release APK can't install over a debug-signed one (Settings > VERSION still checks).
        if (savedInstanceState == null && !BuildConfig.DEBUG) updates.check()
    }

    override fun onResume() {
        super.onResume()
        com.pokedaisy.app.companion.ui.OptionColors.inGame = false
        com.pokedaisy.app.companion.i18n.L10n.apply(prefs.appLanguage, null)
        unsupportedRoms = RomFolder.unsupported(this)
        partialRoms = RomFolder.partial(this)
        if (prefs.setupRequested) {
            prefs.setupRequested = false
            startSetup()
            setup?.fromSettings = true
        }
        setup?.let { s ->
            // Back from the rebind page: show what's bound now.
            if (s.step == SetupState.Step.BUTTONS) readButtons(s)
            // Back from Android's All files access page: carry on with the folder pick it was for.
            s.hasAccess = StorageAccess.hasAllFilesAccess(this)
            val waiting = s.awaitingAccess
            s.awaitingAccess = null
            if (s.hasAccess) when (waiting) {
                SetupState.Step.ROMS -> pickRomsFolder.launch(null)
                SetupState.Step.SAVES -> pickSavesFolder.launch(null)
                else -> {}
            }
        }
        rescanRomsFolder()
        identifyGames()
        updates.onResume()
        // The manual (Settings > HELP), fetched in the background so it's there offline later.
        if (com.pokedaisy.app.companion.ui.DocsSources.override == null) DocsStore.prefetch(this)
        bump()   // refresh list / PLAYING marker after returning
    }

    @Composable
    private fun LibraryScreen() {
        @Suppress("UNUSED_EXPRESSION") revision
        val m = rememberGbaTextMetrics()
        val small = rememberGbaTextMetrics(textScale = 1f)
        var sort by remember { mutableStateOf(LibrarySort.of(prefs.librarySort)) }
        var pickingSort by remember { mutableStateOf(false) }
        val roms = remember(revision, sort) { sortLibrary(this@LibraryActivity, prefs, listRoms(), sort) }
        var renaming by remember { mutableStateOf<File?>(null) }
        var deleting by remember { mutableStateOf<File?>(null) }
        var gridView by remember { mutableStateOf(prefs.libraryViewMode == 1) }
        val recentRoms = remember(revision, roms) {
            prefs.recentRomPaths().mapNotNull { path -> roms.find { it.absolutePath == path } }.take(3)
        }

        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(modifier = Modifier.fillMaxSize().onSizeChanged { screenSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) } }) {
            AppBackdrop(logos = true)
            // The lists scroll to the screen's edges (their margin is padding inside them), so a card
            // slides off the screen rather than being cut at an inner box's edge.
            Column(modifier = Modifier.fillMaxSize()) {
                OptionTitleWindow("POKéDAISY", m, Modifier.padding(start = SCREEN_MARGIN, end = SCREEN_MARGIN, top = SCREEN_MARGIN), leading = {
                    PixelArt(LOGO_ROWS, LOGO_PALETTE, Modifier.size(m.lineHeight))
                }) {
                    // The header's first button: where UP from the top of the games lands.
                    HeaderIcon(Icons.AutoMirrored.Filled.Sort, tr("Sort"), m, Modifier.focusRequester(headerFirst)) { pickingSort = true }
                    HeaderIcon(if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, tr("Toggle view"), m) {
                        gridView = !gridView
                        prefs.libraryViewMode = if (gridView) 1 else 0
                    }
                    HeaderIcon(Icons.Filled.Add, tr("Import ROM"), m) { importRom.launch(arrayOf("*/*")) }
                    HeaderIcon(Icons.Filled.Refresh, tr("Refresh"), m) { refreshLibrary() }
                    // The manual (Settings > HELP), on its own.
                    HeaderIcon(Icons.AutoMirrored.Filled.HelpOutline, tr("Help"), m) {
                        startActivity(Intent(this@LibraryActivity, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SCREEN, SettingsActivity.SCREEN_HELP))
                    }
                    HeaderIcon(Icons.Filled.Settings, tr("Settings"), m) {
                        startActivity(Intent(this@LibraryActivity, SettingsActivity::class.java))
                    }
                }
                Spacer(Modifier.height(m.u * 4))

                if (roms.isEmpty()) {
                    OptionListWindow(m, Modifier.fillMaxWidth().padding(horizontal = SCREEN_MARGIN)) {
                        GbaText(
                            tr(
                                "No ROMs yet. Tap + to import a .gba (or a .zip / .7z), link your ROMs folder in SETTINGS > FOLDERS, or drop files into {0}",
                                "Android/data/$packageName/files/roms/",
                            ),
                            OptionColors.label, OptionColors.labelShadow, small, maxLines = Int.MAX_VALUE,
                            modifier = Modifier.padding(m.u * 4),
                        )
                    }
                } else if (gridView) {
                    RomGrid(
                        roms, recentRoms, m, small,
                        modifier = Modifier.weight(1f),
                        onPlayRecent = { play(it) },
                        onRename = { renaming = it },
                        onDelete = { deleting = it },
                    )
                } else {
                    RomList(
                        roms, recentRoms, m, small,
                        modifier = Modifier.weight(1f),
                        onPlayRecent = { play(it) },
                        onRename = { renaming = it },
                        onDelete = { deleting = it },
                    )
                }
            }

            if (pickingSort) {
                OptionSelector(
                    tr("SORT BY"), LibrarySort.entries.toList(), sort, { tr(it.label) }, m,
                    onPick = { sort = it; prefs.librarySort = it.id; pickingSort = false },
                    onDismiss = { pickingSort = false },
                )
            }

            renaming?.let { rom ->
                RenameDialog(
                    current = romLabel(rom),
                    m = m, small = small,
                    onDismiss = { renaming = null },
                    onConfirm = { name ->
                        prefs.setRomDisplayName(rom, name.takeIf { it != GameTitles.defaultLabel(this@LibraryActivity, rom) })
                        renaming = null
                        bump()
                    },
                )
            }

            coverPickerFor?.let { rom ->
                CoverPicker(
                    rom = rom,
                    displayName = romLabel(rom),
                    source = coverSourceOverride ?: LibraryCoverSource(prefs.steamGridDbApiKey, prefs.raWebApiKey)
                        .takeIf { prefs.steamGridDbApiKey != null || prefs.raWebApiKey != null },
                    saving = coverSaving,
                    error = coverError,
                    m = m, small = small,
                    onPick = { icon -> saveSteamGridCover(rom, icon) },
                    onDismiss = { if (!coverSaving) coverPickerFor = null },
                )
            }

            pendingImport?.let { p ->
                OptionConfirm(
                    title = tr("NOT SUPPORTED"),
                    message = tr(
                        "{0} isn't supported by the second screen: the game plays, but the companion can't show its party, map, items or battles. Add it anyway?",
                        p.name.substringBeforeLast('.'),
                    ),
                    confirmLabel = if (p.play) tr("PLAY") else tr("ADD"),
                    m = m,
                    onDismiss = { p.tmp.delete(); pendingImport = null },
                    onConfirm = { pendingImport = null; finishImport(p) },
                )
            }

            gameInfo?.let { info -> GameInfoDialog(
                    info, m, small, onDismiss = { gameInfo = null },
                    onOpenUrl = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                    onForgetBestEffort = { sha1 ->
                        com.pokedaisy.app.companion.data.BestEffortStore.forget(sha1)
                        gameInfo = null
                        rescanRomsFolder()
                    },
                    onPickGame = { gp, id ->
                        val store = com.pokedaisy.app.companion.data.BestEffortStore
                        when (id) {
                            GAME_AUTO -> store.forget(gp.sha1)
                            GAME_NONE -> store.decline(gp.sha1)
                            // Its ROM-side parts are checked when the game next runs (the companion's sampler).
                            else -> com.pokedaisy.app.companion.data.BestEffort.candidate(id)?.let { c ->
                                store.save(gp.sha1, com.pokedaisy.app.companion.data.BestEffort.Match(c, full = false, off = emptySet(), score = 0), com.pokedaisy.app.companion.data.BestEffortStore.Source.PICKED, verified = false)
                            }
                        }
                        gameInfo = null
                        rescanRomsFolder()
                    },
                ) }

            UpdateDialog(updates, m, small)

            // After any update offer, never over setup (this screen isn't composed then).
            if (crashAsk && updates.release == null) {
                // Shown = asked, whatever the answer (or none, if the app goes away): only once.
                LaunchedEffect(Unit) { prefs.crashReportsAsked = true }
                OptionConfirm(
                    title = tr("CRASH REPORTS?"),
                    message = tr(
                        "When PokéDaisy crashes, it can send a report so the bug gets fixed sooner: the device model, Android version and where the app crashed. Never your ROMs, saves or anything personal. You can change this any time in Settings.",
                    ),
                    confirmLabel = tr("SEND REPORTS"),
                    cancelLabel = tk("NO THANKS"),
                    m = m,
                    onDismiss = { crashAsk = false },
                    onConfirm = { crashAsk = false; CrashReports.setEnabled(prefs, true) },
                )
            }

            // After the crash-reports question, one at a time.
            if (expandIntro && setup == null && !crashAsk && updates.release == null) {
                com.pokedaisy.app.companion.ui.ExpandIntro(
                    m, small,
                    onTurnOn = {
                        prefs.gameAspect = com.pokedaisy.app.companion.GameAspect.WIDE
                        prefs.expandIntroSeen = true
                        expandIntro = false
                    },
                    onNotNow = { prefs.expandIntroSeen = true; expandIntro = false },
                )
            }

            backupPick?.let { b ->
                OptionSelector(
                    tr("RESTORE BACKUP"), b.files.indices.toList(), -1, { backupLabel(b.files[it]) }, m,
                    onPick = { backupPick = null; readBackup(b.rom, b.files[it]) },
                    onDismiss = { backupPick = null },
                )
            }

            saveLoad?.let { p ->
                val name = romLabel(p.rom)
                val current = remember(p) { SavesLocation.saveFor(this@LibraryActivity, prefs, p.rom) }
                val size = p.bytes.size.toLong()
                val what = "${p.fileName} (${GameInfo.sizeLabel(size)}${GameSaves.kind(size)?.let { " · $it" } ?: ""})"
                OptionConfirm(
                    title = tr("LOAD SAVE?"),
                    message = (if (current.isFile) {
                        tr(
                            "{0}: replace its save with {1}? The current save is kept, renamed to {2}.",
                            name, what, GameSaves.backupName(current),
                        )
                    } else {
                        tr("{0} has no save yet: use {1} as its save?", name, what)
                    }) + " " + tr("The game starts from this save next time, not from where you left off.") +
                        (if (GameSaves.kind(size) == null) " " + tr("This doesn't look like a GBA save file.") else ""),
                    confirmLabel = tr("LOAD"),
                    m = m,
                    onDismiss = { saveLoad = null },
                    onConfirm = { saveLoad = null; loadSave(p) },
                )
            }

            deleting?.let { rom ->
                OptionConfirm(
                    title = tr("DELETE ROM?"),
                    message = tr(
                        "{0}: removes the .{1} file. Saves and save states are kept.",
                        romLabel(rom), rom.extension,
                    ),
                    confirmLabel = tr("DELETE"),
                    m = m,
                    onDismiss = { deleting = null },
                    onConfirm = {
                        rom.delete()
                        RomAdded.forget(this@LibraryActivity, rom)
                        prefs.favoriteRoms = prefs.favoriteRoms - rom.absolutePath
                        prefs.setRomDisplayName(rom, null)
                        prefs.setRomSourcePath(rom, null)
                        prefs.setRomCoverManual(rom, false)
                        prefs.setRomCoverSource(rom, null)
                        coverFile(rom).delete()
                        if (prefs.lastRomPath == rom.absolutePath) prefs.lastRomPath = null
                        deleting = null
                        bump()
                    },
                )
            }
        }
    }

    @Composable
    private fun HeaderIcon(icon: ImageVector, label: String, m: GbaTextMetrics, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Spacer(Modifier.width(m.u * 4))
        OptionButton(null, m, onClick = onClick, modifier = modifier, icon = icon, contentDescription = label)
    }

    /** The header's leftmost button (sort). */
    private val headerFirst = FocusRequester()

    /** For a card in the games' top row (the RECENTLY PLAYED tiles, or the first row without them):
     * D-pad UP goes to the header's leftmost button. A lazy list's top row had nowhere to go - UP did nothing. */
    private val toHeader: () -> FocusRequester? = { headerFirst }
    private val noUp: () -> FocusRequester? = { null }

    @Composable
    private fun RomList(
        roms: List<File>,
        recentRoms: List<File>,
        m: GbaTextMetrics,
        small: GbaTextMetrics,
        modifier: Modifier = Modifier,
        onPlayRecent: (File) -> Unit,
        onRename: (File) -> Unit,
        onDelete: (File) -> Unit,
    ) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = SCREEN_MARGIN, end = SCREEN_MARGIN, bottom = SCREEN_MARGIN),
            verticalArrangement = Arrangement.spacedBy(m.u * 4),
        ) {
            if (recentRoms.isNotEmpty()) {
                item(key = "recent") {
                    RecentPlayedSection(recentRoms, gridView = false, m, small, onPlay = onPlayRecent)
                }
                item(key = "all") { BackdropText(tr("ALL GAMES"), small) }
            }
            itemsIndexed(roms, key = { _, rom -> rom.absolutePath }) { i, rom ->
                RomRow(rom, m, small, onRename = { onRename(rom) }, onDelete = { onDelete(rom) }, upTo = if (i == 0 && recentRoms.isEmpty()) toHeader else noUp)
            }
        }
    }

    @Composable
    private fun RomGrid(
        roms: List<File>,
        recentRoms: List<File>,
        m: GbaTextMetrics,
        small: GbaTextMetrics,
        modifier: Modifier = Modifier,
        onPlayRecent: (File) -> Unit,
        onRename: (File) -> Unit,
        onDelete: (File) -> Unit,
    ) {
        val grid = rememberLazyGridState()
        // Without RECENTLY PLAYED the games start at item 0; the top row is what the grid laid out as row 0.
        fun topRow(index: Int): () -> FocusRequester? = {
            headerFirst.takeIf { recentRoms.isEmpty() && grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.row == 0 }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            state = grid,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = SCREEN_MARGIN, end = SCREEN_MARGIN, bottom = SCREEN_MARGIN),
            horizontalArrangement = Arrangement.spacedBy(m.u * 4),
            verticalArrangement = Arrangement.spacedBy(m.u * 4),
        ) {
            if (recentRoms.isNotEmpty()) {
                item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                    RecentPlayedSection(recentRoms, gridView = true, m, small, onPlay = onPlayRecent)
                }
                item(key = "all", span = { GridItemSpan(maxLineSpan) }) { BackdropText(tr("ALL GAMES"), small) }
            }
            gridItemsIndexed(roms, key = { _, rom -> rom.absolutePath }) { i, rom ->
                RomTile(rom, m, small, onRename = { onRename(rom) }, onDelete = { onDelete(rom) }, upTo = topRow(i))
            }
        }
    }

    /** The "RECENTLY PLAYED" strip — now just the first item inside whichever
     * scrollable container (RomList's LazyColumn or RomGrid's
     * LazyVerticalGrid, full-span) is active, so it scrolls away with the
     * rest of the list instead of staying pinned above it. Its own row still
     * scrolls horizontally (a plain, non-lazy Row wrapped in
     * horizontalScroll — only ever up to 3 items, laziness would be
     * pointless) since 3 tiles/rows can easily be wider than the screen. */
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    private fun RecentPlayedSection(
        recentRoms: List<File>,
        gridView: Boolean,
        m: GbaTextMetrics,
        small: GbaTextMetrics,
        onPlay: (File) -> Unit,
    ) {
        // A tile focused with the D-pad brings the whole section into view, its title too (the list
        // alone scrolled just far enough for the tile, leaving RECENTLY PLAYED off the top).
        val section = remember { BringIntoViewRequester() }
        val scope = rememberCoroutineScope()
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = m.u * 4)
                .bringIntoViewRequester(section)
                .onFocusChanged { if (it.hasFocus) scope.launch { section.bringIntoView() } },
        ) {
            BackdropText(tr("RECENTLY PLAYED"), small, Modifier.padding(bottom = m.u * 3))
            // Out to the screen's edges, the margin inside the scroll: a tile slides off the side.
            Row(
                horizontalArrangement = Arrangement.spacedBy(m.u * 4),
                modifier = Modifier.bleedHorizontally(SCREEN_MARGIN).horizontalScroll(rememberScrollState())
                    .padding(horizontal = SCREEN_MARGIN),
            ) {
                recentRoms.forEach { rom ->
                    if (gridView) {
                        RecentTile(rom, m, small, onClick = { onPlay(rom) })
                    } else {
                        RecentListItem(rom, m, small, onClick = { onPlay(rom) })
                    }
                }
            }
        }
    }

    /** Shared cover-image box: the auto-captured or manually-set PNG if one
     * exists yet, else a placeholder (cover capture/import is
     * fire-and-forget, so a just-imported ROM briefly has neither). Framed
     * like the save-state screenshots. */
    @Composable
    private fun CoverThumb(rom: File, m: GbaTextMetrics, modifier: Modifier = Modifier) {
        @Suppress("UNUSED_EXPRESSION") revision
        val bitmap = remember(rom.absolutePath, revision) {
            coverFile(rom).takeIf { it.isFile && it.length() > 0 }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
        val u = m.u
        Box(
            modifier = modifier
                .aspectRatio(ICON_ASPECT)
                .clip(PixelRoundedShape(u * 3))
                .background(com.pokedaisy.app.companion.ui.OptionColors.imageWell)
                .drawWithContent {
                    drawContent()
                    val px = u.toPx()
                    drawLayeredFrame(listOf(OptionColors.frameDark to px, OptionColors.frameLight to px), radius = 3 * px)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                CoverImage(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.None,
                )
            } else {
                Icon(
                    Icons.Filled.VideogameAsset, contentDescription = null,
                    tint = OptionColors.muted, modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    private val MENU_ITEM_MIN_HEIGHT = 48.dp
    /** The library's margin to the screen's edges. */
    private val SCREEN_MARGIN = 16.dp

    /** Wider than the parent by [d] each side (the list's own margin), drawn from [d] left of it. */
    private fun Modifier.bleedHorizontally(d: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
        val extra = d.roundToPx() * 2
        val wide = constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        )
        val p = measurable.measure(wide)
        layout((p.width - extra).coerceAtLeast(0), p.height) { p.place(-extra / 2, 0) }
    }
    /** About a menu column's width (Material's menus are 112-280 dp). */
    private val MENU_COLUMN_WIDTH = 280.dp

    /** The library screen's size, for a game's menu to pick one or two columns. */
    private var screenSize by mutableStateOf(DpSize.Zero)

    @Composable
    private fun RomOptionsMenu(
        expanded: Boolean,
        onDismiss: () -> Unit,
        rom: File,
        m: GbaTextMetrics,
        onRename: () -> Unit,
        onDelete: () -> Unit,
    ) {
        class Item(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)
        val items = buildList {
            if (rom.absolutePath in prefs.favoriteRoms) {
                add(Item(tr("UNFAVORITE"), Icons.Filled.StarBorder) { setFavorite(rom, false) })
            } else {
                add(Item(tr("FAVORITE"), Icons.Filled.Star) { setFavorite(rom, true) })
            }
            add(Item(tr("INFO"), Icons.Filled.Info) { showInfo(rom) })
            add(Item(tr("RENAME"), Icons.Filled.Edit, onClick = onRename))
            add(Item(tr("REPLACE COVER"), Icons.Filled.Search) { coverError = null; coverPickerFor = rom })
            add(Item(tr("SET COVER"), Icons.Filled.Image) { pendingCoverTarget = rom; pickCoverImage.launch("image/*") })
            if (prefs.romCoverManual(rom)) {
                add(Item(tr("RESET COVER"), Icons.Filled.Refresh) { resetCover(rom) })
            }
            add(Item(tr("LOAD SAVE"), Icons.Filled.FileOpen) { saveTarget = rom; pickSaveFile.launch(arrayOf("*/*")) })
            add(Item(tr("RESTORE BACKUP"), Icons.Filled.History) { showBackups(rom) })
            // This game's saves in a folder of its own (another emulator's), or back to the usual search.
            add(Item(tr("SAVE FOLDER"), Icons.Filled.Folder) {
                if (!StorageAccess.hasAllFilesAccess(this@LibraryActivity)) {
                    StorageAccess.requestAllFilesAccess(this@LibraryActivity)
                    Toast.makeText(this@LibraryActivity, tr("Grant \"All files access\", then tap the button again"), Toast.LENGTH_LONG).show()
                } else {
                    saveFolderTarget = rom
                    pickGameSaveFolder.launch(null)
                }
            })
            if (prefs.romSaveDir(rom) != null) {
                add(Item(tr("DEFAULT SAVE FOLDER"), Icons.Filled.FolderOff) { setGameSaveFolder(rom, null) })
            }
            // The game's cheats, on Settings' CHEATS page.
            add(Item(tr("CHEATS"), Icons.Filled.Code) {
                startActivity(Intent(this@LibraryActivity, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CHEATS_ROM, rom.absolutePath))
            })
            add(Item(tr("HIDE"), Icons.Filled.VisibilityOff) { hideRom(rom) })
            // A linked folder's ROM is the player's own file: it can be hidden, never deleted.
            if (!RomFolder.isLinked(prefs, rom)) {
                add(Item(tr("DELETE"), Icons.Filled.Delete, danger = true, onClick = onDelete))
            }
        }

        @Composable
        fun MenuItem(item: Item) {
            val fg = if (item.danger) OptionColors.value else OptionColors.label
            DropdownMenuItem(
                text = { GbaText(item.label, fg, if (item.danger) OptionColors.valueShadow else OptionColors.labelShadow, m) },
                leadingIcon = { Icon(item.icon, null, tint = fg) },
                onClick = { onDismiss(); item.onClick() },
            )
        }
        // Two columns where one would run off the screen and two fit across it (a wide screen):
        // the list had grown past a landscape screen's height.
        val itemHeight = maxOf(MENU_ITEM_MIN_HEIGHT, m.lineHeight + 16.dp)
        val twoColumns = screenSize.width >= MENU_COLUMN_WIDTH * 2 + 32.dp &&
            itemHeight * items.size + 16.dp > screenSize.height - 32.dp
        // Square corners on the popup's own surface (its shape must be a
        // CornerBasedShape - no pixel steps possible), and a subtle 1u frame
        // just inside it rather than Material's smooth-rounded edge.
        val u = m.u
        MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(0.dp))) {
            DropdownMenu(
                expanded = expanded, onDismissRequest = onDismiss,
                modifier = Modifier
                    .background(OptionColors.titleFill)
                    .drawWithContent {
                        drawContent()
                        drawLayeredFrame(listOf(MenuBorder to u.toPx()), radius = 0f)
                    },
            ) {
                if (!twoColumns) {
                    items.forEach { MenuItem(it) }
                } else {
                    // Down the first column, then the second (DELETE last, bottom right); a 1u rule between.
                    val half = (items.size + 1) / 2
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Column(Modifier.width(IntrinsicSize.Max)) { items.take(half).forEach { MenuItem(it) } }
                        Box(Modifier.fillMaxHeight().padding(vertical = u * 4).width(u).background(MenuBorder))
                        Column(Modifier.width(IntrinsicSize.Max)) { items.drop(half).forEach { MenuItem(it) } }
                    }
                }
            }
        }
    }

    /** "⋮" button + its menu, sharing one Box so the popup anchors at the icon. [menu] / [onMenu]: the
     * row's own (a long press on the row opens it too). */
    @Composable
    private fun RomMenuButton(rom: File, m: GbaTextMetrics, menu: Boolean, onMenu: (Boolean) -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
        Box {
            Box(modifier = Modifier.clip(PixelRoundedShape(m.u * 3)).clickable { onMenu(true) }.padding(m.u * 3)) {
                Icon(Icons.Filled.MoreVert, contentDescription = tr("ROM options"), tint = OptionColors.label, modifier = Modifier.size(m.lineHeight))
            }
            RomOptionsMenu(menu, onDismiss = { onMenu(false) }, rom = rom, m = m, onRename = onRename, onDelete = onDelete)
        }
    }

    /** A long press's menu: opens it with a light tick (the device's long-press haptic). */
    @Composable
    private fun longPressMenu(open: () -> Unit): () -> Unit {
        val haptic = LocalHapticFeedback.current
        return { haptic.performHapticFeedback(HapticFeedbackType.LongPress); open() }
    }

    /** A favorite's star, beside its title. */
    @Composable
    private fun FavoriteStar(size: androidx.compose.ui.unit.Dp) {
        Icon(Icons.Filled.Star, contentDescription = tr("Favorite"), tint = OptionColors.favorite, modifier = Modifier.size(size))
    }

    private fun setFavorite(rom: File, on: Boolean) {
        prefs.favoriteRoms = if (on) prefs.favoriteRoms + rom.absolutePath else prefs.favoriteRoms - rom.absolutePath
        bump()
    }

    /** A card in the OPTION list-window look; the last-played ROM gets the
     * list's white "cursor" fill. D-pad focus tints it like a focused row. */
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    private fun RomCard(
        selected: Boolean,
        m: GbaTextMetrics,
        modifier: Modifier = Modifier,
        onLongClick: (() -> Unit)? = null,
        /** Where D-pad UP goes from this card, if not the card above ([toHeader] for the top row). */
        upTo: () -> FocusRequester? = noUp,
        onClick: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        OptionListWindow(
            m,
            modifier
                // The list window's own corner (OptionListWindow draws its frame at a 2u radius).
                .gbaFocusRing(focused, PixelRoundedShape(m.u * 2))
                .focusProperties { upTo()?.let { up = it } }
                .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick),
            fill = if (selected || focused) OptionColors.rowSelected else OptionColors.listFill,
        ) { content() }
    }

    @Composable
    private fun RomRow(rom: File, m: GbaTextMetrics, small: GbaTextMetrics, onRename: () -> Unit, onDelete: () -> Unit, upTo: () -> FocusRequester? = noUp) {
        val isLast = rom.absolutePath == prefs.lastRomPath
        val isNew = remember(rom, revision) { RomAdded.isNew(this@LibraryActivity, rom) }
        val display = romLabel(rom)
        // Existing progress indicator: does a .sav/.srm already exist for this ROM?
        val hasSave = remember(rom, revision) {
            SavesLocation.saveFor(this@LibraryActivity, prefs, rom).exists()
        }
        val favorite = rom.absolutePath in prefs.favoriteRoms
        // The ⋮ menu opens from a long press on the row too, like a grid tile.
        var menu by remember { mutableStateOf(false) }
        RomCard(isLast, m, Modifier.fillMaxWidth(), onLongClick = longPressMenu { menu = true }, upTo = upTo, onClick = { play(rom) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverThumb(rom, m, modifier = Modifier.height(56.dp))
                Spacer(Modifier.width(m.u * 6))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GbaText(display, OptionColors.label, OptionColors.labelShadow, m, Modifier.weight(1f, fill = false))
                        if (favorite) {
                            Spacer(Modifier.width(m.u * 4))
                            FavoriteStar(small.lineHeight)
                        }
                        if (hasSave) {
                            Spacer(Modifier.width(m.u * 4))
                            Icon(
                                Icons.Filled.Save, contentDescription = tr("Save file exists"),
                                tint = OptionColors.label, modifier = Modifier.size(small.lineHeight),
                            )
                        }
                        if (isLast) {
                            Spacer(Modifier.width(m.u * 4))
                            GbaText(tr("PLAYING"), OptionColors.value, OptionColors.valueShadow, small)
                        }
                        if (isNew) {
                            Spacer(Modifier.width(m.u * 4))
                            OptionBadge(tk("NEW"), m, fill = OptionColors.newBadge, shadow = OptionColors.newBadgeShadow)
                        }
                        if (rom.absolutePath in unsupportedRoms) {
                            Spacer(Modifier.width(m.u * 4))
                            OptionBadge(tk("NOT SUPPORTED"), m)
                        } else if (rom.absolutePath in partialRoms) {
                            Spacer(Modifier.width(m.u * 4))
                            OptionBadge(tk("PARTIALLY SUPPORTED"), m, fill = OptionColors.muted)
                        }
                    }
                    GbaText(
                        prefs.romSourcePath(rom) ?: rom.absolutePath,
                        OptionColors.muted, OptionColors.mutedShadow, small,
                    )
                }
                RomMenuButton(rom, m, menu, { menu = it }, onRename, onDelete)
            }
        }
    }

    @Composable
    private fun RomTile(rom: File, m: GbaTextMetrics, small: GbaTextMetrics, onRename: () -> Unit, onDelete: () -> Unit, upTo: () -> FocusRequester? = noUp) {
        val isLast = rom.absolutePath == prefs.lastRomPath
        val isNew = remember(rom, revision) { RomAdded.isNew(this@LibraryActivity, rom) }
        val display = romLabel(rom)
        val hasSave = remember(rom, revision) {
            SavesLocation.saveFor(this@LibraryActivity, prefs, rom).exists()
        }
        // Just the cover and the title (+ save marker); the options menu is a long press.
        var menu by remember { mutableStateOf(false) }
        Box {
            RomCard(
                isLast, m, Modifier.fillMaxWidth(),
                onLongClick = longPressMenu { menu = true },
                upTo = upTo,
                onClick = { play(rom) },
            ) {
                Column {
                    CoverThumb(rom, m, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(m.u * 3))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GbaText(display, OptionColors.label, OptionColors.labelShadow, small, Modifier.weight(1f))
                        if (rom.absolutePath in prefs.favoriteRoms) {
                            Spacer(Modifier.width(m.u * 2))
                            FavoriteStar(small.lineHeight)
                        }
                        if (hasSave) {
                            Spacer(Modifier.width(m.u * 2))
                            Icon(
                                Icons.Filled.Save, contentDescription = tr("Save file exists"),
                                tint = OptionColors.label, modifier = Modifier.size(small.lineHeight),
                            )
                        }
                    }
                    val support = when (rom.absolutePath) {
                        in unsupportedRoms -> tk("NOT SUPPORTED") to OptionColors.value
                        in partialRoms -> tk("PARTIALLY SUPPORTED") to OptionColors.muted
                        else -> null
                    }
                    if (isNew || support != null) {
                        Spacer(Modifier.height(m.u * 2))
                        Row(horizontalArrangement = Arrangement.spacedBy(m.u * 2)) {
                            if (isNew) OptionBadge(tk("NEW"), m, fill = OptionColors.newBadge, shadow = OptionColors.newBadgeShadow)
                            support?.let { (label, fill) -> OptionBadge(label, m, fill = fill) }
                        }
                    }
                }
            }
            RomOptionsMenu(menu, onDismiss = { menu = false }, rom = rom, m = m, onRename = onRename, onDelete = onDelete)
        }
    }

    /** Compact, menu-less version of [RomTile] for the "Recently Played" row —
     * a shortcut, not a full library entry. */
    @Composable
    private fun RecentTile(rom: File, m: GbaTextMetrics, small: GbaTextMetrics, onClick: () -> Unit) {
        val display = romLabel(rom)
        RomCard(false, m, Modifier.width(112.dp), upTo = toHeader, onClick = onClick) {
            Column {
                CoverThumb(rom, m, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(m.u * 3))
                GbaText(
                    display, OptionColors.label, OptionColors.labelShadow, small, maxLines = 2,
                    modifier = Modifier.height(small.lineHeight * 2),
                )
            }
        }
    }

    /** List-row-shaped version of a recent shortcut (mirrors [RomRow]'s
     * icon-left/text-right shape) for the "Recently Played" row while in
     * list view. */
    @Composable
    private fun RecentListItem(rom: File, m: GbaTextMetrics, small: GbaTextMetrics, onClick: () -> Unit) {
        val display = romLabel(rom)
        RomCard(false, m, Modifier.width(220.dp), upTo = toHeader, onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverThumb(rom, m, modifier = Modifier.height(44.dp))
                Spacer(Modifier.width(m.u * 4))
                GbaText(display, OptionColors.label, OptionColors.labelShadow, small, Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun RenameDialog(
        current: String,
        m: GbaTextMetrics,
        small: GbaTextMetrics,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
    ) {
        var text by remember { mutableStateOf(current) }
        OptionOverlay(onDismiss, Modifier.widthIn(max = 520.dp)) {
            Column {
                OptionTitleWindow(tr("RENAME"), m)
                Spacer(Modifier.height(m.u * 4))
                OptionListWindow(m, Modifier.fillMaxWidth()) {
                    Column {
                        GbaText(
                            tr("Name shown in the list (display only - the file keeps its name)."),
                            OptionColors.label, OptionColors.labelShadow, small, maxLines = Int.MAX_VALUE,
                            modifier = Modifier.padding(horizontal = m.u * 8, vertical = m.u * 2),
                        )
                        OptionTextField(text, { text = it }, small, Modifier.padding(horizontal = m.u * 8, vertical = m.u * 2))
                        OptionLine(tr("SAVE"), null, selected = false, m, height = m.rowHeight * 1.3f) { onConfirm(text) }
                        OptionLine(tr("CANCEL"), null, selected = true, m, height = m.rowHeight * 1.3f, onClick = onDismiss)
                    }
                }
            }
        }
    }

    /** Imported ROMs plus the linked folder's supported ones (from its last scan). */
    /** The library, alphabetical. Every ROM it lists for the first time is dated ([RomAdded]): NEW
     * unless setup is running or a newly linked folder's first scan brought it in. Any thread. */
    private fun listRoms(): List<File> = RomFolder.libraryRoms(this, prefs).also { roms ->
        RomAdded.record(this, roms, initial = setup != null || linkingFolder)
    }

    /** A folder was just linked: its first scan's ROMs are the library being set up, not new games. */
    @Volatile private var linkingFolder = false

    private fun coverFile(rom: File): File = File(coversDir, "${rom.name}.png")

    private fun play(rom: File) {
        prefs.lastRomPath = rom.absolutePath
        prefs.pushRecentRom(rom.absolutePath)
        // Opened on the bottom screen, the game still goes to the top one (Screens.mainDisplayLaunch).
        val onMain = Screens.mainDisplayLaunch(this)
        val intent = Intent(this, PokeDaisyActivity::class.java).putExtra(EXTRA_ROM, rom.absolutePath)
        if (onMain != null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent, onMain)
    }

    /** Blocking: fetches+caches [rom]'s cover over the network - its
     * RetroAchievements box art when a Web API key is set, else SteamGridDB's
     * icon for a known game ([CoverArtSync.fetchOne] — shared with
     * SettingsActivity's Cover Art screen). Call only from a background thread
     * — never the UI thread (network I/O), never inside a Compose recomposition
     * (the ROM's SHA1 / MD5 are whole-file reads). */
    private fun generateAutoCoverBlocking(rom: File): Boolean =
        CoverArtSync.fetchOne(rom, coverFile(rom), prefs) ==
            CoverArtSync.Status.FETCHED

    /** Fire-and-forget wrapper for a single ROM — used right after import or
     * a manual Reset Cover, both triggered from the UI thread. */
    private fun generateAutoCoverAsync(rom: File) {
        Thread({
            if (generateAutoCoverBlocking(rom)) runOnUiThread { bump() }
        }, "pokedaisy-cover-fetch").apply { isDaemon = true; start() }
    }

    /** Backfills a cover for every listed ROM that doesn't have one yet
     * (skips ROMs with a manually-picked cover) — called on Refresh so ROMs
     * imported before an API key was set, or before this feature existed,
     * catch up. Runs entirely off the UI thread: hashing + fetching several
     * ROMs in a row is real work (see generateAutoCoverBlocking's doc). */
    private fun backfillCovers() {
        Thread({
            val any = listRoms().map { generateAutoCoverBlocking(it) }.any { it }
            if (any) runOnUiThread { bump() }
        }, "pokedaisy-cover-backfill-scan").apply { isDaemon = true; start() }
    }

    /** REPLACE COVER's window: the ROM it's open for (internal for the ui-preview harness). */
    internal var coverPickerFor by mutableStateOf<File?>(null)
    /** Canned picker data for the ui-preview harness (no network there). */
    internal var coverSourceOverride: CoverSource? = null
    private var coverSaving by mutableStateOf(false)
    /** EXPAND's one-time introduction is up ([com.pokedaisy.app.companion.ui.ExpandIntro]). */
    private var expandIntro by mutableStateOf(false)
    private var coverError by mutableStateOf<String?>(null)

    /** Downloads the picked icon over [rom]'s cover (only swapped once it has
     * fully arrived) and marks it manual, so Refresh won't touch it and
     * RESET COVER can go back to the automatic pick. */
    private fun saveSteamGridCover(rom: File, icon: SteamGridDbClient.Icon) {
        coverSaving = true
        coverError = null
        Thread({
            val ok = SteamGridDbClient.download(icon.url, coverFile(rom))
            runOnUiThread {
                coverSaving = false
                if (ok) {
                    prefs.setRomCoverManual(rom, true)
                    coverPickerFor = null
                    bump()
                } else {
                    coverError = tr("COULDN'T DOWNLOAD THAT ONE - TRY ANOTHER")
                }
            }
        }, "pokedaisy-cover-pick").apply { isDaemon = true; start() }
    }

    /** INFO: the quick facts at once, then again with the hashes (a whole-ROM read). */
    private fun showInfo(rom: File) {
        gameInfo = GameInfo.read(this, prefs, rom, hashes = false)
        Thread({
            val full = GameInfo.read(this, prefs, rom, hashes = true)
            runOnUiThread { if (gameInfo?.title == full.title) gameInfo = full }
        }, "pokedaisy-game-info").apply { isDaemon = true; start() }
    }

    /** Reads the picked save (anything over [GameSaves.MAX_BYTES] isn't one) for its confirm. */
    private fun readPickedSave(rom: File, uri: Uri) {
        Thread({
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(1 shl 14)
                    while (out.size() <= GameSaves.MAX_BYTES) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            }.getOrNull()
            val name = RomUris.displayName(this, uri) ?: tr("the picked file")
            runOnUiThread {
                when {
                    bytes == null || bytes.isEmpty() -> Toast.makeText(this, tr("Couldn't read {0}", name), Toast.LENGTH_LONG).show()
                    bytes.size > GameSaves.MAX_BYTES -> Toast.makeText(this, tr("{0} is too big to be a GBA save", name), Toast.LENGTH_LONG).show()
                    else -> saveLoad = PendingSaveLoad(rom, bytes, name)
                }
            }
        }, "pokedaisy-read-save").apply { isDaemon = true; start() }
    }

    /** Every backup of [rom]'s save: the ones each start takes ([SaveBackups]) and LOAD SAVE's. */
    private fun showBackups(rom: File) {
        val save = SavesLocation.saveFor(this, prefs, rom)
        val dir = save.parentFile ?: SavesLocation.dir(this, prefs)
        val files = (SaveBackups.list(save) + RomArchive.saveNames(rom).flatMap { GameSaves.backups(dir, it) })
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.name.substringAfter(".backup-") }
        if (files.isEmpty()) {
            Toast.makeText(this, tr("No backups of this game's save yet"), Toast.LENGTH_LONG).show()
        } else {
            backupPick = BackupPick(rom, files)
        }
    }

    /** `2026-10-07 23:35  FLASH 128K` from `<game>.backup-20261007-233512.srm`. */
    private fun backupLabel(f: File): String {
        val stamp = f.name.substringAfter(".backup-").substringBefore('.')
        val date = runCatching {
            val d = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).parse(stamp)!!
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(d)
        }.getOrDefault(f.name)
        return date + (GameSaves.kind(f.length())?.let { "  $it" } ?: "")
    }

    /** A backup goes through LOAD SAVE's own confirm, so the current save is kept too. */
    private fun readBackup(rom: File, f: File) {
        Thread({
            val bytes = runCatching { f.readBytes() }.getOrNull()
            runOnUiThread {
                if (bytes == null || bytes.isEmpty()) Toast.makeText(this, tr("Couldn't read {0}", f.name), Toast.LENGTH_LONG).show()
                else saveLoad = PendingSaveLoad(rom, bytes, f.name)
            }
        }, "pokedaisy-read-backup").apply { isDaemon = true; start() }
    }

    private fun loadSave(p: PendingSaveLoad) {
        Thread({
            val result = GameSaves.load(this, prefs, p.rom, p.bytes)
            runOnUiThread {
                val msg = when (result) {
                    is GameSaves.Result.Loaded -> result.backup?.let { tr("Save loaded - the old one is {0}", it.name) } ?: tr("Save loaded")
                    is GameSaves.Result.Failed -> result.reason
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                bump()
            }
        }, "pokedaisy-load-save").apply { isDaemon = true; start() }
    }

    /** Takes [rom] out of the library, keeping the file, its name, cover and saves;
     * Settings > HIDDEN GAMES shows it again. */
    private fun hideRom(rom: File) {
        prefs.hiddenRoms = prefs.hiddenRoms + rom.absolutePath
        Toast.makeText(this, tr("Hidden - show it again in Settings > Hidden games"), Toast.LENGTH_SHORT).show()
        bump()
    }

    private fun setManualCover(rom: File, uri: Uri) {
        runCatching {
            val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
            val cropped = cropToIconAspect(src)
            val out = coverFile(rom)
            out.parentFile?.mkdirs()
            val tmp = File(out.parentFile, "${out.name}.tmp")
            tmp.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 92, it) }
            tmp.renameTo(out)
            prefs.setRomCoverManual(rom, true)
        }
    }

    /** Reverts to the auto cover: clears the manual flag, drops the current
     * file, and immediately re-queues a fetch so a cover is never left
     * simply missing (for a known, fetchable hack; otherwise back to the
     * placeholder icon). */
    private fun resetCover(rom: File) {
        prefs.setRomCoverManual(rom, false)
        coverFile(rom).delete()
        bump()
        generateAutoCoverAsync(rom)
    }

    /** Center-crops [src] to [ICON_ASPECT] (never upscales), then caps the
     * long edge at [MAX_COVER_WIDTH] so a manually-picked photo doesn't
     * balloon the covers/ folder. */
    private fun cropToIconAspect(src: Bitmap): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val (cw, ch) = if (srcRatio > ICON_ASPECT) {
            (src.height * ICON_ASPECT).toInt().coerceAtLeast(1) to src.height
        } else {
            src.width to (src.width / ICON_ASPECT).toInt().coerceAtLeast(1)
        }
        val x = ((src.width - cw) / 2).coerceAtLeast(0)
        val y = ((src.height - ch) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(src, x, y, cw.coerceAtMost(src.width - x), ch.coerceAtMost(src.height - y))
        if (cropped.width <= MAX_COVER_WIDTH) return cropped
        val scale = MAX_COVER_WIDTH.toFloat() / cropped.width
        return Bitmap.createScaledBitmap(cropped, MAX_COVER_WIDTH, (cropped.height * scale).toInt().coerceAtLeast(1), true)
    }

    /**
     * Copies [uri] aside and checks it with [CompanionSupport] (a whole-file
     * hash for big ROMs, so off the UI thread): a supported ROM goes straight
     * into the library, anything else waits on the "not supported" confirm.
     * A `.zip` / `.7z` is unpacked: its ROM joins the library under the
     * archive's name (`Emerald.zip` -> `Emerald.gba`).
     */
    private fun startImport(uri: Uri, play: Boolean) {
        // VIEW is exported: any app can point it anywhere. Never this app's own files (RomIntake).
        if (RomIntake.isOwn(this, uri)) return
        Thread({
            val pending = try {
                var tmp = File(importDir, "import-${System.currentTimeMillis()}.gba")
                try {
                    contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { RomIntake.copyCapped(input, it) } }
                } catch (t: Throwable) {
                    tmp.delete()
                    throw t
                }
                var name = RomUris.sanitizeFileName(RomUris.displayName(this, uri) ?: "imported-${System.currentTimeMillis()}.gba")
                RomArchive.sniff(tmp)?.let { format ->
                    val rom = File(importDir, "${tmp.name}.rom")
                    val entry = RomArchive.extract(tmp, rom, format)
                    tmp.delete()
                    if (entry == null) {
                        runOnUiThread { Toast.makeText(this, tr("No GBA ROM in {0}", name), Toast.LENGTH_LONG).show() }
                        return@Thread
                    }
                    val base = name.takeIf { it.substringAfterLast('.', "").lowercase() in RomArchive.EXTENSIONS }
                        ?.substringBeforeLast('.') ?: entry.name.substringAfterLast('/').substringBeforeLast('.')
                    name = RomUris.sanitizeFileName("$base.${entry.extension}")
                    tmp = rom
                }
                if (tmp.length() > 0 && RomIdentity.looksLikeRom(tmp)) {
                    PendingImport(tmp, name, RomUris.originalPath(this, uri), play)
                } else {
                    tmp.delete(); null
                }
            } catch (t: Throwable) {
                null
            } ?: return@Thread
            val supported = CompanionSupport.isSupported(pending.tmp)
            runOnUiThread { if (supported) finishImport(pending) else pendingImport = pending }
        }, "pokedaisy-import").apply { isDaemon = true; start() }
    }

    /** Moves an accepted import into the library (replacing a same-named ROM, as before). */
    private fun finishImport(p: PendingImport) {
        val out = File(romsDir, p.name)
        if (!p.tmp.renameTo(out)) {
            runCatching { p.tmp.copyTo(out, overwrite = true) }
            p.tmp.delete()
        }
        if (out.length() > 0) {
            prefs.setRomSourcePath(out, p.sourcePath)
            generateAutoCoverAsync(out)
            identifyGames()
            if (p.play) play(out)
        }
        bump()
    }

    // ---- first-time setup ---------------------------------------------------

    private fun startSetup() {
        setup = SetupState().apply {
            hasAccess = StorageAccess.hasAllFilesAccess(this@LibraryActivity)
            romsFolder = prefs.romsFolder
            if (romsFolder != null) found = RomFolder.found(this@LibraryActivity, prefs).map(::romLabel)
            savesDir = prefs.savesDirOverride
            apiKey = prefs.steamGridDbApiKey.orEmpty()
            raKey = prefs.raWebApiKey.orEmpty()
            keySaved = prefs.steamGridDbApiKey != null || prefs.raWebApiKey != null
        }
    }

    @Composable
    private fun SetupHost(s: SetupState) {
        val m = rememberGbaTextMetrics()
        val small = rememberGbaTextMetrics(textScale = 1f)
        BackHandler { setupBack(s) }
        SetupScreen(
            s, m, small,
            onChooseRoms = { chooseFolder(s, SetupState.Step.ROMS) },
            onChooseSaves = { chooseFolder(s, SetupState.Step.SAVES) },
            onUseSaves = { path -> useSavesFolder(path) },
            onOpenPage = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
            onSaveKey = {
                prefs.steamGridDbApiKey = s.apiKey
                prefs.raWebApiKey = s.raKey
                s.keySaved = prefs.steamGridDbApiKey != null || prefs.raWebApiKey != null
                if (s.keySaved) s.covers.start(this, prefs, replace = false)
            },
            onBack = { setupBack(s) },
            onChangeButtons = {
                startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SCREEN, SettingsActivity.SCREEN_CONTROLS))
            },
            onNext = {
                val steps = SetupState.Step.entries
                if (s.step == steps.last()) finishSetup() else enterStep(s, steps[s.step.ordinal + 1])
            },
        )
    }

    /** BACK: the previous step; on the first, leaves setup as if skipped - back to
     * Settings when RUN SETUP opened it. */
    private fun setupBack(s: SetupState) {
        if (s.step.ordinal > 0) {
            enterStep(s, SetupState.Step.entries[s.step.ordinal - 1])
            return
        }
        finishSetup()
        if (s.fromSettings) startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun enterStep(s: SetupState, step: SetupState.Step) {
        s.step = step
        if (step == SetupState.Step.BUTTONS) readButtons(s)
        if (step == SetupState.Step.SAVES) {
            Thread({
                val found = runCatching { SavesLocation.suggestions(prefs) }.getOrDefault(emptyList())
                runOnUiThread { s.suggestions = found }
            }, "pokedaisy-saves-suggest").apply { isDaemon = true; start() }
        }
    }

    /** The GAME BUTTONS step's list: A, B, L, R, START, SELECT (+ the second START / SELECT, turbo) as bound now. */
    private fun readButtons(s: SetupState) {
        s.buttons = GbaControls.rawBindings(getExternalFilesDir(null) ?: filesDir).map { (btn, keys) ->
            btn.label to keys.joinToString(" / ") { it.removePrefix("BUTTON_").replace('_', ' ') }
        }
    }

    private fun finishSetup() {
        prefs.setupDone = true
        setup = null
        bump()
    }

    /** Folders are opened by raw path, so All files access comes first: Android's page
     * opens, and onResume carries on with the pick once the player comes back. */
    private fun chooseFolder(s: SetupState, step: SetupState.Step) {
        if (!StorageAccess.hasAllFilesAccess(this)) {
            s.awaitingAccess = step
            StorageAccess.requestAllFilesAccess(this)
            return
        }
        if (step == SetupState.Step.ROMS) pickRomsFolder.launch(null) else pickSavesFolder.launch(null)
    }

    /** The picker's folder as a real path (persisting its grant), or null with a toast. */
    private fun pickedFolder(uri: Uri?): String? {
        if (uri == null) return null
        val path = StorageAccess.treePath(uri)
        if (path == null) {
            Toast.makeText(this, tr("Only folders on this device's own storage are supported"), Toast.LENGTH_LONG).show()
            return null
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        return path
    }

    /**
     * SAVE FOLDER: [rom]'s save lives in [path] from now on (null: the saves folders' search again). A
     * folder without a save for it gets a copy of the current one - copied, never moved, so the
     * old file stays where it was.
     */
    private fun setGameSaveFolder(rom: File, path: String?) {
        val before = SavesLocation.saveFor(this, prefs, rom)
        prefs.setRomSaveDir(rom, path)
        val after = SavesLocation.saveFor(this, prefs, rom)
        if (before.isFile && !after.exists() && before.absolutePath != after.absolutePath) {
            runCatching { before.copyTo(after) }
                .onSuccess { Toast.makeText(this, tr("Save copied to {0}", after.parent ?: ""), Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(this, tr("Can't write the save to {0}", after.parent ?: ""), Toast.LENGTH_LONG).show() }
        }
        revision++
    }

    private fun linkRomsFolder(path: String) {
        if (path != prefs.romsFolder) {
            prefs.romsFolder = path
            RomFolder.clearCache(this)
            linkingFolder = true
        }
        val s = setup
        s?.romsFolder = path
        s?.found = null
        rescanRomsFolder(s)
    }

    private fun useSavesFolder(path: String?) {
        prefs.savesDirOverride = path
        setup?.savesDir = path
        bump()
    }

    private var scanningFolder = false
    /** The Refresh button is waiting on a scan to say what it found. */
    private var reportScan = false
    private var refreshToast: Toast? = null

    /** The header's Refresh: rescans the linked folder and catches up on covers,
     * with a toast now ("Refreshing…") and one when the scan is done saying what
     * the library has. */
    private fun refreshLibrary() {
        bump()
        backfillCovers()
        if (prefs.romsFolder == null) {
            showRefreshToast(tr("Library refreshed: {0}", gamesLabel(listRoms().size)))
            return
        }
        reportScan = true
        showRefreshToast(tr("Refreshing library…"))
        rescanRomsFolder()
    }

    /** One toast at a time: the result replaces "Refreshing…" instead of queueing behind it. */
    private fun showRefreshToast(text: String) {
        refreshToast?.cancel()
        refreshToast = Toast.makeText(this, text, Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun gamesLabel(n: Int) = if (n == 1) tr("1 game") else tr("{0} games", n)

    private fun refreshResult(r: RomFolder.ScanResult): String {
        if (!r.readable) return tr("Couldn't read your ROMs folder - check All files access in SETTINGS > FOLDERS")
        val parts = mutableListOf(gamesLabel(listRoms().size))
        if (r.added.isNotEmpty()) parts += tr("{0} new", r.added.size)
        val skipped = r.files - r.supported
        if (skipped > 0) parts += tr("{0} not supported", skipped)
        return tr("Library refreshed: {0}", parts.joinToString(", "))
    }

    /** Looks for new or changed ROMs in the linked folder, off the UI thread; [s]
     * shows the progress (setup's ROMS step). New games get their covers. */
    private fun rescanRomsFolder(s: SetupState? = null) {
        // Setup's own scan always runs (RomFolder.scan queues it behind a running one).
        // A Refresh during another scan (the one onResume starts) gets that one's result.
        if (scanningFolder && s == null) return
        if (prefs.romsFolder == null) {
            // Nothing linked: just the imported ROMs' NOT SUPPORTED verdicts.
            Thread({
                RomFolder.checkImported(this)
                runOnUiThread { bump() }
            }, "pokedaisy-rom-check").apply { isDaemon = true; start() }
            return
        }
        scanningFolder = true
        s?.scanning = true
        s?.checked = 0
        s?.toCheck = 0
        Thread({
            val result = RomFolder.scan(this, prefs) { checked, total ->
                if (s != null) runOnUiThread { s.checked = checked; s.toCheck = total }
            }
            RomFolder.checkImported(this)
            GameTitles.identify(this, listRoms())
            // The linked folder's ROMs are dated now (as the library being set up, not NEW).
            if (result.readable) linkingFolder = false
            val found = RomFolder.found(this, prefs).map(::romLabel)
            runOnUiThread {
                scanningFolder = false
                s?.scanning = false
                s?.found = found
                if (result.added.isNotEmpty()) backfillCovers()
                if (reportScan) {
                    reportScan = false
                    showRefreshToast(refreshResult(result))
                }
                bump()
            }
        }, "pokedaisy-rom-scan").apply { isDaemon = true; start() }
    }

    private fun romLabel(rom: File) = GameTitles.label(this, prefs, rom)

    /** Names the library's games ([GameTitles]: hashes new ROMs) off the UI thread,
     * redrawing once any name changed. The folder scan does the same for what it finds. */
    private fun identifyGames() {
        Thread({
            val roms = listRoms() + RomFolder.hiddenRoms(this, prefs)
            if (GameTitles.identify(this, roms)) runOnUiThread { bump() }
        }, "pokedaisy-game-titles").apply { isDaemon = true; start() }
    }

    companion object {
        /** The options popup's subtle 1u frame. */
        private val MenuBorder get() = if (OptionColors.dark) Color(0xFF4A5262) else Color(0xFFA8A8B0)
        const val EXTRA_ROM = "rom"
        private const val MAX_COVER_WIDTH = 480
    }
}
