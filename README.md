# Walksnail OSD

Walksnail OSD is a desktop app that draws the flight-controller OSD from a Walksnail Avatar DVR onto the recorded video.

It replaces [Walksnail OSD Tool](https://github.com/avsaase/walksnail-osd-tool) by Alexander van Saase and the other authors of that project. The last release is [v0.3.0](https://github.com/avsaase/walksnail-osd-tool/releases/tag/v0.3.0) (23 March 2024), and the project is no longer maintained. This app reads the same DVR files. The OSD format, the PNG font atlas, and the placement of glyphs on a frame follow that tool, and both projects are licensed under the GNU GPL version 3. See [Credits](#credits) and [NOTICE](NOTICE).

This project is independent of Walksnail and Caddx.

## What the app does today

- Opens a Walksnail DVR `.mp4`, the matching `.osd`, and a PNG OSD font.
- Fills in files that share the same name in the same folder.
- Draws the OSD on the video frame you select.
- Shows the video resolution, frame rate, frame count, duration, and codec.
- Shows the flight-controller tag, the number of OSD frames, and the OSD duration.
- Writes a new MP4 with the OSD drawn into the frames, and keeps the audio.

The downloadable builds include a Java runtime and the FFmpeg libraries used to read the video and to write H.264. You do not install Java. H.265 export needs an `ffmpeg` on your PATH that includes `libx265`. The bundled libraries do not include that encoder.

Bring your own PNG OSD font. Use the font atlas from the aircraft, the same kind of PNG that Betaflight, INAV, and similar tools export. This app does not ship one.

The labels in the window are Italian.

## Burn

**Burn** reads the MP4, draws the OSD on each frame at the `.osd` timestamp, and writes `{name}_with_osd.mp4`. Audio is copied when the source has any.

Choose H.264 or H.265 and a bitrate from 1 to 80 Mbps. If a hardware encoder answers a short probe, the app uses it. If the probe or the encode fails, the app retries with a software encoder and names the encoder that wrote the file. A progress bar and **Annulla** stay on screen, and the window does not freeze.

Offset X and Y move the OSD in pixels. **Centra** frames the glyphs of the current preview frame. With playback correction on, for Walksnail firmware up to 32.37.10, OSD timestamps are scaled by `video duration / OSD duration`.

The encoder is the `ffmpeg` binary from the JavaCV native libraries, the same binary the app images ship. If that binary does not start, the app uses `ffmpeg` on `PATH`. If neither runs, Burn says how to fix it.

For H.264 the app prefers `libx264` when the chosen binary has it, and otherwise uses `libopenh264` from the JavaCV build. OpenH264 follows the requested bitrate less closely. H.265 needs `libx265`.

## Coming soon

The SRT overlay is in progress. It will draw data from the Walksnail `.srt` file on the preview and on the burned video.

The window already shows a field for the `.srt` file. In the current build that file is not drawn.

## Screenshots

TODO: add screenshots of the main window. Show the file list, the frame slider, and a DVR frame with the OSD drawn on it.

## Download

Download the zip for your system from [GitHub Releases](https://github.com/Grogdunn/kwalksnail-osd/releases). A release created from a `v*` tag starts as a draft. Download it from the release page once it is published. Tag names that produce a release look like `v1.2.3`.

These builds are not signed or notarized.

### Windows (x64)

1. Download `walksnail-osd-windows-x64.zip`.
2. Unzip it and open the `Walksnail-osd` folder.
3. Run `Walksnail-osd.exe`.

SmartScreen shows "Windows protected your PC" for this unsigned program. Choose **More info**, then **Run anyway**.

### macOS (Apple Silicon and Intel)

1. Download `walksnail-osd-macos-arm64.zip` on Apple Silicon, or `walksnail-osd-macos-x64.zip` on an Intel Mac.
2. Unzip it and open `Walksnail-osd.app`.

Gatekeeper blocks the unsigned app the first time. Control-click the app, choose **Open**, then **Open** again. The same approval is under **System Settings → Privacy & Security → Open Anyway**.

### Linux (x64)

1. Download `walksnail-osd-linux-x64.zip`.
2. Unzip it and open the `Walksnail-osd` folder.
3. Run `./bin/Walksnail-osd`.

The executable is not signed.

## Use the preview

1. Keep the DVR `.mp4` and the `.osd` file that Walksnail wrote beside it. Keep the original file names. An `.srt` file with the same name can sit in that folder too.
2. Start Walksnail OSD.
3. Open the `.mp4`. When the `.osd` and the `.srt` share that file name and that folder, the app selects them too.
4. Open the PNG OSD font.
5. Move the frame slider. The OSD for that moment is drawn on the frame.

You can also open each file from the **File** menu or from the file list. The menu entries are **Apri Video MP4...**, **Apri File OSD...**, **Apri Sottotitoli SRT...**, and **Apri Font OSD...**.

## Build from source

The project needs JDK 25.

Start the app:

```
./gradlew run
```

On Windows, run `gradlew.bat run`.

Build an app image for the machine you are on:

```
./gradlew createDistributable
```

On Windows, run `gradlew.bat createDistributable`.

## Support

If this project is useful, you can support the work on [Ko-fi](https://ko-fi.com/grogdunn) or [GitHub Sponsors](https://github.com/sponsors/Grogdunn).

## License

Walksnail OSD is free software under the GNU General Public License version 3 only. The full text is in [LICENSE](LICENSE).

Walksnail OSD Tool identifies the same license as `GPL-3.0` and does not grant a later version, so this project does not grant one either.

You may run, study, share, and modify this program. If you distribute a modified version, license that version under GPL-3.0 and keep the notices in [NOTICE](NOTICE).

The packaged FFmpeg libraries are a separate work. Builds include `org.bytedeco:ffmpeg:8.1.2-1.5.14` without the `gpl` classifier. That is Bytedeco's LGPL build of FFmpeg: it contains OpenH264 (`libopenh264`) and it does not contain `libx264` or `libx265`. Those libraries keep their own licenses. The GPL-3.0-only grant of this program does not replace them. Details are in [NOTICE](NOTICE).

## Credits

The OSD file reader, the PNG font-atlas reader, and the placement of glyphs on a frame are a Kotlin translation of the matching code in [Walksnail OSD Tool](https://github.com/avsaase/walksnail-osd-tool) (`backend/src/osd` and `backend/src/font`).

Walksnail OSD Tool is copyright its authors. The package metadata names [Alexander van Saase](https://github.com/avsaase) as the author. The Git history also records these contributors:

- [Maksym Motorny](https://github.com/mmotorny)
- [Janez Troha](https://github.com/dz0ny)
- [Marcelo Bezerra](https://github.com/mmosca)
- [Viacheslav Zhivetyev](https://github.com/vmzhivetyev)

Copyright (C) 2026 [Lorenzo Caenazzo](https://github.com/Grogdunn) for this repository, including the desktop UI, the video reader, and the packaging.

No font files, icons, or other media from Walksnail OSD Tool are included here.

## Repository tooling

pstack is included unchanged in `.cursor/skills/pstack/` (upstream 0.15.36, commit `bb68f2365eceafa0cb2d253112f675acf5f144cc`). Model roles are pinned to Grok in `.cursor/rules/pstack-models.mdc`. To update pstack, replace that folder with an intact copy of upstream. pstack stays under the MIT license in `.cursor/skills/pstack/LICENSE`. It is developer tooling, and it is separate from the desktop app. See [NOTICE](NOTICE).
