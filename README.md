# Scout · field references

An offline field notebook for environment art and lighting reference. Use a phone for observations and a Sony A7CR (or another camera) for original images. Hong Kong is an optional itinerary; trips and spontaneous stops work anywhere.

## Test on your phone

- **Browser app:** [Open Scout](https://devilzhu1985.github.io/hk-scout/) in Chrome on Android or Safari on iPhone. Open once online, then add it to your home screen and test an offline reload.
- **Android phone trial:** download `scout-2.1.7-preview.1-debug.apk` from the [Android test release](https://github.com/Devilzhu1985/hk-scout/releases/tag/v2.1.7-preview.1). This is the complete Scout app with its light meter built in, not a separate light-meter utility. This personal test build includes direct sensor access; browser mode generally uses manual light readings. Actual sensor availability and accuracy still need testing on your phone.
- If the old Hong Kong interface appears, export any existing records, close all tabs/windows of the app and reopen the link. Do not clear site data to update. Once on Scout 2, use **Update app** when offered.
- Web and Android installations have separate local notebooks. Export a field ZIP and import it into the other installation to move records. The APK is a debug build, not an app-store release; export before uninstalling it.

The website and Android package are two editions of Scout. Adding the website to the home screen does not give it Android sensor access. The Lighting panel checks capabilities first: it offers live capture when available, otherwise explains the current limitation and keeps manual entry available. Browser camera brightness is not substituted for lux.

## Guided camera meter · 2.1.7

The Android **Camera EV · aim & save** viewfinder now shows a high-contrast aiming guide, with the area outside the requested central region dimmed. The guide turns green only when the reading can be saved. Phones without region control show **Whole frame** and a crosshair explicitly labelled as an aiming aid. The metering algorithm and requested region are unchanged.

The panel shows **1 Aim → 2 Hold steady → 3 Save**, a live EV100 value labelled **Live / not saved**, and real sample duration/count/spread while waiting for stability. The progress bar comes from the sample window, not an animation timer. Bright preview highlights, exposure limits, stale frames and missing metadata get specific next-action messages and cannot enable Save. Old values disappear when stale. Aperture/shutter/ISO details are expandable; the save button stays outside the scrollable feedback panel.

Use a lit wall, floor or another surface you want to reference. Keep the framing still until **Stable · ready to save**, then tap **Save EV to slate**. Green means the existing freshness/stability checks passed; it does not mean the estimate has been calibrated. Both English and Simplified Chinese are included. No new image, reading or permission is created merely by viewing the live UI. This UI runs inside the Android APK; the web edition still reviews transferred readings.

## Clapperboard and camera EV · 2.1.6

The camera slate now resembles a film clapperboard: black-and-white striped sticks, a large stop/scene code, ruled time/light cells, GPS/weather, QR and readable trip/stop IDs. It keeps the actual Scout identifiers and the visible bottom action buttons; no fictional take/roll fields or clap action are added.

In the Android app, open the slate and tap **Camera EV · aim & save** (**主摄 EV · 瞄准测光**). A rear-camera viewfinder shows a target and live EV100. Aim at the lit surface or scene you want to reference, hold steady, then tap **Save EV to slate**. Scout returns to the same slate with the saved reading. Cancelling, unavailable metadata or camera permission failure leaves the previous reading intact. This mode does not take a photograph or create a gallery file.

This is an **uncalibrated reflected-light EV100 estimate**, separate from incident lux. Directly aiming at a bright bulb or the sun can exceed the camera's range; a lit neutral surface is more useful for environment-lighting reference. Phone exposure algorithms, target reflectance and framing affect the result. Stability does not establish accuracy, and the Sony lens cover remains an uncalibrated target.

The estimate uses the camera's reported aperture, shutter duration, sensor ISO and post-RAW sensitivity gain, normalized to ISO 100. Scout prefers a logical rear camera at 1x; phone APIs do not universally identify a physical main lens. Where supported, it requests the central 20% of sensor width/height for AE and labels this as a camera-algorithm request, not a calibrated spot meter. Otherwise it labels whole-frame AE. A converged, stable, recent exposure is required before saving; a preview highlight check blocks obvious clipping but is not a RAW clipping measurement. Device/camera ID, actual settings, sampling evidence and time travel with the reading in the field ZIP. See Android's [exposure-result contract](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#SENSOR_EXPOSURE_TIME), [post-RAW sensitivity](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#CONTROL_POST_RAW_SENSITIVITY_BOOST) and [AE regions](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AE_REGIONS).

The browser displays transferred camera readings and retains manual EV/lux entry; it cannot run this Camera2 meter. The original Android lux sensor remains available as a separate option. Before relying on camera EV on the S25 Edge or Xiaomi, compare the same surface and framing against a known meter, and test permission denial, interruption and bright/dim scenes on the actual phone.

## Field Guide and PC Workspace · 2.1.5

Android opens into **Capture**. Use the slate to save GPS, weather and optional EV/lux, take a **vibe photo** of the atmosphere, then **Finish stop**. Place/notes are expandable, and further lighting readings remain available. Newly added phone images default to the editable `vibe` role; existing image classifications are preserved. **Files** offers large selection tiles and batch cleanup; **Send to PC** exports the field ZIP parts. Originals still save separately from previews and native captures still publish to DCIM/Camera.

Desktop browsers (900px or wider at launch) open the **Workspace**. Import the phone ZIP first to bring in stops, timestamps, readings and reference images, then **Add camera folder** or **Add camera files**. This is local browser storage, not an automatic connection to the phone. The phone/browser capture features remain accessible from Stops.

- Click a tile, use Shift for a range, or Ctrl / Command A outside a text field to select all displayed files. Filter by source, stop, camera or search; changing a filter clears the selection.
- Assign a stop or image role to the selection in one action. The scene inspector shows linked field context, including the phone vibe reference when available. Same-model camera bodies are not automatically distinguished by serial number for clock matching; ambiguous associations require manual review.
- **Move to Trash** is reversible. Use **Undo** immediately, or open **Project Trash → Restore selected** later. Trash retains image bytes until **Delete selected permanently** or Storage cleanup is confirmed. Imported older packages cannot silently revive newer deletions.
- Camera folders are read locally. Originals are never uploaded, rewritten, renamed or deleted. Full-file SHA-256 detects duplicate content. The browser retains metadata and supported previews, not copies of external camera originals or permanent filesystem access. Selecting a folder again is needed for another scan.
- Browser import supports JPEG and other browser-decodable images, and extracts available metadata from supported containers. RAW/HEIC preview support varies by format and browser. Unsupported previews are clearly marked; use the existing desktop RAW catalogue importer for broader extraction. A later catalogue can enrich a preview-less active record without replacing its notes, role or stop assignment. Missing camera times remain missing; filesystem modification times are never substituted.
- Browser batches are limited to 500 supported files, 256 MiB per source file and 48 MiB of generated previews. Review skipped files and failures before committing; use smaller batches or the desktop importer for a larger shoot. Originals must be kept and independently backed up on the PC.

The Android camera offers a **Main / Wide** control only when the selected camera exposes a zoom ratio below 1 through Camera2. Wide framing saves JPEG and disables RAW selection for that frame; RAW keeps the sensor framing and remains available at Main. Requested and reported zoom ratios are retained as capture metadata. This is not a guarantee that every phone exposes its ultrawide lens. Use the system camera and import when the manufacturer's lens is unavailable. See the [Android zoom-ratio contract](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_ZOOM_RATIO). Wide framing still needs a physical-phone trial.

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

1. **Capture here now.** First use can create a dated trip without typing a city or name. Or choose **Plan a trip** for an itinerary and time zone. Opening the Sony slate for an open stop automatically requests a fresh GPS fix in the background, including when reopening it. Returning from light entry or cancelling a light sample stays in the same slate session without requesting GPS again. Viewing a finished stop preserves its historic coordinates. Coordinates, accuracy, capture time and fix time save automatically; online weather is fetched for the new location. Permission/network failure never blocks shooting.
2. **Measure light on the Sony slate, then photograph it.** In Android, tap **Camera EV · aim & save**, aim the rear camera at a lit surface and save the stable estimate. For incident-sensor lux instead, tap **Measure EV + lux · 4 seconds** on supported devices, holding the screen facing up with the sensor clear. Then turn the saved slate toward the Sony. Or use **Enter EV / lux** for a separate meter reading. The slate shows the saved reading, source and time together with the code, current clock, GPS and readable trip/stop IDs. Photograph it with your A7CR, then tap **Code photographed → continue**. Choose **Phone only / skip code** when appropriate. This is your confirmation, not automatic QR recognition.
3. **Capture the vibe.** Use **Take vibe photo** for a wide atmosphere reference, choose an existing image, or skip when shooting only with the camera. Phone originals stay separate from previews.
4. **Record more lighting only if needed.** A reading saved from the slate is already attached to the stop. Add different zones under Lighting details & readings when useful; this is optional and does not add another required flow step. The lens cover remains an uncalibrated target; preview color samples are rendered sRGB, not measured CCT or albedo.
5. **Finish stop, then choose where to go.** Finishing saves the end time for Sony matching. **Choose next stop** lets you select any planned visit, recorded place or new destination. Choose walking, public transport or driving to see a live route in Google Maps; **Open destination in Amap** offers a mainland-China alternative. At the location, tap **Arrived · start capture**. Choosing a destination or opening a route never starts a capture or moves the previous stop's GPS. **Capture here now** remains available for spontaneous references. Starting another capture checks unfinished captures on this device across all trips and asks before finishing them.
6. **Back up before clearing anything.** Save every exported ZIP part and verify an independent second copy. Packages now include phone originals when available; older records may have previews only. Sony originals remain in your camera/desktop archive.
7. **Copy camera originals to the PC.** Import the phone ZIP and add camera folders in **Library**, or use the desktop RAW importer for richer previews. Add camera clock segments, inspect the slate and confirm proposed associations. A camera 90 seconds slow needs +90 seconds. Camera time is never rewritten.
8. **Prepare the assistant handoff.** Export the trip's handoff ZIP parts and supply those with your organization priorities. Exports do not automatically upload to an AI service.

The header switches between **English** and **中文 (Simplified Chinese)**. This preference belongs to the device. UI labels, dates and the optional Hong Kong itinerary switch; your names, notes, filenames, canonical roles and original metadata are preserved.

### Choosing and reaching the next stop

The next destination stays on this device through reloads and is tied to the selected trip. It can be changed or cleared. It is a travel choice, not a new recorded location, and is not exported as a capture. Google Maps receives the selected destination only when a route link is opened; its origin is the traveller's current location, not a historical photograph's GPS. Routes, journey times and transfers are supplied by the map service. Amap opens a WGS84-labelled marker for saved coordinates, or searches by place/city when coordinates are unknown; confirm the destination and use Amap's route button. No unofficial WGS84-to-GCJ conversion is applied to capture data. Network availability and destination entrances must be checked on the actual phone.

New captures started from the Hong Kong itinerary carry a stable `plannedStopKey` for that day/time-slot/place occurrence. Dusk and night visits to Apliu Street therefore have separate finished-capture counts. Revisit and ZIP export/import preserve that key. Counts mean recorded capture intervals have ended, not that every planned subject was photographed or that Sony files are already imported. Older unlinked captures are not guessed into a plan. The Hong Kong template is still fixed; this release does not add a general editable itinerary or automatically reconcile old custom plans.

The slate uses a compact photograph area for the stop code, time, EV/lux, GPS, weather summary, QR and readable IDs. **Details & instructions** holds complete measurement provenance, full weather and explanations. Its bottom action area stays visible on small screens and when the content scrolls; measuring, continuing and skipping do not require scrolling to the bottom. GPS refresh/failure labels distinguish a previous saved fix from a newly obtained fix. A pending automatic GPS response cannot overwrite a stop that was finished or whose coordinates changed while the request was running.

The slate QR contains only `{app: "scout", v: 2, tripId, setId, code}`. Its identity fields are also printed as text; it does not contain a URL, upload data, or replace the displayed lighting/GPS details. Automatic QR recognition is not implemented. The large stop code supports manual matching; photographing the live clock helps review Sony clock offsets.

Phone measurements retain their original lux and provenance. The slate displays **estimated EV100 from lux** using the flat incident-meter convention `log2(lux / 2.5)` (C=250, ISO 100; see [Sekonic's EV/lux table](https://sekonic.com/content/Files/manual/L-358/L-358_operating_manual_en.pdf)). This is an uncalibrated estimate, not the Sony reflected-light meter, exposure compensation, or a calibrated meter substitute. Zero lux has no finite EV estimate. Entered meter EV100 stays separate. Readings are frozen after capture and labelled with their actual saved time; reopening a slate selects the latest active reading of that stop and flags readings older than five minutes. Remeasure whenever the light or position changes.

### Automatic weather and time

New stops automatically request [Open-Meteo current conditions](https://open-meteo.com/en/docs) using a fresh GPS fix rounded to two decimals. The user approved this approximate-location transfer. No notes, images or record IDs are sent. **Trips & kit → Automatic weather & time** can turn off future lookups. There is no IP-based location fallback or lookup of imported/history coordinates.

Open-Meteo has global geographic coverage, including Hong Kong and mainland China; that is not evidence of reachability on every mainland network. Local coordinates, weather fetch and online place-name lookup are separate operations. Android now explicitly enables the installed geolocation plugin's existing LocationManager fallback and allows 30 seconds for a fresh fix when Google Play services or network assistance are unavailable. This does not guarantee a fix indoors or on an untested phone. Failed weather requests remain labelled unavailable and do not block capture or fabricate historical weather. Hong Kong/mainland physical network tests remain outstanding. See [Capacitor location fallback](https://capacitorjs.com/docs/apis/geolocation).

The saved snapshot includes conditions, temperature, cloud cover, wind, precipitation and its interval, provider time, retrieval time, GPS-fix time, requested/grid coordinates and the provider's location time zone. These are nearby **weather-model estimates**, not measurements at the exact street or inside a building. The slate and record show the estimate and source; observed differences belong in notes. Snapshots survive ZIP transfer and are not replaced on reopening or by a later GPS fix. Failed/offline lookups leave weather unavailable with a retry option; historical/finished stops are not filled using today's weather.

Capture and slate time already use the device clock automatically. Leave the phone's automatic date/time enabled. New stops adopt the weather provider's GPS-derived local time zone once available. Editing coordinates/time zone manually disables that automatic change for the stop. Older stops keep their existing time zones. Online weather has its own provider timestamp; Scout does not treat it as the current second, reset the phone clock, or rewrite UTC capture instants. Offline captures retain the initial trip/device display zone until a valid weather lookup succeeds.

### Automatic location names

GPS alone supplies coordinates, not a reliable venue name. Scout starts with a coordinate label and protects any name you type from later lookup results. The Android app uses its system Geocoder, where available, for a nearby street/area estimate. This may require network and can be inaccurate among tall buildings.

Browser area names are optional: **Enable online area names** explains that it sends the device's current GPS to BigDataCloud. The setting can be disabled under **Trips & kit → Location names**. Requests originate on the device, use only a fresh fix obtained there, and never query imported/manual/history coordinates or fall back to IP. See the [provider's client-side policy](https://www.bigdatacloud.com/docs/article/fair-use-policy-for-free-client-side-reverse-geocoding-api). It returns city/area names, not exact shops. Coordinates still save offline; lookup failures never fabricate a location.

### Phone camera and original files

Version 2.1.1 fixes the native preview being rotated a second time and distorted. It fits the complete preview frame with black margins when needed and selects a sharp stream matching the still image aspect ratio. This follows Android’s [TextureView orientation contract](https://developer.android.com/media/camera/camera2/camera-preview#textureview). The camera screen still prefers portrait orientation; geometry tests do not replace checking the actual phone.

- **Android Scout:** includes its own Camera2 reference camera; no separate camera utility is required. A rear camera supporting RAW can save DNG plus a processed JPEG companion. Supported white-balance presets are listed. A manual Kelvin input appears only when Android 16 / API 36 **and the camera hardware** expose the CCT mode, range, request and result keys. Requested and reported settings are recorded separately. A Kelvin setting is not a measurement of scene color temperature.
- **Android/iPhone browsers:** **Open phone camera** requests rear-camera capture. The browser/OS may show a chooser. Browsers cannot force the manufacturer's Pro mode, RAW or manual Kelvin. To use those OEM controls, select Pro/RAW in the phone's camera and import the saved files afterward.
- Scout saves received JPEG/PNG/WebP/HEIC/HEIF/DNG bytes unchanged and creates a separate small preview when decoding is possible. JPEG/HEIC files have already been processed by the phone; preserving the file cannot undo that processing. DNG without a browser decoder still saves with no preview. Older compressed-only records cannot recover originals retroactively.
- Android capture selects the largest exposed JPEG/RAW sizes up to 24 megapixels per output. The phone reference camera is not a replacement for the high-resolution A7CR workflow. Notebook originals are limited to 56 MiB combined per image record; larger originals should use the desktop importer.
- Native captures are staged until both the notebook transaction and gallery copies succeed. **Trips & kit → Recover camera captures** retries completed staged captures after an interrupted handoff. If a record cannot be imported (for example, its stop was archived or its originals exceed the portable limit), recovery offers an independent original-file share without discarding the staged capture. Exported originals have their own IDs, filenames, MIME types, sizes and SHA-256 hashes. **Edit → Save original** exports an individual original.

The Android camera now has a dark, large viewfinder, a fixed bottom shutter, a compact RAW/JPEG switch and a separate WB panel. The layout draws on the control organization described by [Samsung Pro/Expert RAW](https://www.samsung.com/ca/support/mobile-devices/professional-photography-with-galaxy-expert-raw-app/) and [Open Camera](https://opencamera.org.uk/help.html); available hardware capabilities remain unchanged. There are no decorative manual exposure controls. RAW and numeric Kelvin still appear only when actually supported.

New Scout Android captures also publish unchanged JPEG and supported DNG originals to **DCIM/Camera**, using [Android MediaStore](https://developer.android.com/training/data-storage/shared/media). The system gallery may show the JPEG companion without displaying a DNG thumbnail. The notebook keeps its own originals and previews; a gallery copy does not replace the field backup. Android 10+ needs no broad gallery permission for these app-created files; Android 8/9 requests legacy write permission. A pending-file journal and SHA-256 readback protect interrupted copies. A failed gallery copy leaves staged originals for **Retry gallery save** or **Recover camera captures**; cleanup occurs only after notebook and gallery success. Existing public originals are not deleted when a notebook record is removed. Previously acknowledged captures are not automatically backfilled into the gallery. Browser/iPhone web capture cannot force a save into DCIM/Camera; gallery saving there is controlled by the OS/camera chooser or the user's explicit save action.

See Android's [manual CCT controls](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#COLOR_CORRECTION_COLOR_TEMPERATURE) for the capability boundary. Physical S25 Edge/Xiaomi RAW, preview orientation and manual-WB behavior remain device-test items.

### Updating the installed app

In Scout 2.1.0 or newer, tap **Update Scout** to install 2.1.4. For an older build without that button, install the APK from the release link above. Install over your existing Scout; do not uninstall it. Later, the button checks public GitHub releases, downloads a newer compatible APK, verifies its SHA-256, package identity, increasing Android version code and signing certificate, then opens Android's installer. Android may ask you to allow installations from Scout and will ask you to approve the update. Opening the installer is not a claim that installation completed. Keep a field backup.

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
| Samsung S25 Edge / Xiaomi Android app | Implemented | Rear-camera EV estimate when Camera2 metadata is exposed; separate `TYPE_LIGHT` lux sensor when available |
| Android browser / installed PWA | Implemented over HTTPS | Manual; experimental browser ambient-light API only when exposed |
| Recent iPhone Safari / home-screen app | Web workflow implemented; physical-device testing pending | Manual lux or meter EV100 |
| Desktop browser | Capture/review/import/export | Manual entries |

The Android bridge samples actual light-sensor events for four seconds. It rejects missing events and reported saturation, and cancels on backgrounding. It records event count and range; a stable sensor may report only one event. This is an event mean, not a calibrated time-integrated incident-light reading. The phone sensor's spectral response, placement and shielding can differ substantially between devices.

Calibrated photometry, camera-derived CCT, iOS native sensor integration, live camera tethering, automatic QR recognition, cloud sync and automated AI sorting are **not implemented**. The code/QR is a photographed identifier for manual review in this version. Image-derived color and exposure settings are never labelled as calibrated light readings.

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

No account, analytics SDK or automatic cloud/AI photo transfer is used. Automatic weather sends rounded fresh GPS to Open-Meteo and can be disabled; optional place-name lookups use the device address provider or BigDataCloud as described above; update checks contact GitHub. GPS, notes and image metadata remain sensitive when you choose to share a package. The external map link opens Google Maps; offline basemaps are not included. New web builds wait for explicit activation through **Update app**, after saving the open record.

## Delete images, stops and trips

- **Library / Files:** select tiles and choose **Move to Trash**, then restore or permanently delete from **Project Trash**. **Select shown** selects the current filtered results. Individual permanent deletion remains in image details and stop reference cards with the same confirmation review.
- **Field:** **Delete stop** removes that capture and all its linked image and lighting records, including archived children.
- **Trips & kit:** **Delete trip** removes the whole trip, stops, images, readings and camera-clock segments. Deleting the active trip selects another available trip; deleting the last one returns to the start screen.
- **Trips & kit → Storage & cleanup:** see image bytes held in this notebook, delete archived records, or remove files that no record references. An archived stop can still own active images, so review the cascade counts.

Every permanent deletion previews record counts, the affected items, image bytes to free and shared files that will be kept. A confirmation checkbox enables **Delete permanently**. **Back up first** opens Transfer for that trip; complete and verify the backup before reopening deletion. Cancelling makes no changes. If another tab, capture or GPS write changes the notebook while the preview is open, Scout requires a fresh review.

Deletion frees Scout's stored original-image and preview bytes when no other record or unresolved conflict references them. It does **not** delete system Camera/DCIM copies, Sony files, previously saved ZIPs, staged native camera recovery files, or the original v1 localStorage/photo database. The size shown counts notebook image bytes, not total operating-system app storage or caches. Shared image bytes are kept. Archive remains reversible through Transfer and does not free image storage; permanent deletion has no in-app undo.

Small tombstones keep names/filenames, IDs, revision ancestry and required capture timestamps so old packages cannot silently restore deleted records. Notes, GPS/weather, EXIF and image references are removed from permanently deleted records. A whole legacy-trip deletion also clears its migrated metadata archive; the independent v1 source remains. Cleanup applies to this browser profile or app installation only. A normal trip backup includes its deletion markers; **Transfer → Deleted trips → Export deletion record** lets you carry an entire trip's deletions to another device deliberately. The receiving device explicitly lists permanent deletions and requires acknowledgement before applying them. Its leftover image bytes remain available for a separate **Storage & cleanup → Clean unused files** confirmation. Shared or divergent edits remain conflicts; related unresolved conflicts must be resolved before cleanup. Importing new live records under a permanently deleted parent is rejected rather than hidden. To recover a permanently deleted shoot from an independent older backup, import it into a separate Scout notebook/profile.

## Development and validation

- `npm run test:camera-ev`: isolated Android-bridge fixtures for camera EV save/cancel/failure, duplicate taps, unchanged GPS on return, bilingual clapperboard layout and visible mobile footer controls. This does not exercise a physical camera.
- `npm run test:workspace`: isolated desktop camera import, EXIF, duplicates, RAW fallback/enrichment, range/keyboard selection, batch assignment, Trash/Undo/Restore, bilingual layout, mobile controls and export.
- `npm test`: model, transactional storage, packages, clocks, solar dates and desktop importer integration.
- `npm run test:cleanup`: isolated Chromium tests for single/bulk deletion, cancellation, shared-file retention, stale imports, cascade cleanup, last-trip reload, bilingual controls and mobile confirmation-button bounds.
- `npm run test:browser`: Chromium workflow including mobile layout, controlled GPS, offline reload, lighting, image/color notes, catalogue review, handoff restore, repeated import and v1 migration. Starts its own local test server. Install a test browser with `npx playwright install chromium` if none is available.
- `SCOUT_TEST_ENGINE=webkit npm run test:browser`: optional WebKit pass after installing its Playwright browser. Desktop engine tests do not replace iPhone testing.
- Android: `./gradlew :app:testDebugUnitTest` from `android/` runs native EV formula/stability, live feedback/save eligibility, preview geometry/size-selection and original-copy regressions (use `gradlew.bat` on Windows).
- `npm run build`: bundles dependencies locally, fingerprints the offline cache and generates Capacitor assets.

See [HANDOFF.md](HANDOFF.md) for implementation boundaries, validation evidence and the next device checks.

Navigation regression: `npm run test:navigation` (isolated browser profile and test server on port 4181). Route links follow the [Google Maps URL contract](https://developers.google.com/maps/documentation/urls/get-started) and Amap [marker](https://lbs.amap.com/api/uri-api/guide/mobile-web/point) / [search](https://lbs.amap.com/api/uri-api/guide/search/search) documentation.
