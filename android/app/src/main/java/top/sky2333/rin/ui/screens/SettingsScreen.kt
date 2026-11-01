package top.sky2333.rin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import top.sky2333.rin.data.AppSettings
import top.sky2333.rin.data.User
import top.sky2333.rin.data.defaultTermStart
import top.sky2333.rin.ui.AppViewModel
import top.sky2333.rin.ui.Identity
import top.sky2333.rin.ui.components.Avatar
import top.sky2333.rin.ui.components.Hairline
import top.sky2333.rin.ui.components.PaperPage
import top.sky2333.rin.ui.components.RinMasthead
import top.sky2333.rin.ui.components.SectionHeader
import top.sky2333.rin.ui.components.SuiSecondaryButton
import top.sky2333.rin.ui.components.ThemePickerSheet
import top.sky2333.rin.ui.schedule.todayAtCampusClock
import top.sky2333.rin.ui.theme.LocalRinColors
import top.sky2333.rin.ui.theme.ThemeCatalog
import top.sky2333.rin.ui.theme.ThemeCorners
import top.sky2333.rin.ui.theme.ThemeMode

private enum class SettingsPage { HOME, APPEARANCE, CARDS, RIN, FACES, REMINDERS, PROFILE, ABOUT }

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit
) {
    val live by vm.settings.collectAsStateWithLifecycle()
    val current = live ?: settings
    val colors = LocalRinColors.current
    val today = todayAtCampusClock()
    val displayName = Identity.displayName(current, "R")
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    var themePickerOpen by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCustomThemeCover(uri)
    }

    when (page) {
        SettingsPage.HOME -> PaperPage {
            val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = pageInset, end = pageInset, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item("head") {
                    RinMasthead(
                        dateLine = "本地优先 · 第 ${scheduleWeek(vm)} 周",
                        title = "我的",
                        avatarText = displayName,
                        onRefresh = { },
                        onTheme = onToggleTheme,
                        onAvatarClick = { page = SettingsPage.PROFILE }
                    )
                }
                item("profile") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(colors.surface)
                            .border(1.dp, colors.border, MaterialTheme.shapes.large)
                            .clickable { page = SettingsPage.PROFILE }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(displayName, size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                color = colors.text,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "头像、称呼与隐私",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text("›", color = colors.textSecondary, style = MaterialTheme.typography.titleLarge)
                    }
                }
                item("appearance-title") { SectionHeader("外观") }
                item("appearance") {
                    SettingsNavigationCard(
                        title = "主题与外观",
                        summary = "${themeMode.spec.name} · ${themeMode.spec.en}",
                        onClick = { page = SettingsPage.APPEARANCE }
                    )
                }
                item("cards") {
                    SettingsNavigationCard(
                        title = "今日卡片",
                        summary = "开关与顺序，只留每天想看的内容",
                        onClick = { page = SettingsPage.CARDS }
                    )
                }
                item("rin") {
                    SettingsNavigationCard(
                        title = "Rin 与表情",
                        summary = if (current.petEnabled) "首页陪伴 · 已开启" else "首页陪伴 · 已关闭",
                        onClick = { page = SettingsPage.RIN }
                    )
                }
                item("reminder-title") { SectionHeader("提醒") }
                item("reminders") {
                    SettingsNavigationCard(
                        title = "上课与签到提醒",
                        summary = "铃声、音量、震动与权限",
                        onClick = { page = SettingsPage.REMINDERS }
                    )
                }
                item("term-title") { SectionHeader("课表") }
                item("term") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(colors.surface)
                            .border(1.dp, colors.border, MaterialTheme.shapes.large)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("学期起点", color = colors.text, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (current.termStartDate.isBlank()) "未设置" else current.termStartDate,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        SuiSecondaryButton(
                            text = "把本周设为第 1 周",
                            onClick = { vm.setTermStart(defaultTermStart(today)) },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                item("about-title") { SectionHeader("关于") }
                item("about") {
                    SettingsNavigationCard(
                        title = "关于 Rin",
                        summary = "版本 ${vm.appVersion} · 隐私说明",
                        onClick = { page = SettingsPage.ABOUT }
                    )
                }
            }
        }

        SettingsPage.APPEARANCE -> SettingsSubpage("主题与外观", "颜色、圆角与动效", { page = SettingsPage.HOME }) {
            item("theme") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("主题", themeMode.spec.name)
                    Text(
                        text = themeMode.spec.blurb,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    SuiSecondaryButton(
                        text = "更换主题",
                        onClick = { themePickerOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item("accent") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("强调色", if (current.themeAccent.isBlank()) "跟随主题" else current.themeAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeCatalog.accentPresets.take(4).forEach { preset ->
                            AccentChip(
                                label = preset.label,
                                selected = current.themeAccent == preset.hex,
                                onClick = { vm.setThemeAccent(preset.hex) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeCatalog.accentPresets.drop(4).forEach { preset ->
                            AccentChip(
                                label = preset.label,
                                selected = current.themeAccent == preset.hex,
                                onClick = { vm.setThemeAccent(preset.hex) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item("corners") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("圆角")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeCorners.entries.forEach { corners ->
                            AccentChip(
                                label = corners.label,
                                selected = current.themeCorners == corners.key,
                                onClick = { vm.setThemeCorners(corners.key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item("switches") {
                Column {
                    PaperSwitchRow(
                        title = "主题装饰",
                        description = "保留主题自带的线条、贴纸与纹理",
                        checked = current.themeDecorations,
                        onCheckedChange = vm::setThemeDecorations,
                        contentDescription = "主题装饰"
                    )
                    Hairline()
                    PaperSwitchRow(
                        title = "动效",
                        description = "页面转场与 Rin 动作",
                        checked = current.animationsEnabled,
                        onCheckedChange = vm::setAnimationsEnabled,
                        contentDescription = "动效"
                    )
                    Hairline()
                    PaperSwitchRow(
                        title = "启动页",
                        description = "打开 App 时显示 Rin 启动页",
                        checked = current.splashEnabled,
                        onCheckedChange = vm::setSplashEnabled,
                        contentDescription = "启动页"
                    )
                }
            }
            item("cover") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("自定义封面", if (current.customThemeEnabled) "已启用" else "跟随主题")
                    Text(
                        text = "选一张自己的照片作为主题封面，颜色会从画面里取。",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuiSecondaryButton(
                            text = if (current.customThemeEnabled) "更换封面" else "选择封面",
                            onClick = {
                                runCatching {
                                    coverPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }.onFailure { vm.notify("这台设备没有可用的图片选择器") }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (current.customThemeEnabled) {
                            SuiSecondaryButton(
                                text = "恢复主题封面",
                                onClick = vm::clearCustomThemeCover,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        SettingsPage.CARDS -> SettingsTodayCardsContent(
            settings = current,
            onBack = { page = SettingsPage.HOME },
            onToggle = vm::setTodayCardEnabled,
            onMove = vm::moveTodayCard,
            onReset = vm::resetTodayCards
        )

        SettingsPage.RIN -> SettingsSubpage("Rin 与表情", "陪伴、尺度与通知表情", { page = SettingsPage.HOME }) {
            item("switches") {
                Column {
                    PaperSwitchRow(
                        title = "首页 Rin",
                        description = "在今日页放一只 Rin",
                        checked = current.petEnabled,
                        onCheckedChange = vm::setPetEnabled,
                        contentDescription = "首页 Rin"
                    )
                    Hairline()
                    PaperSwitchRow(
                        title = "情境动作",
                        description = "根据时间与天气换动作",
                        checked = current.petContextAware,
                        onCheckedChange = vm::setPetContextAware,
                        contentDescription = "情境动作"
                    )
                    Hairline()
                    PaperSwitchRow(
                        title = "天气影响",
                        description = "用天气决定 Rin 的穿着和动作",
                        checked = current.petWeatherEnabled,
                        onCheckedChange = vm::setPetWeatherEnabled,
                        contentDescription = "天气影响"
                    )
                }
            }
            item("scale") {
                PetSliderRow(
                    title = "大小",
                    value = current.petScale,
                    range = 0.6f..1.4f,
                    onChange = vm::setPetScale
                )
            }
            item("offset-x") {
                PetSliderRow(
                    title = "左右位置",
                    value = current.petOffsetX,
                    range = -0.35f..0.35f,
                    onChange = vm::setPetOffsetX
                )
            }
            item("offset-y") {
                PetSliderRow(
                    title = "上下位置",
                    value = current.petOffsetY,
                    range = -0.35f..0.35f,
                    onChange = vm::setPetOffsetY
                )
            }
            item("faces") {
                SettingsNavigationCard(
                    title = "通知表情",
                    summary = "套装、圆底与提醒场景",
                    onClick = { page = SettingsPage.FACES }
                )
            }
        }

        SettingsPage.FACES -> SettingsFacesContent(vm = vm, onBack = { page = SettingsPage.RIN })

        SettingsPage.REMINDERS -> SettingsRemindersContent(
            settings = current,
            onBack = { page = SettingsPage.HOME },
            onSound = vm::setReminderSound,
            onVolume = vm::setReminderVolume,
            onVibrate = vm::setReminderVibrate,
            onVibrationMode = vm::setReminderVibrationMode,
            onHeadsUp = vm::setReminderHeadsUp
        )

        SettingsPage.PROFILE -> SettingsProfileContent(
            vm = vm,
            settings = current,
            user = User(username = "R", displayName = "R"),
            onBack = { page = SettingsPage.HOME }
        )

        SettingsPage.ABOUT -> SettingsSubpage("关于 Rin", "版本与隐私", { page = SettingsPage.HOME }) {
            item("version") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaperInfoRow("Rin 校园助手", "版本 ${vm.appVersion}")
                    PaperInfoRow("定位", "本地优先的课表、提醒与校园生活客户端")
                    PaperInfoRow("数据", "课表、提醒、头像与封面只保存在本机")
                    PaperInfoRow("权限", "只有天气需要网络；提醒使用系统通知与闹钟")
                }
            }
            item("note") {
                Text(
                    text = "Rin 不读取任何账号，也不上传个人课表；提醒只在本机完成。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (themePickerOpen) {
        ThemePickerSheet(
            selected = themeMode,
            onSelect = {
                vm.setTheme(it)
                themePickerOpen = false
            },
            onDismiss = { themePickerOpen = false }
        )
    }
}

@Composable
private fun AccentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.signal.copy(alpha = 0.16f) else colors.surface)
            .border(1.dp, if (selected) colors.signal else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.signal else colors.text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PetSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val colors = LocalRinColors.current
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = colors.text, style = MaterialTheme.typography.bodyMedium)
            Text("${(draft * 100).toInt()}%", color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onChange(draft) },
            valueRange = range
        )
    }
}

private fun scheduleWeek(vm: AppViewModel): Int =
    vm.schedule.value.overview?.week ?: vm.schedule.value.currentWeek
