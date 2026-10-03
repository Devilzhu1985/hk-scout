# Scout 2.1.1 implementation handoff

Date: 2026-10-03. Repository: hk-scout. This is the local implementation handoff, distinct from the shoot-specific HANDOFF.md generated inside each export.

## Phone test publication

The existing GitHub Pages site builds the repository root from `main`: https://devilzhu1985.github.io/hk-scout/. The `v2.1.1-preview.1` prerelease supplies the Android debug APK and a SHA-256 checksum for a device trial. This publication does not change the physical-device validation boundary below.

## Camera preview correction and lighting slate · 2.1.1

User reported a rotated, stretched or cropped native camera preview. The former transform reapplied SENSOR_ORIENTATION even though TextureView already applies it, then used the wrong axes for the aspect correction. CameraPreview now removes the implicit stretch, compensates only display rotation and fits the complete frame. The black viewfinder shows letterboxing instead of cropping. Display changes, including 180-degree changes without a resize, update the transform. Supported rotate-and-crop controls are set to NONE so compatibility mode does not add another crop. The activity still prefers portrait; this change does not add a landscape camera UI.

Preview size selection first matches the still-image aspect ratio, then selects the largest stream within 1920 × 1080 bounds, independently of camera HAL order. Eight JVM tests cover exact upright portrait framing, expected letterbox coordinates, rotation direction, all sensor/display quarter-turns across five view shapes, resolution ordering and unusual-size/empty-list handling. Physical S25 Edge preview and capture framing must still be checked; no Android device was connected during development.

Release: versionName 2.1.1 / versionCode 5, same app ID and signing identity. Browser and packaged web assets carry the same version. Schema version remains 2; no original-image processing changes. Existing 2.1.0 installations can request this release with Update Scout.

Additional user decision in this release: light/EV capture belongs on the camera slate, and all QR identity fields must also be human-readable. The slate offers a four-second sensor sample saved directly as a normal phone_lux record with instrument, timestamp, sample statistics and an explicitly instructed/unverified placement protocol. Manual EV/lux entry returns to the slate after save or cancel. A saved reading already satisfies the later lighting step; additional zone readings remain available. Sensor failures/cancellation do not save a new reading. The latest active reading is limited to the current stop and printed with its source and timestamp; older-than-five-minute readings are flagged.

src/slate.js keeps QR identity stable and computes a display-only EV100 estimate for positive lux using C=250. It never changes lux into a camera_ev record or equates it with reflected-light metering. Zero lux has no finite EV; negative/zero entered EV remain valid. The QR still contains only app=scout, v=2, tripId, setId and code, all printed on the slate. GPS/place and live clock also appear, and delayed GPS updates reach an open slate. No automatic QR decoder was added.

Additional user decision: fetch weather automatically rather than requiring typed conditions/time. The user explicitly approved new-stop GPS rounded to two decimals being sent to Open-Meteo. src/weather.js validates current model data and units, preserves provider/retrieval/fix timestamps, grid/request coordinates, time zone and precipitation interval, and applies it only to the unchanged fresh GPS of a recent open stop without an existing snapshot. set.weather and set.autoTimezone are optional in schema 2 and validated on import; writes use the same Store transaction owner as other set changes. New stops enable autoTimezone and adopt the provider's GPS-derived zone with the first weather snapshot; manual coordinate/time-zone edits disable it. Older stops lack the flag and keep their chosen zones. No migration or fabricated historic weather. Device-local onlineWeather defaults on and can disable future requests. Weather estimates remain distinct from notes and measured lighting. Device UTC capture instants were already automatic; online weather timestamps do not overwrite them.

Verification for the combined release: 27 web unit/integration tests, eight native geometry tests, Chromium capture/export/restore flow plus slate sensor save/cancel, manual EV return, bilingual slate, bypass of the already-completed lighting step, weather rounding/display, disabling and HTTP-failure fallback. A live Open-Meteo request with public Hong Kong fixture coordinates confirmed the expected units/timestamp contract. Android build and physical preview verification have separate meanings: no phone was connected for a camera test.

## Guided capture, bilingual camera and updates · 2.1.0

Latest user decisions: English/Simplified Chinese switching; phone camera capture with original-file retention; native RAW/manual Kelvin only when actually exposed; an installed-app update button; automatic GPS and optional place names; a guided next-action flow with explicit **Finish stop**; publish browser and Android editions together.

Workflow: capture now without trip/city typing → Sony slate confirmation or skip → phone reference or Sony-only skip → lighting or skip → Finish stop → Start next stop / backup. Finishing records the closed matching interval and leaves records editable. Starting another stop prompts before closing an unfinished stop. Optional place/camera details, light diagnostics and checklists are collapsed.

