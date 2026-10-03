# Scout · field references

An offline field notebook for environment art and lighting reference. Use a phone for observations and a Sony A7CR (or another camera) for original images. Hong Kong is an optional itinerary; trips and spontaneous stops work anywhere.

## Test on your phone

- **Browser app:** [Open Scout](https://devilzhu1985.github.io/hk-scout/) in Chrome on Android or Safari on iPhone. Open once online, then add it to your home screen and test an offline reload.
- **Android phone trial:** download `scout-2.1.0-preview.1-debug.apk` from the [Android test release](https://github.com/Devilzhu1985/hk-scout/releases/tag/v2.1.0-preview.1). This is the complete Scout app with its light meter built in, not a separate light-meter utility. This personal test build includes direct sensor access; browser mode generally uses manual light readings. Actual sensor availability and accuracy still need testing on your phone.
- If the old Hong Kong interface appears, export any existing records, close all tabs/windows of the app and reopen the link. Do not clear site data to update. Once on Scout 2, use **Update app** when offered.
- Web and Android installations have separate local notebooks. Export a field ZIP and import it into the other installation to move records. The APK is a debug build, not an app-store release; export before uninstalling it.

The website and Android package are two editions of Scout. Adding the website to the home screen does not give it Android sensor access. The Lighting panel checks capabilities first: it offers live capture when available, otherwise explains the current limitation and keeps manual entry available. Browser camera brightness is not substituted for lux.

## Start locally

Requires Node.js 22 or newer.

```sh
npm ci
npm run build
npm start
```

Open http://127.0.0.1:4173. The root `index.html`, `app.js`, styles, icons, manifest and service worker also work on a static HTTPS host, including a GitHub Pages project subdirectory. Run the build after editing `src/`; the generated root files must travel with a deployment. No deployment is performed by the build.

For phone browsers, use HTTPS and open the app once online before relying on offline capture. A desktop localhost address does not make the app available on a separate phone. Browser storage is specific to the browser and site address; the Android app has separate storage. Transfer packages between them.

## The shooting workflow

1. **Capture here now.** First use can create a dated trip without typing a city or name. Or choose **Plan a trip** for an itinerary and time zone. Every new stop requests a fresh GPS fix in the background. Coordinates, accuracy and fix time are saved automatically; permission failure never blocks shooting.
2. **Photograph the Sony code.** Photograph the slate with your A7CR, then tap **Code photographed → continue**. Choose **Phone only / skip code** when appropriate. This is your confirmation, not automatic QR recognition.
3. **Take a phone reference.** Use **Take reference photo**, choose an existing image, or skip the phone image when shooting only with Sony. Original phone files are retained separately from rendered previews.
4. **Record lighting if needed.** Measure with an exposed Android light sensor or enter a reading with its instrument and orientation. Quick visual references can **Skip lighting**. The lens cover remains an uncalibrated target; preview color samples are rendered sRGB, not measured CCT or albedo.
5. **Finish stop.** This explicit final step saves the end time for later Sony timestamp matching. Then choose **Start next stop** or **Back up this trip**. **Finish stop now** also lets you end early. Notes and associations remain editable. Beginning another stop while one is open asks before finishing it.
6. **Back up before clearing anything.** Save every exported ZIP part and verify an independent second copy. Packages now include phone originals when available; older records may have previews only. Sony originals remain in your camera/desktop archive.
7. **Copy Sony originals and run the desktop importer.** Import its catalogue ZIPs, add camera clock correction segments in **Library**, inspect the slate and confirm proposed associations. A camera 90 seconds slow needs +90 seconds. Camera time is never rewritten.
8. **Prepare the assistant handoff.** Export the trip's handoff ZIP parts and supply those with your organization priorities. Exports do not automatically upload to an AI service.

The header switches between **English** and **中文 (Simplified Chinese)**. This preference belongs to the device. UI labels, dates and the optional Hong Kong itinerary switch; your names, notes, filenames, canonical roles and original metadata are preserved.

### Automatic location names

GPS alone supplies coordinates, not a reliable venue name. Scout starts with a coordinate label and protects any name you type from later lookup results. The Android app uses its system Geocoder, where available, for a nearby street/area estimate. This may require network and can be inaccurate among tall buildings.

Browser area names are optional: **Enable online area names** explains that it sends the device's current GPS to BigDataCloud. The setting can be disabled under **Trips & kit → Location names**. Requests originate on the device, use only a fresh fix obtained there, and never query imported/manual/history coordinates or fall back to IP. See the [provider's client-side policy](https://www.bigdatacloud.com/docs/article/fair-use-policy-for-free-client-side-reverse-geocoding-api). It returns city/area names, not exact shops. Coordinates still save offline; lookup failures never fabricate a location.

### Phone camera and original files

- **Android Scout:** includes its own Camera2 reference camera; no separate camera utility is required. A rear camera supporting RAW can save DNG plus a processed JPEG companion. Supported white-balance presets are listed. A manual Kelvin input appears only when Android 16 / API 36 **and the camera hardware** expose the CCT mode, range, request and result keys. Requested and reported settings are recorded separately. A Kelvin setting is not a measurement of scene color temperature.
- **Android/iPhone browsers:** **Open phone camera** requests rear-camera capture. The browser/OS may show a chooser. Browsers cannot force the manufacturer's Pro mode, RAW or manual Kelvin. To use those OEM controls, select Pro/RAW in the phone's camera and import the saved files afterward.
- Scout saves received JPEG/PNG/WebP/HEIC/HEIF/DNG bytes unchanged and creates a separate small preview when decoding is possible. JPEG/HEIC files have already been processed by the phone; preserving the file cannot undo that processing. DNG without a browser decoder still saves with no preview. Older compressed-only records cannot recover originals retroactively.
- Android capture selects the largest exposed JPEG/RAW sizes up to 24 megapixels per output. The phone reference camera is not a replacement for the high-resolution A7CR workflow. Notebook originals are limited to 56 MiB combined per image record; larger originals should use the desktop importer.
- Native captures are staged until the IndexedDB transaction succeeds. **Trips & kit → Recover camera captures** retries completed staged captures after an interrupted handoff. If a record cannot be imported (for example, its stop was archived or its originals exceed the portable limit), recovery offers an independent original-file share without discarding the staged capture. Exported originals have their own IDs, filenames, MIME types, sizes and SHA-256 hashes. **Edit → Save original** exports an individual original.

See Android's [manual CCT controls](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#COLOR_CORRECTION_COLOR_TEMPERATURE) for the capability boundary. Physical S25 Edge/Xiaomi RAW, preview orientation and manual-WB behavior remain device-test items.

### Updating the installed app

Install 2.1.0 once from the release link above to obtain the **Update Scout** button. Install over your existing Scout; do not uninstall it. Later, the button checks public GitHub releases, downloads a newer compatible APK, verifies its SHA-256, package identity, increasing Android version code and signing certificate, then opens Android's installer. Android may ask you to allow installations from Scout and will ask you to approve the update. Opening the installer is not a claim that installation completed. Keep a field backup.

Browser updates remain separate: new offline assets wait behind the **Update app** banner and save the open record before activating. The header update checker identifies the browser edition rather than attempting to install an APK there.

## Desktop camera importer

```sh
npm run catalogue -- --input "D:/Photos/Trip/Originals" --output "D:/Photos/Trip/Review"
```

Input and output must be separate, non-nested directories. The importer recursively reads ARW, DNG, JPEG, PNG, HEIC/HEIF and TIFF files. It does not follow symbolic links or junctions, and never writes, moves, renames or deletes originals.

- SHA-256 identifies exact byte duplicates; alternate paths remain in the catalogue. Similar images, exposure brackets and RAW/JPEG pairs are preserved separately.
- ExifTool reads original metadata without inferring camera time from filesystem dates. Original clock text and offset tags remain in the metadata; field matching uses explicit clock segments.
- ARW/DNG previews come from embedded JPEGs, not a new RAW development. Other supported images are rendered with Sharp. Previews are resized to at most 1600 pixels and converted to sRGB.
- Missing previews or metadata are reported. HEIC support depends on the decoder build; unsupported files stay in the inventory for manual handling. Corrupt files never become successful previews.
- The catalogue checkpoints after each file. Rerun the same command to resume or refresh; verified previews are reused. Do not run two importers into the same output folder at once. Wait until the card-copy operation has completed.
- `IMPORT.txt` lists the current ZIP batches. Each run writes a separate batch directory, so old batches are not silently replaced. Import the current list; repeated images are ignored within the destination trip.

The importer verifies the copied files it reads. It does not compare the desktop copy with the original card or establish that a second backup exists.

## Devices and measurements

| Device / mode | Field notes, photos, GPS, packages | Live lighting |
| --- | --- | --- |
| Samsung S25 Edge / Xiaomi Android app | Implemented | Android `TYPE_LIGHT`, only if the device exposes it |
| Android browser / installed PWA | Implemented over HTTPS | Manual; experimental browser ambient-light API only when exposed |
| Recent iPhone Safari / home-screen app | Web workflow implemented; physical-device testing pending | Manual lux or meter EV100 |
| Desktop browser | Capture/review/import/export | Manual entries |

The Android bridge samples actual light-sensor events for four seconds. It rejects missing events and reported saturation, and cancels on backgrounding. It records event count and range; a stable sensor may report only one event. This is an event mean, not a calibrated time-integrated incident-light reading. The phone sensor's spectral response, placement and shielding can differ substantially between devices.

Native camera-based EV/CCT estimation, iOS native sensor integration, live camera tethering, automatic QR recognition, cloud sync and automated AI sorting are **not implemented**. The code/QR is a photographed identifier for manual review in this version. Image-derived color and exposure settings are never labelled as calibrated light readings.

## Android build

The repository includes a Capacitor Android project with light-meter, reference-camera, place-name and update plugins. This build requires Android 8 / API 26 or newer.

```sh
npm ci
npm run android:sync
cd android
./gradlew assembleDebug
```

On Windows use `gradlew.bat assembleDebug`. Use JDK 21 and an Android SDK with API 36 and the Gradle-requested build tools. Set `JAVA_HOME` and `android/local.properties` (`sdk.dir=...`) or configure the project in Android Studio.

The output is `android/app/build/outputs/apk/debug/app-debug.apk`. A debug build is suitable for a personal device trial. Export data before uninstalling or switching signing identities. Keep the signing key if distributing subsequent builds; release signing and app-store publishing are separate work.

## Storage, transfer and recovery

Records and attachments commit together in IndexedDB. Save indicators update after transaction completion; failed writes do not count as saved photos. Edits save when a field loses focus, with an explicit save button as well. Wait for **Saved on this device** before closing. Browser eviction, clearing site data and uninstalling the Android app can remove local data; portable backups are required.

Field ZIPs contain `field.json`, attachment checksums, previews, preserved phone originals, `catalogue.csv`, `contact-sheet.html` and `HANDOFF.md`. Large trips split into parts of up to 100 image records or roughly 32 MiB of image attachments (one larger image record can occupy its own part). Each ZIP has a 64 MiB expanded limit; extremely large metadata archives may require a smaller export.

Imports preview the merge before committing. Revision ancestry preserves newer records and deletion markers; divergent edits are held in **Transfer → Conflicts** for explicit resolution. An already-imported package is idempotent. Archive actions retain tombstones and attachments; **Transfer** can restore records. Resolve conflicts before exporting. Archiving a set does not erase its image records or originals.

Original v1 `hkscout_v1` localStorage and `hkscout` photo database migrate on the same origin; neither source is cleared. Original v1 JSON backups also import. Global legacy aperture and white-balance settings are preserved in the archive, but are not asserted as historical per-photo facts. Old custom itinerary/progress/checklist fields remain in the legacy archive; the new interface uses photo sets. If v1 migration fails, source data stays intact.

No account, analytics SDK or automatic cloud/AI photo transfer is used. Optional place-name lookups use the device address provider or BigDataCloud as described above; update checks contact GitHub. GPS, notes and image metadata remain sensitive when you choose to share a package. The external map link opens Google Maps; offline basemaps are not included. New web builds wait for explicit activation through **Update app**, after saving the open record.

## Development and validation

- `npm test`: model, transactional storage, packages, clocks, solar dates and desktop importer integration.
- `npm run test:browser`: Chromium workflow including mobile layout, controlled GPS, offline reload, lighting, image/color notes, catalogue review, handoff restore, repeated import and v1 migration. Starts its own local test server. Install a test browser with `npx playwright install chromium` if none is available.
- `SCOUT_TEST_ENGINE=webkit npm run test:browser`: optional WebKit pass after installing its Playwright browser. Desktop engine tests do not replace iPhone testing.
- `npm run build`: bundles dependencies locally, fingerprints the offline cache and generates Capacitor assets.

See [HANDOFF.md](HANDOFF.md) for implementation boundaries, validation evidence and the next device checks.
