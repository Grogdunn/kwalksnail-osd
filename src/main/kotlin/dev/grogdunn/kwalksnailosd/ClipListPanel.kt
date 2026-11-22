package dev.grogdunn.kwalksnailosd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.LinearProgressIndicator
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
import dev.grogdunn.kwalksnailosd.clip.BatchJobState
import dev.grogdunn.kwalksnailosd.clip.ClipEntry
import dev.grogdunn.kwalksnailosd.clip.ClipList
import dev.grogdunn.kwalksnailosd.video.OsdOffset
import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.batch_state_cancelled
import dev.grogdunn.kwalksnailosd.generated.resources.batch_state_done
import dev.grogdunn.kwalksnailosd.generated.resources.batch_state_failed
import dev.grogdunn.kwalksnailosd.generated.resources.batch_state_pending
import dev.grogdunn.kwalksnailosd.generated.resources.batch_state_running
import dev.grogdunn.kwalksnailosd.generated.resources.clips_batch_hint
import dev.grogdunn.kwalksnailosd.generated.resources.clips_column_duration
import dev.grogdunn.kwalksnailosd.generated.resources.clips_mode_batch
import dev.grogdunn.kwalksnailosd.generated.resources.clips_mode_join
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
import dev.grogdunn.kwalksnailosd.generated.resources.clips_offset_x
import dev.grogdunn.kwalksnailosd.generated.resources.clips_offset_y
import dev.grogdunn.kwalksnailosd.generated.resources.clips_own_offset
import dev.grogdunn.kwalksnailosd.generated.resources.clips_preview
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ClipModeButton(
                label = stringResource(Res.string.clips_mode_join),
                selected = !state.batchMode,
                enabled = editsEnabled,
                onClick = { stateHolder.setBatchMode(false) },
            )
            ClipModeButton(
                label = stringResource(Res.string.clips_mode_batch),
                selected = state.batchMode,
                enabled = editsEnabled,
                onClick = { stateHolder.setBatchMode(true) },
            )
        }
        if (state.batchMode) {
            Text(
                text = stringResource(Res.string.clips_batch_hint),
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
            )
        }
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
        if (!state.batchMode) MismatchMessage(clipList)
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
                    highlightMismatches = !state.batchMode,
                )
            }
        }
    }
}

@Composable
private fun ClipModeButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent,
        ),
    ) {
        Text(label, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    highlightMismatches: Boolean = true,
) {
    val info = entry.info
    val video = info.videoInfo
    val highlighted = highlightMismatches && clipList.highlighted(entry.id)
    val previewed = entry.id == stateHolder.state.previewClipId
    val canPreview = editsEnabled && video != null && info.osd != null
    val rowHeightPx = remember(entry.id) { mutableFloatStateOf(1f) }
    val borderColor = when {
        highlighted -> MaterialTheme.colors.error
        previewed -> MaterialTheme.colors.primary
        else -> Color(0xFF2C2C2C)
    }
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
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = canPreview) { stateHolder.previewClip(entry.id) },
            )
            OutlinedButton(
                onClick = { stateHolder.previewClip(entry.id) },
                enabled = canPreview,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(stringResource(Res.string.clips_preview), fontSize = 11.sp, maxLines = 1)
            }
        }
        Text(
            text = clipSummary(video, info.osd != null, info.srt != null, yes, no),
            style = MaterialTheme.typography.caption,
            color = if (entry.excluded) Color.Gray else MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = entry.offset != null,
                onCheckedChange = { stateHolder.enableClipOffset(entry.id, it) },
                enabled = editsEnabled,
            )
            Text(stringResource(Res.string.clips_own_offset), style = MaterialTheme.typography.caption)
        }
        entry.offset?.let { offset ->
            ClipOffsetAxis(
                label = stringResource(Res.string.clips_offset_x, offset.x),
                enabled = editsEnabled,
                onNudge = { delta -> stateHolder.setClipOffset(entry.id, nudgeOffset(offset, delta, 0)) },
            )
            ClipOffsetAxis(
                label = stringResource(Res.string.clips_offset_y, offset.y),
                enabled = editsEnabled,
                onNudge = { delta -> stateHolder.setClipOffset(entry.id, nudgeOffset(offset, 0, delta)) },
            )
        }
        if (stateHolder.state.batchMode) {
            BatchJobCaption(stateHolder.state.batchJobs[entry.id])
        }
        info.probeError?.let { ClipError(it) }
        info.osdError?.let { ClipError(it) }
        info.srtError?.let { ClipError(it) }
    }
}

@Composable
private fun BatchJobCaption(job: BatchJobState?) {
    val kind = batchStatusKind(job) ?: return
    val text = when (kind) {
        BatchStatusKind.Pending -> stringResource(Res.string.batch_state_pending)
        is BatchStatusKind.Running -> stringResource(Res.string.batch_state_running, kind.percent)
        is BatchStatusKind.Done -> stringResource(Res.string.batch_state_done, kind.fileName)
        is BatchStatusKind.Failed -> stringResource(Res.string.batch_state_failed, kind.message)
        BatchStatusKind.Cancelled -> stringResource(Res.string.batch_state_cancelled)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = if (kind is BatchStatusKind.Failed) MaterialTheme.colors.error else MaterialTheme.colors.onSurface,
        maxLines = if (kind is BatchStatusKind.Failed) 3 else 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (kind is BatchStatusKind.Running) {
        LinearProgressIndicator(
            progress = kind.fraction.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
    }
}

internal sealed interface BatchStatusKind {
    data object Pending : BatchStatusKind
    data class Running(val fraction: Float, val percent: Int) : BatchStatusKind
    data class Done(val fileName: String) : BatchStatusKind
    data class Failed(val message: String) : BatchStatusKind
    data object Cancelled : BatchStatusKind
}

internal fun batchStatusKind(state: BatchJobState?): BatchStatusKind? = when (state) {
    null -> null
    BatchJobState.Pending -> BatchStatusKind.Pending
    is BatchJobState.Running -> BatchStatusKind.Running(
        fraction = state.fraction,
        percent = (state.fraction.coerceIn(0f, 1f) * 100f + 0.5f).toInt().coerceIn(0, 100),
    )
    is BatchJobState.Done -> BatchStatusKind.Done(state.result.output.fileName.toString())
    is BatchJobState.Failed -> BatchStatusKind.Failed(state.message)
    BatchJobState.Cancelled -> BatchStatusKind.Cancelled
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
private fun ClipOffsetAxis(label: String, enabled: Boolean, onNudge: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.caption, maxLines = 1)
        listOf(-10, -1, 1, 10).forEach { delta ->
            val text = if (delta < 0) "−${-delta}" else "+$delta"
            NudgeButton(label = text, description = text, enabled = enabled, onClick = { onNudge(delta) })
        }
    }
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

internal const val OSD_OFFSET_MIN = -200
internal const val OSD_OFFSET_MAX = 700

internal fun nudgeOffset(offset: OsdOffset, dx: Int, dy: Int): OsdOffset =
    offset.copy(
        x = (offset.x + dx).coerceIn(OSD_OFFSET_MIN, OSD_OFFSET_MAX),
        y = (offset.y + dy).coerceIn(OSD_OFFSET_MIN, OSD_OFFSET_MAX),
    )

private fun AppStateHolder.moveClipBy(id: String, down: Boolean): Boolean {
    val before = state.clipList?.entries?.map { it.id } ?: return false
    if (down) moveClipDown(id) else moveClipUp(id)
    return state.clipList?.entries?.map { it.id } != before
}
