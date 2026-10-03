# Scout · field references

An offline field notebook for environment art and lighting reference. Use a phone for observations and a Sony A7CR (or another camera) for original images. Hong Kong is an optional itinerary; trips and spontaneous stops work anywhere.

## Test on your phone

- **Browser app:** [Open Scout](https://devilzhu1985.github.io/hk-scout/) in Chrome on Android or Safari on iPhone. Open once online, then add it to your home screen and test an offline reload.
- **Android light-sensor trial:** download `scout-2.0.0-preview.1-debug.apk` from the [Android test release](https://github.com/Devilzhu1985/hk-scout/releases/tag/v2.0.0-preview.1). This personal test build includes the native light-sensor bridge; browser mode generally uses manual light readings. Actual sensor availability and accuracy still need testing on your phone.
- If the old Hong Kong interface appears, export any existing records, close all tabs/windows of the app and reopen the link. Do not clear site data to update. Once on Scout 2, use **Update app** when offered.
- Web and Android installations have separate local notebooks. Export a field ZIP and import it into the other installation to move records. The APK is a debug build, not an app-store release; export before uninstalling it.

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

1. **Prepare a trip.** Choose a destination and IANA time zone, such as `Asia/Hong_Kong`. Select the Hong Kong itinerary or start blank. Set the camera model to its EXIF model name; the A7CR normally reports `ILCE-7CR`. Device labels and camera defaults live on each device; existing sets keep their snapshots.
2. **Start a photo set.** Tap **Capture here** at any location, including a detour. Photograph the displayed code and clock with the Sony. Keep the code readable. Set names are editable; revisits get new codes and intervals. Starting another set closes the previous open set created by this device.
3. **Record the place.** Save a fresh GPS fix with its accuracy, or enter coordinates manually. Note the landmark, light sources, weather, material appearance, shooting direction and any access constraints. GPS can be poor among tall buildings; coordinates do not establish a precise doorway or camera pose.
4. **Record the light.** Use Android live lux where available, or enter a meter reading manually. Name the position, instrument and orientation protocol. Keep lux, camera-meter EV100 and exposure settings separate. Comparable positive lux readings can show a ratio and stop difference.
5. **Record color references.** Include the target in a Sony frame and optionally attach a phone reference. The Sony lens cover is an **uncalibrated reference**, not an assumed 18% gray card. Sampling the preview saves image-derived sRGB and location notes; it does not measure CCT, RAW RGB or physical albedo. Keep appearance references separate from attempts to neutralize material color.
6. **Finish the set.** Capture an establishing view, useful details and any needed brackets or panorama. Add optional Sony filename boundaries and finish the interval before moving on. A recovered open set remains visible after restarting.
7. **Back up the field record.** In **Transfer**, create a field backup. Save every part if the trip is split. Import it on the desktop and review the merge. Verify the saved package and keep an independent second copy. A successful share/download request is not proof that a second copy exists.
8. **Copy camera originals, then catalogue them.** Use the desktop command below. Import each resulting ZIP into the same destination trip.
9. **Correct clocks and review associations.** In **Library → Camera clock segments**, enter the camera's actual wall-time range, its UTC offset during that range and the seconds to add to correct its clock. A camera 90 seconds slow needs `+90`. Photographing the phone clock provides evidence for this correction. Use separate segments after clock/time-zone changes; overlapping segments block suggestions. Only closed sets for the same camera are suggested. Confirm suggestions or assign manually after inspecting the slate and images.
10. **Prepare the AI handoff.** Export from **Transfer**. Give the resulting ZIP parts to your assistant along with your organization priorities. They contain previews, stable IDs, field records, a CSV, contact sheet and review instructions. Originals stay in your own archive.

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

The repository includes a Capacitor Android project and the `LightMeter` native plugin.

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

Field ZIPs contain `field.json`, attachment checksums, JPEG previews, `catalogue.csv`, `contact-sheet.html` and `HANDOFF.md`. Large trips split into parts of up to 100 image records or roughly 32 MiB of previews. Each ZIP has a 64 MiB expanded limit; extremely large metadata archives may require a smaller export.

Imports preview the merge before committing. Revision ancestry preserves newer records and deletion markers; divergent edits are held in **Transfer → Conflicts** for explicit resolution. An already-imported package is idempotent. Archive actions retain tombstones and attachments; **Transfer** can restore records. Resolve conflicts before exporting. Archiving a set does not erase its image records or originals.

Original v1 `hkscout_v1` localStorage and `hkscout` photo database migrate on the same origin; neither source is cleared. Original v1 JSON backups also import. Global legacy aperture and white-balance settings are preserved in the archive, but are not asserted as historical per-photo facts. Old custom itinerary/progress/checklist fields remain in the legacy archive; the new interface uses photo sets. If v1 migration fails, source data stays intact.

No account, analytics or automatic cloud/AI transfer is used. GPS, notes and image metadata remain sensitive when you choose to share a package. The external map link opens Google Maps; offline basemaps are not included. New web builds wait for explicit activation through **Update app**, after saving the open record.

## Development and validation

- `npm test`: model, transactional storage, packages, clocks, solar dates and desktop importer integration.
- `npm run test:browser`: Chromium workflow including mobile layout, controlled GPS, offline reload, lighting, image/color notes, catalogue review, handoff restore, repeated import and v1 migration. Starts its own local test server. Install a test browser with `npx playwright install chromium` if none is available.
- `SCOUT_TEST_ENGINE=webkit npm run test:browser`: optional WebKit pass after installing its Playwright browser. Desktop engine tests do not replace iPhone testing.
- `npm run build`: bundles dependencies locally, fingerprints the offline cache and generates Capacitor assets.

See [HANDOFF.md](HANDOFF.md) for implementation boundaries, validation evidence and the next device checks.
