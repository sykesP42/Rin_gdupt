package top.sykesp42.rin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import kotlinx.coroutines.delay
import top.sykesp42.rin.data.ApiRoom
import top.sykesp42.rin.data.JwxtFreeRoom
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.ClassroomCatalogUiState
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.components.DataBand
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.SuiAlertDialog
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.suiTextFieldColors
import top.sykesp42.rin.ui.extractRoomCode
import top.sykesp42.rin.ui.search.RoomSuggestion
import top.sykesp42.rin.ui.search.suggestActiveRoom
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.ThemeMode

/**
 * Search is one A2 paper page: masthead, an optional current-room band, a manual
 * code section and a debounced name/code search. Results are hairline rows; the
 * only surface fills are the warm current-room band and the error stripe.
 */
@Composable
fun SearchScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenSchedule: (() -> Unit)? = null,
    onOpenAcademic: (Int) -> Unit = {}
) {
    val search by vm.search.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val catalog by vm.classroomCatalog.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val grades by vm.grades.collectAsStateWithLifecycle()
    val signAccess by vm.signAccess.collectAsStateWithLifecycle()
    val signPro = signAccess.proEnabled
    val colors = LocalSuiSignColors.current
    // Spec 2.2: 24dp page inset, tightened to 20dp on narrow phones.
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    var query by rememberSaveable { mutableStateOf("") }
    var showManual by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showManual) { showManual = false }
    var codeRoom by remember { mutableStateOf<ApiRoom?>(null) }
    var codeInput by remember { mutableStateOf("") }
    val liveRoom = today.view?.takeIf { it.roomCode.isNotBlank() }
        ?.let { RoomSuggestion(it.roomCode, it.courseName) }
    val scheduledRoom = schedule.overview?.current?.let { event ->
        extractRoomCode(event.location)?.takeIf { it.isNotBlank() }
            ?.let { RoomSuggestion(it, event.courseName) }
    }
    val activeRoom = suggestActiveRoom(liveRoom, scheduledRoom, today.manualCode)

    LaunchedEffect(Unit) { vm.ensureClassroomCatalogLoaded() }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            vm.searchRooms("")
            return@LaunchedEffect
        }
        delay(350)
        vm.searchRooms(query)
    }
    val searching = query.isNotBlank()
    val resultsReady = searching &&
        !search.loading &&
        search.error.isBlank() &&
        search.searched &&
        search.query.trim() == query.trim()

    PaperPage {
        if (showManual && signPro) {
            ManualCodeScreen(
                initialCode = today.manualCode,
                currentManualCode = today.manualCode,
                onBack = { showManual = false },
                onMonitor = { code ->
                    vm.useRoom(code)
                    showManual = false
                    onOpenToday()
                },
                onClearManual = vm::clearManualCode,
                onOpenSchedule = onOpenSchedule
            )
        } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = pageInset,
                end = pageInset,
                top = 8.dp,
                bottom = 92.dp
            )
        ) {
            item(key = "masthead") {
                RinMasthead(
                    dateLine = if (today.manualCode.isNotBlank()) {
                        "手动教室 ${today.manualCode}"
                    } else {
                        "空教室 · 校历 · 考试 · 成绩"
                    },
                    title = "校务",
                    avatarText = avatarText,
                    onRefresh = {
                        vm.refreshClassroomCatalog()
                        vm.searchRooms(query)
                    },
                    onTheme = onToggleTheme
                )
            }
            if (!searching) {
                item(key = "campus-intro") {
                    val week = schedule.overview?.week ?: 0
                    if (week > 0) {
                        Text(
                            text = "第 $week 周",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }
            item(key = "search") {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it }
                    )
                    if (search.loading || (searching && !resultsReady && search.error.isBlank())) {
                        SearchLoadingLine(modifier = Modifier.padding(top = 12.dp))
                    }
                    if (search.error.isNotBlank()) {
                        SearchErrorRow(
                            message = search.error,
                            onRetry = { vm.searchRooms(query) },
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
            if (searching && resultsReady && search.rooms.isNotEmpty()) {
                item(key = "results-header") {
                    Column(modifier = Modifier.padding(top = 20.dp)) {
                        SectionHeader(title = "搜索结果", meta = "${search.rooms.size} 间")
                    }
                }
                itemsIndexed(
                    items = search.rooms,
                    key = { index, room -> "${room.roomCode}-${room.roomName}-$index" }
                ) { _, room ->
                    RoomRow(
                        room = room,
                        favorite = favorites.contains(room.roomName),
                        onToggleFavorite = { vm.toggleFavorite(room.roomName) },
                        onMonitor = {
                            vm.useRoom(room.roomCode)
                            onOpenToday()
                        },
                        onEnterCode = {
                            codeRoom = room
                            codeInput = room.suggestedCode
                        },
                        signActionsEnabled = signPro
                    )
                }
            }
            if (searching && resultsReady && search.rooms.isEmpty()) {
                item(key = "no-match") {
                    Text(
                        text = "试试完整四位码，或刷新目录",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
            if (!searching) {
            item(key = "campus-now") {
                CampusNowBand(
                    freeRooms = grades.freeRooms,
                    loading = grades.freeRoomLoading,
                    error = grades.freeRoomError,
                    onOpen = { onOpenAcademic(1) },
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
            item(key = "campus-academic") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    SectionHeader(title = "教务")
                    AcademicEntry(
                        "空教室",
                        "现在没课的教室",
                        meta = if (grades.freeRooms.isNotEmpty()) "${grades.freeRooms.size} 间" else null,
                        onClick = { onOpenAcademic(1) }
                    )
                    AcademicEntry("校历", "本月课程、考试与通知", onClick = { onOpenAcademic(2) })
                    AcademicEntry(
                        "考试",
                        "下一场和整学期安排",
                        meta = if (grades.exams.isNotEmpty()) "${grades.exams.size} 场" else null,
                        onClick = { onOpenAcademic(3) }
                    )
                    AcademicEntry(
                        "成绩",
                        "绩点、学分与课程明细",
                        meta = grades.gpa?.let { String.format(java.util.Locale.US, "%.2f", it) },
                        onClick = { onOpenAcademic(4) }
                    )
                }
            }
            if (activeRoom != null && signPro) {
                item(key = "active-room") {
                    ActiveRoomBand(
                        room = activeRoom,
                        onMonitor = {
                            vm.useRoom(activeRoom.code)
                            onOpenToday()
                        },
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
            item(key = "catalog") {
                CatalogSection(
                    catalog = catalog,
                    onCampus = vm::selectCampus,
                    onRefresh = vm::refreshClassroomCatalog,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            if (signPro) {
                item(key = "manual") {
                    ManualCodeEntry(
                        currentCode = today.manualCode,
                        onClick = { showManual = true },
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
            }
            if (!searching && catalog.rooms.isNotEmpty()) {
                val visibleRooms = catalog.rooms.filter {
                    catalog.selectedCampus.isBlank() || it.campus == catalog.selectedCampus
                }
                val favoriteRooms = visibleRooms.filter { favorites.contains(it.roomName) }
                if (favoriteRooms.isNotEmpty()) {
                    item(key = "favorites-header") {
                        Column(modifier = Modifier.padding(top = 24.dp)) {
                            SectionHeader(title = "常用教室", meta = "${favoriteRooms.size} 间")
                        }
                    }
                    itemsIndexed(
                        items = favoriteRooms,
                        key = { index, room ->
                            "fav-${room.roomCode}-${room.roomName}-${room.campus}-$index"
                        }
                    ) { _, room ->
                        RoomRow(
                            room = room,
                            favorite = true,
                            onToggleFavorite = { vm.toggleFavorite(room.roomName) },
                            onMonitor = {
                                vm.useRoom(room.roomCode)
                                onOpenToday()
                            },
                            onEnterCode = {
                                codeRoom = room
                                codeInput = room.suggestedCode
                            },
                            signActionsEnabled = signPro
                        )
                    }
                }
                val otherRooms = visibleRooms.filterNot { favorites.contains(it.roomName) }
                item(key = "catalog-list-header") {
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        SectionHeader(
                            title = catalog.selectedCampus.ifBlank { "全部教室" },
                            meta = "${otherRooms.size} 间"
                        )
                    }
                }
                itemsIndexed(
                    items = otherRooms,
                    key = { index, room ->
                        "catalog-${room.roomCode}-${room.roomName}-${room.campus}-$index"
                    }
                ) { _, room ->
                    RoomRow(
                        room = room,
                        favorite = false,
                        onToggleFavorite = { vm.toggleFavorite(room.roomName) },
                        onMonitor = {
                            vm.useRoom(room.roomCode)
                            onOpenToday()
                        },
                        onEnterCode = {
                            codeRoom = room
                            codeInput = room.suggestedCode
                        },
                        signActionsEnabled = signPro
                    )
                }
            }
        }
        }
    }

    if (signPro) codeRoom?.let { room ->
        SuiAlertDialog(
            onDismissRequest = { codeRoom = null },
            title = { Text("补全教室编号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(room.roomName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "四位码用于后台检查签到",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (room.suggestedCode.isNotBlank()) {
                        Text(
                            "推测 ${room.suggestedCode}，请核对",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { value ->
                            codeInput = value.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("四位教室编号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = suiTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = codeInput.length == 4,
                    onClick = {
                        vm.monitorRoomWithCode(room.roomName, codeInput)
                        codeRoom = null
                        onOpenToday()
                    }
                ) {
                    Text("保存并监控")
                }
            },
            dismissButton = {
                TextButton(onClick = { codeRoom = null }) {
                    Text("取消", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun AcademicEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    meta: String? = null
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (!meta.isNullOrBlank()) {
            Text(
                text = meta,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Text(
            text = "›",
            color = colors.textSecondary,
            style = MaterialTheme.typography.titleMedium
        )
    }
    Hairline()
}

/** The campus desk "now" stripe: free rooms come from the live JWXT call, so an
 * empty or unread result is stated as such instead of being called free. */
@Composable
private fun CampusNowBand(
    freeRooms: List<JwxtFreeRoom>,
    loading: Boolean,
    error: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    DataBand(modifier = modifier) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (freeRooms.isNotEmpty()) colors.signal else colors.textSecondary)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "空教室 · 现在",
                color = colors.text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when {
                    loading -> "正在读取教务…"
                    error.isNotBlank() -> error
                    freeRooms.isNotEmpty() -> buildString {
                        append("${freeRooms.size} 间可用")
                        val names = freeRooms.take(2).joinToString(" · ") { it.roomName }
                        if (names.isNotBlank()) append(" · $names")
                    }
                    else -> "教务未登录，读不到空教室"
                },
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            onClick = onOpen,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = "查看",
                color = colors.text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Local catalog status + campus filter. The catalog is persisted, so this
 * section remains useful before the next network refresh completes. */
@Composable
private fun CatalogSection(
    catalog: ClassroomCatalogUiState,
    onCampus: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = "教室目录",
            meta = if (catalog.total > 0) "${catalog.total} 间" else null
        )
        if (catalog.total > 0 || catalog.campuses.isNotEmpty()) {
            CampusFilter(
                campuses = catalog.campuses,
                selected = catalog.selectedCampus,
                onSelect = onCampus,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Text(
            text = when {
                catalog.loading -> "正在更新目录…"
                catalog.error.isNotBlank() -> catalog.error
                catalog.refreshedAt.isNotBlank() -> {
                    val time = runCatching { formatClock(Instant.parse(catalog.refreshedAt)) }.getOrNull()
                    if (time != null) "本机缓存 · 更新于 $time" else "本机缓存"
                }
                else -> "无教室目录"
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (catalog.loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = colors.signal,
                trackColor = colors.border
            )
        }
        if (catalog.rooms.isNotEmpty()) {
            val verified = catalog.rooms.count { it.roomCode.isNotBlank() }
            Text(
                text = "$verified 间已关联教室码 · ${catalog.rooms.size - verified} 间需要手填",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        TextButton(
            onClick = onRefresh,
            enabled = !catalog.loading,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            Text(
                text = if (catalog.loading) "正在刷新…" else "刷新目录",
                color = colors.text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CampusFilter(
    campuses: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CampusPill("全部", selected.isBlank(), onClick = { onSelect("") })
        campuses.forEach { campus ->
            CampusPill(campus, selected == campus, onClick = { onSelect(campus) })
        }
    }
}

@Composable
private fun CampusPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** Warm current-room stripe with a sky dot; the action stays a text action. */
@Composable
private fun ActiveRoomBand(
    room: RoomSuggestion,
    onMonitor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    DataBand(modifier = modifier) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.signal)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "当前课程",
                color = colors.text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "教室 ${room.code} · ${room.courseName.ifBlank { "未命名课程" }}",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            onClick = onMonitor,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = "开始监控",
                color = colors.text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Search input only; results and status lines stay outside the field. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("四位教室码或教室名称") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.textSecondary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "清空搜索",
                        tint = colors.textSecondary
                    )
                }
            }
        },
        singleLine = true,
        colors = suiTextFieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}

/** No spinner: one static sky dot keeps the page free of endless animation. */
@Composable
private fun SearchLoadingLine(modifier: Modifier = Modifier) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = modifier.heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.signal)
        )
        Text(
            text = "正在搜索…",
            color = colors.text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Exactly one search error surface: warning stripe, ink copy, ink retry. */
@Composable
private fun SearchErrorRow(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.06f))
    ) {
        Hairline()
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                color = colors.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "重试",
                    color = colors.text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Hairline()
    }
}

/** Result row: monospace ink code, name, last course and a text monitor action. */
@Composable
private fun RoomRow(
    room: ApiRoom,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMonitor: () -> Unit,
    onEnterCode: () -> Unit,
    signActionsEnabled: Boolean = true
) {
    val colors = LocalSuiSignColors.current
    val verified = room.roomCode.isNotBlank()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = verified && signActionsEnabled, onClick = onMonitor)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = room.roomCode.ifBlank { "—" },
                color = colors.text,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                modifier = Modifier.widthIn(min = 44.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.roomName.ifBlank { "未命名教室" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        room.campus.takeIf { it.isNotBlank() },
                        room.building.takeIf { it.isNotBlank() },
                        room.capacity.takeIf { it > 0 }?.let { "$it 人" },
                        room.lastCourseName.takeIf { it.isNotBlank() }?.let { "最近：$it" }
                    ).joinToString(" · ").let { base ->
                        when {
                            room.roomCode.isNotBlank() -> base
                            room.suggestedCode.isNotBlank() ->
                                listOf(base, "建议 ${room.suggestedCode}").filter { it.isNotBlank() }
                                    .joinToString(" · ")
                            else -> base
                        }
                    }.ifBlank { "编号待确认" },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (favorite) "取消常用" else "设为常用",
                    tint = if (favorite) colors.signal else colors.textSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }
            if (signActionsEnabled) {
                if (verified) {
                    TextButton(
                        onClick = onMonitor,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "开始监控",
                            color = colors.text,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    TextButton(
                        onClick = onEnterCode,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "补编号",
                            color = colors.text,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
        Hairline()
    }
}
