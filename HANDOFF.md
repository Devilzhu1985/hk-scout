# Scout 2.0 implementation handoff

Date: 2026-10-03. Repository: hk-scout. This is the local implementation handoff, distinct from the shoot-specific HANDOFF.md generated inside each export.

## Phone test publication

The existing GitHub Pages site builds the repository root from `main`: https://devilzhu1985.github.io/hk-scout/. The `v2.0.1-preview.1` prerelease supplies the Android debug APK and a SHA-256 checksum for a device trial. This publication does not change the physical-device validation boundary below.

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

Functional changes: generalized trips and capture sets; phone observations, GPS, lighting and color reference; camera slate and clock review; read-only camera catalogue and preview import; offline package merge, conflict resolution, recovery and assistant handoff.

Technical changes: modular browser application, schema 2/IndexedDB transactions, checked ZIP batches and revision ancestry, ExifTool/Sharp importer, Capacitor Android project and native light-sensor bridge, build scripts and meaningful data/browser tests. Generated root assets support the existing static hosting model.

Affected Markdown files: README.md and HANDOFF.md. No other Markdown files are part of this implementation.