Data additions remain optional in schema 2: set.autoName, placeLookup (source and approximate flag), slateStatus/slateConfirmedAt, referenceSkipped and lightingSkipped; asset.originals[] (blobId, filename, MIME, size, SHA-256), phoneCapture (requested and actual settings, device/camera and capture time). All writes still belong to the Store transaction; originals and previews commit atomically. Native files are acknowledged/deleted only after commit. Old records remain readable; old 2.0 clients cannot import new HEIC/DNG attachments and should update first. No migration fabricates an original for an older compressed-only asset.

Local language and onlinePlaceNames preferences do not travel with field records. Template translation protects substitutions: user text, IDs, filenames, stored enum values and code blocks are not implicitly translated. Form saves track edited fields/versions so background GPS refreshes do not submit unrelated stale form fields.

Native camera limits: Android Camera2 rear camera, supported outputs up to 24 MP, auto exposure/focus, RAW DNG where exposed, real AWB presets. Manual numeric Kelvin is offered only with API 36 CCT mode + range + request/result keys + AWB-off support. No estimated RGB-to-Kelvin conversion. Applied values are kept separate from requested values, and an unconfirmed request raises a notice. This is not a full replacement for OEM Pro mode. Browser capture cannot force OEM mode or RAW. No iOS native app was added.

Update checks fetch public releases only on request. Android downloads only the expected repository's APK URL over HTTPS, checks checksum, app ID, newer versionCode and installed signing identity before handing control to the system installer. Installation permission and approval remain user actions; cancellation is not reported as successful installation. The previous installed build needs a one-time manual update to obtain this button. VersionName 2.1.0 / versionCode 4; minSdk 26; existing app ID/signing identity retained.

Verification for this change: 20 unit/integration cases including original DNG transport checksums, rejected altered manifests, capability boundaries, safe localization, semantic update selection and delayed/invalid coordinate lookup protection. Chromium covers the complete old workflow plus fast start, held/denied GPS, untranslated user notes, Chinese roles with canonical values, camera input, exact original JPEG export, skip/finish/next-stop, update-check fixtures and saved language. Camera/installation hardware behavior still requires physical testing; browser mocks are not device evidence.

New implementation areas: src/i18n.js + zh.js; src/photos.js; src/location.js; src/updates.js; ReferenceCameraPlugin/Activity, PlaceNamesPlugin and ScoutUpdaterPlugin. README contains the full operating instructions and provider/Android references.

Before the trip, test on the actual S25 Edge: install over 2.0.1 without uninstalling, verify records, capture portrait RAW+JPEG, inspect original DNG and actual WB metadata, deny/retry permissions, interrupt and recover a completed capture, try place lookup offline, and export/restore originals. Check the next real release's installer permission/approval path; the current build cannot install itself as a newer version. Xiaomi/iPhone and Sony real ARW checks remain outstanding.

## Lighting availability correction · 2.0.1

User feedback: the browser's Measure light action led to an error asking for the Android app, creating the impression of an external light-meter dependency. The full Android Scout application already contains the native meter.

The Lighting panel now probes the actual runtime before offering live capture. Unsupported browsers show an explanation, an explicit download of the complete Scout Android edition and a transfer path for existing records. Android bridge/sensor failures have recovery messages without another install prompt. Permission/capture errors remain in the meter dialog, offer manual entry and never save a fabricated reading. Switching to manual entry stops an active capture.

Fourteen automated data/importer/capability tests passed, alongside the Chromium capture-to-handoff workflow with browser-without-sensor coverage and controlled sensor success/error/cancellation. Native capability outcomes use injected test adapters; physical sensor testing remains pending. The updated Android debug build compiles successfully.

## Product decisions

- Environment art and lighting references; Sony A7CR RAW originals plus phone field records.
- Samsung S25 Edge first; recent Xiaomi Android and iPhone web workflows accommodated by capability checks and manual fallbacks.
- Local files/manual transfer first. Any city and unplanned stops; original Hong Kong location suggestions retained as an optional template.
- Phone lux is uncalibrated. Camera-meter EV100, exposure settings and image-derived sRGB have separate meanings.
- Sony lens cover is uncalibrated. No assumed neutral reflectance, measured CCT or physical albedo.
- Photo associations are suggestions until confirmed. Original photos remain unchanged. AI organization is a later review of an exported package, not an automatic upload.

## Implementation map

| File / area | Responsibility |
| --- | --- |
| src/app.js | Trip/set capture, slate, reference images, measurements, color sampling, clock review, conflicts, transfer |
| src/model.js | Record validation, revision ancestry, tombstones, matching, v1 record conversion |
| src/store.js | Atomic IndexedDB record/attachment transactions and device-local preferences |
| src/packets.js | Portable ZIP validation, checksums, batching, CSV/contact sheet/AI handoff |
| src/native.js; src/light-capability.js | Sensor capability checks, Capacitor sensor/GPS/share access and browser fallback |
| src/sun.js | Approximate sunrise/sunset using the capture location and time zone |
| src/hk-template.js | Original Hong Kong places and itinerary |
| scripts/catalogue.mjs | Read-only desktop original inventory, EXIF, hashes, previews, checkpoints and batches |
| android/app/src/main/java/com/hkscout/field | Native TYPE_LIGHT plugin, lifecycle cancellation, bridge registration |
| scripts/build.mjs; src/sw-template.js | Offline web bundle and controlled updates |
| tests | Data/importer integration tests and browser workflow |

