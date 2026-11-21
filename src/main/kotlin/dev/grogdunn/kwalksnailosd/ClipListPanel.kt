package dev.grogdunn.kwalksnailosd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.grogdunn.kwalksnailosd.clip.ClipEntry
import dev.grogdunn.kwalksnailosd.clip.ClipList
import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.clips_column_duration
import dev.grogdunn.kwalksnailosd.generated.resources.clips_column_fps
import dev.grogdunn.kwalksnailosd.generated.resources.clips_column_osd
import dev.grogdunn.kwalksnailosd.generated.resources.clips_column_resolution
import dev.grogdunn.kwalksnailosd.generated.resources.clips_column_srt
import dev.grogdunn.kwalksnailosd.generated.resources.clips_drag_handle
import dev.grogdunn.kwalksnailosd.generated.resources.clips_exclude
import dev.grogdunn.kwalksnailosd.generated.resources.clips_fps
import dev.grogdunn.kwalksnailosd.generated.resources.clips_mismatch_both
import dev.grogdunn.kwalksnailosd.generated.resources.clips_mismatch_fps
import dev.grogdunn.kwalksnailosd.generated.resources.clips_mismatch_resolution
import dev.grogdunn.kwalksnailosd.generated.resources.clips_move_down
import dev.grogdunn.kwalksnailosd.generated.resources.clips_move_up
import dev.grogdunn.kwalksnailosd.generated.resources.clips_no
import dev.grogdunn.kwalksnailosd.generated.resources.clips_resolution
import dev.grogdunn.kwalksnailosd.generated.resources.clips_restore_order
import dev.grogdunn.kwalksnailosd.generated.resources.clips_total_duration
import dev.grogdunn.kwalksnailosd.generated.resources.clips_yes
import dev.grogdunn.kwalksnailosd.generated.resources.file_error
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ClipListPanel(stateHolder: AppStateHolder) {
    val state = stateHolder.state
    val clipList = state.clipList ?: return
    val editsEnabled = !state.isBurning
    val yes = stringResource(Res.string.clips_yes)
    val no = stringResource(Res.string.clips_no)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = stateHolder::restoreClipOrder,
            enabled = editsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.clips_restore_order), fontSize = 12.sp)
        }
        Text(
            text = stringResource(Res.string.clips_total_duration, formatDuration(clipList.totalDuration())),
            style = MaterialTheme.typography.caption,
        )
        MismatchMessage(clipList)
        Text(
            text = listOf(
                stringResource(Res.string.clips_column_resolution),
                stringResource(Res.string.clips_column_fps),
                stringResource(Res.string.clips_column_duration),
                stringResource(Res.string.clips_column_osd),
                stringResource(Res.string.clips_column_srt),
            ).joinToString(" · "),
            style = MaterialTheme.typography.caption,
            color = Color.Gray,
        )
        clipList.entries.forEachIndexed { index, entry ->
            key(entry.id) {
                ClipRow(
                    stateHolder = stateHolder,
                    clipList = clipList,
                    entry = entry,
                    index = index,
                    lastIndex = clipList.entries.lastIndex,
                    editsEnabled = editsEnabled,
                    yes = yes,
                    no = no,
                )
            }
        }
    }
}

