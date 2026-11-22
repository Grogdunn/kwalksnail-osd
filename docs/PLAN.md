# Development round: multi-clip burn and OSD-only export

Working branch: `feature/multi-clip-osd-export`. One PR at the end of the round.
Tick the boxes as each part lands.

## 1. Prerequisites

- [x] Case-insensitive file extensions in `lookupFiles` (`.MP4`, `.Osd`, `.SRT`, ...).
- [x] Close the `Files.list` stream (`use {}`).
- [x] Faster OSD and SRT lookup per frame: no linear `findLast` per frame (binary search on the sorted timestamps or a moving index).
- [x] Show a localized error in the UI (en/it/es/de/fr) when a video can't be read, instead of crashing or failing silently.
- [x] Unit tests for the lookup and the extensions.

## 2. New SRT formats

Upstream: [avsaase/walksnail-osd-tool](https://github.com/avsaase/walksnail-osd-tool) issues #62, #65 and #71. A sample zip is attached to #71.

- [x] Parse the formats from #62, #65 and #71 (use the #71 samples as test fixtures).
- [x] Keep the existing SRT format working (regression tests).
- [x] Localized error for unknown or unreadable SRT content.

## 3. Multi-clip burn from a folder

Scanner and list:

- [x] Scan a folder and group video, `.osd` and `.srt` files by base name.
- [x] Default order: lexicographic by name.
- [x] Reorder with a drag handle and with up/down buttons.
- [x] "Restore A→Z" button.
- [x] Per-clip "exclude" checkbox.
- [x] Per-row info: resolution, fps, duration, OSD present, SRT present.
- [x] Total duration of the included clips.

Validation:

- [x] Resolution or fps mismatch between included clips is highlighted on the rows and listed in a message.
- [x] Burn is disabled while there is a mismatch.

Output and rendering:

- [x] Output file `<firstclip>_joined` in the output folder.
- [x] Global OSD offset applied to every clip.
- [x] Concatenated AAC audio, with silence for clips that have no audio.
- [x] Live preview, downscaled, updated about every 0.5 s and throttled.
- [x] Progress as "clip n/N", percentage and ETA, plus a Cancel button.

Quality:

- [x] UI strings in all 5 languages (en/it/es/de/fr).
- [x] Single-video mode unchanged (regression tests).

## 4. OSD-only export

Upstream issue #7 of avsaase/walksnail-osd-tool.

- [x] Chroma key: user-chosen color, default magenta, no antialiasing, any codec.
- [x] True alpha: ProRes 4444 `.mov` or VP9 alpha `.webm`.
- [x] PNG sequence with alpha, zero-padded names, in a subfolder.
- [x] Disk-space warning before writing the PNG sequence.
- [x] UI strings in all 5 languages (en/it/es/de/fr).

## Deferred (not in this round)

- Automatic scale/pad to make mismatching clips compatible (round 2, part 3).
- Per-clip OSD offset (round 2, part 2).
- Batch mode (upstream #24) (round 2, part 1).

# Round 2

One feature per branch and PR, in this order.

## 1. Batch mode

Upstream: [avsaase/walksnail-osd-tool](https://github.com/avsaase/walksnail-osd-tool) issue #24. Branch `feature/batch-mode`.

- [x] Pick several videos at once (multi-file dialog); each gets its `.osd`/`.srt` companion by base name.
- [x] Folder or multi-file list can be burned as "Join into one video" (as before) or "Convert each separately".
- [x] Separate outputs `<name>_with_osd.mp4` in a chosen folder; never overwrite an existing file or another job's output (`_2`, `_3`, ...).
- [x] Jobs run one at a time in list order; excluded clips are skipped.
- [x] Per-job status in the clip list (waiting, percent, done, failed with reason, cancelled) plus overall progress.
- [x] A failing job (unreadable video, missing OSD, encoder error) does not stop the others; summary at the end.
- [x] Cancel stops the current job and the ones not started; finished outputs are kept.
- [x] Resolution/fps mismatch does not block batch mode (each output is independent).
- [x] UI strings in all 5 languages (en/it/es/de/fr).
- [x] Tests: batch burner (fake and real ffmpeg), unique names, multi-file scan, holder batch flow.

## 2. Per-clip OSD offset

Branch `feature/per-clip-osd-offset`.

- [x] Optional own OSD offset per clip in the clip list ("Own OSD offset", X/Y nudges −10/−1/+1/+10); clips without it use the global offset.
- [x] Applied to the joined burn and to batch mode.
- [x] Offset survives reorder, exclude and "Restore A→Z".
- [x] Clip preview in folder/multi-file mode (click the name or "Preview"); the preview shows that clip's effective offset and its own SRT.
- [x] Export controls (global offset, encoder, bitrate) are usable in folder/multi-file mode (they were disabled without a single video).
- [x] UI strings in all 5 languages (en/it/es/de/fr).
- [x] Tests: model, burners (real ffmpeg: clip 2 OSD shifted), holder.

## 3. Automatic scale/pad

- [ ] Scale/pad clips with different resolutions to a common size.
