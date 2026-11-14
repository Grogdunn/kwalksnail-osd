# Walksnail OSD

Desktop preview for a Walksnail Avatar DVR. Open the `.mp4`, the matching `.osd`, and a PNG OSD font, then move the frame slider to draw the flight-controller OSD on that frame.

The downloadable builds include a Java runtime and the ffmpeg libraries used to read the video. You do not install Java or ffmpeg yourself.

## Download

Published files are attached to a GitHub Release. A release created from a `v*` tag starts as a draft: download it from the release page once it is published. These builds are not signed or notarized.

Tag names that produce a release must look like `v1.2.3`.

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

## Build from source

The project needs JDK 25. `./gradlew run` starts the app, and `./gradlew createDistributable` builds an app image for the machine you are on.

## Support

If this project is useful, you can support the work on [Ko-fi](https://ko-fi.com/grogdunn) or [GitHub Sponsors](https://github.com/sponsors/Grogdunn). GitHub Sponsors may stay unavailable until GitHub approves the sponsorship profile.

## pstack

pstack è incluso senza modifiche in `.cursor/skills/pstack/` (versione upstream 0.15.36, commit `bb68f2365eceafa0cb2d253112f675acf5f144cc`). I modelli sono fissati su Grok in `.cursor/rules/pstack-models.mdc`. Per aggiornare, sostituire la cartella con una copia intatta dell'upstream.
