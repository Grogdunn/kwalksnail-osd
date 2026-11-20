# Development round: multi-clip burn and OSD-only export

Working branch: `feature/multi-clip-osd-export`. One PR at the end of the round.
Tick the boxes as each part lands.

## 1. Prerequisites

- [ ] Case-insensitive file extensions in `lookupFiles` (`.MP4`, `.Osd`, `.SRT`, ...).
- [ ] Close the `Files.list` stream (`use {}`).
- [ ] Faster OSD and SRT lookup per frame: no linear `findLast` per frame (binary search on the sorted timestamps or a moving index).
- [ ] Show a localized error in the UI (en/it/es/de/fr) when a video can't be read, instead of crashing or failing silently.
- [ ] Unit tests for the lookup and the extensions.

## 2. New SRT formats

Upstream: [avsaase/walksnail-osd-tool](https://github.com/avsaase/walksnail-osd-tool) issues #62, #65 and #71. A sample zip is attached to #71.

- [x] Parse the formats from #62, #65 and #71 (use the #71 samples as test fixtures).
- [x] Keep the existing SRT format working (regression tests).
- [ ] Localized error for unknown or unreadable SRT content.

## 3. Multi-clip burn from a folder

Scanner and list:

- [ ] Scan a folder and group video, `.osd` and `.srt` files by base name.
- [ ] Default order: lexicographic by name.
- [ ] Reorder with a drag handle and with up/down buttons.
- [ ] "Restore A→Z" button.
- [ ] Per-clip "exclude" checkbox.
- [ ] Per-row info: resolution, fps, duration, OSD present, SRT present.
- [ ] Total duration of the included clips.

Validation:

- [ ] Resolution or fps mismatch between included clips is highlighted on the rows and listed in a message.
- [ ] Burn is disabled while there is a mismatch.

Output and rendering:

- [ ] Output file `<firstclip>_joined` in the output folder.
- [ ] Global OSD offset applied to every clip.
- [ ] Concatenated AAC audio, with silence for clips that have no audio.
- [ ] Live preview, downscaled, updated about every 0.5 s and throttled.
- [ ] Progress as "clip n/N", percentage and ETA, plus a Cancel button.

Quality:

- [ ] UI strings in all 5 languages (en/it/es/de/fr).
- [ ] Single-video mode unchanged (regression tests).

## 4. OSD-only export

Upstream issue #7 of avsaase/walksnail-osd-tool.

- [ ] Chroma key: user-chosen color, default magenta, no antialiasing, any codec.
- [ ] True alpha: ProRes 4444 `.mov` or VP9 alpha `.webm`.
- [ ] PNG sequence with alpha, zero-padded names, in a subfolder.
- [ ] Disk-space warning before writing the PNG sequence.
- [ ] UI strings in all 5 languages (en/it/es/de/fr).

## Deferred (not in this round)

- Automatic scale/pad to make mismatching clips compatible.
- Per-clip OSD offset.
- Batch mode (upstream #24).
