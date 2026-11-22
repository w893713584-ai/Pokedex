package com.idex.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.idex.android.DexViewModel
import com.idex.android.audio.DeviceSound
import com.idex.android.audio.VoiceStyle
import com.idex.android.data.AppLanguage

@Composable
fun CatalogSheet(
    vm: DexViewModel,
    compact: Boolean,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var seenOnly by remember { mutableStateOf(false) }
    var selectedGen by remember { mutableStateOf<Int?>(null) }

    Dialog(
        onDismissRequest = { vm.showingCatalog = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DexTheme.charcoal),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PixelText(
                        text = vm.text("NATIONAL ARCHIVE", "全国图鉴", "全国図鑑"),
                        size = 10f,
                        language = vm.language,
                        color = DexTheme.cream,
                    )
                    IconButton(
                        onClick = {
                            vm.audio.play(DeviceSound.BUTTON)
                            vm.showingCatalog = false
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = vm.text("Close", "关闭", "閉じる"),
                            tint = DexTheme.cream,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Search Box
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = vm.text("Name, #0025 or type", "名称、编号或属性", "名前・番号・タイプ"),
                            fontSize = 12.sp,
                            color = DexTheme.cream.copy(alpha = 0.5f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = DexTheme.cream.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = DexTheme.cream.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DexTheme.cream,
                        unfocusedTextColor = DexTheme.cream,
                        focusedBorderColor = DexTheme.lcd,
                        unfocusedBorderColor = DexTheme.cream.copy(alpha = 0.3f),
                        cursorColor = DexTheme.lcd,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(Modifier.height(8.dp))

                // Generation Filter Chips
                val genLabels = listOf(
                    null to vm.text("ALL", "全部", "すべて"),
                    1 to vm.text("GEN I 关都", "第一世代 关都", "第1世代 カントー"),
                    2 to vm.text("GEN II 城都", "第二世代 城都", "第2世代 ジョウト"),
                    3 to vm.text("GEN III 丰缘", "第三世代 丰缘", "第3世代 ホウエン"),
                    4 to vm.text("GEN IV 神奥", "第四世代 神奥", "第4世代 シンオウ"),
                    5 to vm.text("GEN V 合众", "第五世代 合众", "第5世代 イッシュ"),
                    6 to vm.text("GEN VI 卡洛斯", "第六世代 卡洛斯", "第6世代 カロス"),
                    7 to vm.text("GEN VII 阿罗拉", "第七世代 阿罗拉", "第7世代 アローラ"),
                    8 to vm.text("GEN VIII 伽勒尔", "第八世代 伽勒尔", "第8世代 ガラール"),
                    9 to vm.text("GEN IX 帕底亚", "第九世代 帕底亚", "第9世代 パルデア"),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    genLabels.forEach { (gen, label) ->
                        val isSelected = selectedGen == gen
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) DexTheme.lcd else Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    vm.audio.play(DeviceSound.BUTTON)
                                    selectedGen = gen
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) DexTheme.ink else DexTheme.cream,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // "Discovered only" Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = vm.text("Discovered only", "仅显示已发现", "発見済みのみ"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = DexTheme.cream.copy(alpha = 0.85f),
                    )
                    Switch(
                        checked = seenOnly,
                        onCheckedChange = { seenOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DexTheme.cream,
                            checkedTrackColor = DexTheme.teal,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray,
                        ),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 1025 Pokemon Grid
                val filtered = vm.catalog.filter(
                    query = query,
                    language = vm.language,
                    generation = selectedGen,
                    seenOnly = seenOnly,
                    seenIds = vm.seenIds,
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(filtered, key = { it.id }) { pokemon ->
                        val isDiscovered = vm.seenIds.contains(pokemon.id)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DexTheme.lcd),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clickable {
                                    vm.browse(pokemon)
                                    vm.showingCatalog = false
                                    if (compact) vm.showingCompactEntry = true
                                },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PixelText(
                                        text = "#${pokemon.number}",
                                        size = 6f,
                                        language = vm.language,
                                        color = DexTheme.ink,
                                        latinOnly = true,
                                    )
                                    if (isDiscovered) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = DexTheme.ink,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }

                                val sprite = remember(pokemon.id) {
                                    loadAssetBitmap(context, pokemon.spriteAsset)
                                }
                                if (sprite != null) {
                                    Image(
                                        bitmap = sprite,
                                        contentDescription = pokemon.name(vm.language),
                                        filterQuality = FilterQuality.None,
                                        modifier = Modifier.size(64.dp),
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                PixelText(
                                    text = pokemon.name(vm.language),
                                    size = if (vm.language == AppLanguage.EN) 7f else 10f,
                                    language = vm.language,
                                    color = DexTheme.ink,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSheet(vm: DexViewModel) {
    Dialog(
        onDismissRequest = { vm.showingSettings = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DexTheme.charcoal)
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PixelText(
                        text = vm.text("DEVICE SETTINGS", "设备设置", "デバイス設定"),
                        size = 10f,
                        language = vm.language,
                        color = DexTheme.cream,
                    )
                    IconButton(
                        onClick = {
                            vm.audio.play(DeviceSound.BUTTON)
                            vm.showingSettings = false
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = vm.text("Close", "关闭", "閉じる"),
                            tint = DexTheme.cream,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Section: Language
                SectionCard(title = vm.text("LANGUAGE / 语言", "语言 / LANGUAGE", "言語 / LANGUAGE")) {
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    vm.audio.play(DeviceSound.BUTTON)
                                    vm.updateLanguage(lang)
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = lang.title,
                                color = DexTheme.cream,
                                fontSize = 14.sp,
                            )
                            if (vm.language == lang) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = DexTheme.lcd,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Section: Voice Style
                SectionCard(title = vm.text("NARRATOR VOICE / 声线", "图鉴声线 / VOICE", "図鑑ボイス / VOICE")) {
                    VoiceStyle.ALL.forEach { style ->
                        val title = when (style.id) {
                            "anime" -> vm.text("Anime Pokédex", "动画图鉴（小喇叭机械质感）", "アニメ図鑑（メカニカル）")
                            "robot" -> vm.text("Robot (Heavy)", "机械（强金属感）", "ロボット（強い機械感）")
                            else -> vm.text("Natural (System default)", "自然（系统原声）", "ナチュラル（システム原音）")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    vm.audio.play(DeviceSound.BUTTON)
                                    vm.updateVoiceStyle(style.id)
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = title,
                                color = DexTheme.cream,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        vm.audio.play(DeviceSound.BUTTON)
                                        vm.updateVoiceStyle(style.id)
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Preview",
                                        tint = DexTheme.lcd,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                if (vm.voiceStyleId == style.id) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = DexTheme.lcd,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = vm.text(
                            "Tip: For optimal anime Pokédex timbre on Android, install 'Speech Services by Google' and select a male voice in system settings.",
                            "提示：为获得更贴近动画的图鉴声线，建议在应用商店安装「Google 语音服务」并在系统设置中启用中文男声。",
                            "ヒント：よりアニメに近い声線にするには、Google 音声サービスをインストールし男性の声を選択することをお勧めします。"
                        ),
                        color = DexTheme.cream.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Section: Field Guide
                SectionCard(title = vm.text("FIELD GUIDE", "使用指南", "使い方")) {
                    Text(
                        text = vm.text(
                            "• Point camera at Pokémon or import photos\n" +
                                "• Unfold phone for side-by-side research view\n" +
                                "• All recognition runs 100% on-device offline\n" +
                                "• Audio entries narrated in chosen language",
                            "• 将取景器对准宝可梦，或直接导入相册照片\n" +
                                "• 展开折叠屏即可同时查看取景器与图鉴面板\n" +
                                "• 所有识别在设备端本地离线完成，无须联网\n" +
                                "• 支持按所选语言朗读图鉴与叫声播报",
                            "• ポケモンにカメラを向けるか写真を読み込みます\n" +
                                "• 画面を開くとリサーチパネルが横に表示されます\n" +
                                "• すべての認識処理は端末ローカルで完結します\n" +
                                "• 選択した言語で図鑑の読み上げと鳴き声が再生されます"
                        ),
                        color = DexTheme.cream.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Section: Credits & Disclaimer
                SectionCard(title = vm.text("CREDITS", "制作信息", "クレジット")) {
                    PixelText(
                        text = "iDex · FIELD UNIT 001",
                        size = 7f,
                        language = vm.language,
                        color = DexTheme.cream,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = vm.text(
                            "An unofficial fan project. Pokémon and its characters belong to Nintendo, Creatures, and GAME FREAK. Not affiliated with or endorsed by them.",
                            "非官方独立粉丝项目。宝可梦及相关角色版权归任天堂、Creatures 及 GAME FREAK 所有。本项目与其无关联，亦未获得其认可。",
                            "非公式ファンプロジェクトです。ポケモンおよび関連キャラクターの権利は任天堂・クリーチャーズ・ゲームフリークに帰属します。"
                        ),
                        color = DexTheme.cream.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Original iOS iDex: zihang-huang/iDex (MIT)\nData & Cries: PokéAPI & 52Poké\nModel: skshmjn/Pokemon-classifier-gen9-1025 (ViT INT8)\nFonts: Press Start 2P, Fusion Pixel",
                        fontFamily = FontFamily.Monospace,
                        color = DexTheme.cream.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = DexTheme.lcd,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}