@Composable
private fun MismatchMessage(clipList: ClipList) {
    val mismatch = clipList.mismatch()
    if (!mismatch.resolution && !mismatch.fps) return
    val message = when {
        mismatch.resolution && mismatch.fps -> stringResource(Res.string.clips_mismatch_both)
        mismatch.resolution -> stringResource(Res.string.clips_mismatch_resolution)
        else -> stringResource(Res.string.clips_mismatch_fps)
    }
    Text(message, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
}

@Composable
private fun ClipRow(
    stateHolder: AppStateHolder,
    clipList: ClipList,
    entry: ClipEntry,
    index: Int,
    lastIndex: Int,
    editsEnabled: Boolean,
    yes: String,
    no: String,
) {
    val info = entry.info
    val video = info.videoInfo
    val highlighted = clipList.highlighted(entry.id)
    val rowHeightPx = remember(entry.id) { mutableFloatStateOf(1f) }
    val borderColor = if (highlighted) MaterialTheme.colors.error else Color(0xFF2C2C2C)
    val background = if (highlighted) MaterialTheme.colors.error.copy(alpha = 0.15f) else MaterialTheme.colors.surface
    val excludeLabel = stringResource(Res.string.clips_exclude)
    val dragLabel = stringResource(Res.string.clips_drag_handle)
    val moveUpLabel = stringResource(Res.string.clips_move_up)
    val moveDownLabel = stringResource(Res.string.clips_move_down)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeightPx.floatValue = it.height.toFloat() }
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DragHandle(
                label = dragLabel,
                enabled = editsEnabled,
                rowHeightPx = rowHeightPx,
                onMoveUp = { stateHolder.moveClipBy(entry.id, down = false) },
                onMoveDown = { stateHolder.moveClipBy(entry.id, down = true) },
            )
            NudgeButton(
                label = "↑",
                description = moveUpLabel,
                enabled = editsEnabled && index > 0,
                onClick = { stateHolder.moveClipUp(entry.id) },
            )
            NudgeButton(
                label = "↓",
                description = moveDownLabel,
                enabled = editsEnabled && index < lastIndex,
                onClick = { stateHolder.moveClipDown(entry.id) },
            )
            Checkbox(
                checked = entry.excluded,
                onCheckedChange = { stateHolder.setClipExcluded(entry.id, it) },
                enabled = editsEnabled,
                modifier = Modifier.semantics { contentDescription = excludeLabel },
            )
            Text(
                text = info.files.baseName,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = clipSummary(video, info.osd != null, info.srt != null, yes, no),
            style = MaterialTheme.typography.caption,
            color = if (entry.excluded) Color.Gray else MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        info.probeError?.let { ClipError(it) }
        info.osdError?.let { ClipError(it) }
        info.srtError?.let { ClipError(it) }
    }
}

@Composable
private fun clipSummary(
    video: dev.grogdunn.kwalksnailosd.video.VideoInfo?,
    hasOsd: Boolean,
    hasSrt: Boolean,
    yes: String,
    no: String,
): String {
    val resolution = if (video == null) "—" else stringResource(Res.string.clips_resolution, video.width, video.height)
    val fps = if (video == null) "—" else stringResource(Res.string.clips_fps, "%.2f".format(video.frameRate))
    val duration = if (video == null) "—" else formatDuration(video.duration)
    val osd = stringResource(Res.string.clips_column_osd) + " " + if (hasOsd) yes else no
    val srt = stringResource(Res.string.clips_column_srt) + " " + if (hasSrt) yes else no
    return listOf(resolution, fps, duration, osd, srt).joinToString(" · ")
}

@Composable
private fun ClipError(message: String) {
    Text(
        text = stringResource(Res.string.file_error, message),
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.error,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun NudgeButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(36.dp).height(28.dp).semantics { contentDescription = description },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun DragHandle(
    label: String,
    enabled: Boolean,
    rowHeightPx: androidx.compose.runtime.MutableFloatState,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
) {
    Text(
        text = "⋮⋮",
        color = if (enabled) MaterialTheme.colors.onSurface else Color.Gray,
        modifier = Modifier
            .semantics { contentDescription = label }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var accumulated = 0f
                    drag(down.id) { change ->
                        val delta = change.positionChange().y
                        change.consume()
                        val threshold = (rowHeightPx.floatValue + 8f).coerceAtLeast(1f)
                        accumulated += delta
                        while (accumulated >= threshold && onMoveDown()) {
                            accumulated -= threshold
                        }
                        while (accumulated <= -threshold && onMoveUp()) {
                            accumulated += threshold
                        }
                    }
                }
            },
    )
}

private fun AppStateHolder.moveClipBy(id: String, down: Boolean): Boolean {
    val before = state.clipList?.entries?.map { it.id } ?: return false
    if (down) moveClipDown(id) else moveClipUp(id)
    return state.clipList?.entries?.map { it.id } != before
}