## Data contracts

Schema 2 records have stable IDs, kind, revision, ancestry, updatedAt, last-writing device and a deletion flag. Kinds: trip, set, reading, asset, clock. Set captures retain UTC instants plus their display time zone. Device preferences are local and do not change old capture facts.

Field ZIP format is `scout-field`, schemaVersion 2. Each part carries the trip and set/reading/clock context plus its image subset, including tombstones. IDs and revisions make repeat context safe to merge. Attachment hashes are checked before import and collisions abort the transaction.

Desktop ZIP format is `scout-camera`, schemaVersion 1. SHA-256 describes the original; previewSha256 describes the JPEG. Camera asset IDs combine the destination trip ID with original hash. Duplicate paths are retained, and files without previews stay unresolved.

Clock segments explicitly interpret camera wall time: UTC = wall time − recorded UTC offset + correction seconds. Do not use filesystem mtime or an inferred device time zone as a capture fact. Overlapping segments, open sets and mismatched cameras do not produce unique associations.

## Validation and release boundary

Verified locally:

- Ten automated data/importer tests passed, including transaction rollback, concurrent writes, stale/conflicting revisions, ZIP checksums/size limits, multipart recovery, camera clock ambiguity, v1 migration, solar dates and a real ExifTool/Sharp run against generated JPEG and damaged-file fixtures.
- Chromium workflow passed at mobile and desktop widths with no page errors: capture, controlled GPS, manual lighting/ratios, photo and color notes, offline reload, camera preview review, ZIP handoff export, fresh-profile restoration, repeat-import idempotency and v1 migration.
- Android debug APK compiled successfully with the native light-sensor plugin. Build success does not establish sensor behavior on a phone.
- npm audit reported zero vulnerabilities.
- WebKit browser installation could not complete because its download endpoints timed out. WebKit/iPhone execution remains unverified.

Automated fixtures exercise integrity and workflows; they do not establish photometric accuracy or physical device compatibility. No real A7CR ARW file was supplied for this implementation.

Required physical trial before relying on the app for a trip:

1. Install the Android debug build on the S25 Edge. Test live lux in dark/bright conditions, sensor occlusion, background interruption and unavailable sensor behavior. Compare against a known meter if absolute accuracy matters.
2. Test location grant/denial and poor accuracy in street conditions. Confirm photo picking/capture and native ZIP share/restore.
3. On the available iPhone and Xiaomi, test actual browser/app versions, offline launch, a restart, storage persistence and file export/import. Model names alone do not establish browser API support.
4. Run the importer on a small **real A7CR ARW** sample with multiple cards, brackets, rotated shots and embedded previews. Check original SHA-256 before/after and compare corrected timestamps against the photographed slate.
5. Export a real multi-part trip, import every part into a fresh browser profile, and inspect records/photos before erasing any card.

## Deliberate limits and follow-up

- No native iOS app, camera-derived live EV/CCT, automatic slate decoding or cloud synchronization.
- No automatic semantic sorting, RAW development or destructive culling. The assistant reviews exported previews and evidence; RAW-level quality needs original-file inspection.
- No offline basemap, reverse geocoding, precision pose reconstruction or guaranteed GPS in urban canyons.
- No long-term browser storage guarantee. Portable exports and independent copies remain necessary.
- Existing v1 itinerary completion/custom-place configuration is archived, not fully re-created as new UI controls.
- Imported camera catalogues do not automatically replace a previously imported asset's failed preview; use a new review/import workflow after repairing the source, or add a reference image manually. Metadata changes to an original produce a new hash and therefore a new asset.
- Concurrent desktop import processes must use separate output directories. Full per-device collaboration beyond revision/conflict packages is out of scope.

## Submission note

Functional changes: correct the native reference-camera preview orientation and proportions, preserve its full frame with letterboxing, prefer a sharp preview stream, combine light/EV capture with the readable Sony slate without asking for the same reading later, and automatically save/display nearby weather with capture time.

Technical changes: extract tested TextureView geometry and deterministic stream selection, observe display changes, disable supported implicit rotate-and-crop behavior, preserve measured lux while deriving a labelled EV estimate for display, print the stable QR payload as text, return manual readings to the slate, and add validated optional weather snapshots with rounded GPS, provenance, race guards, opt-out and ZIP preservation. Test bilingual/save/cancel/next-step/weather behavior. Publish matching 2.1.1 browser/Android assets with Android versionCode 5. Physical camera validation remains pending.

Affected Markdown files: README.md and HANDOFF.md. No other Markdown files were changed.
