# Fiji Updater channels: remaining work

State as of the fiji-updater `main` branch, 32 commits past `ea5f12e`
(imagej-updater `master` as of this writing). Everything below is outstanding;
what is already done is in the commit log and not repeated here.

Nothing has been pushed. The branch is intended for a fresh
`fiji/fiji-updater` repository, after which `imagej/imagej-updater` gets
archived with a pointer rather than renamed.

## Blockers before the first channel is minted

These are wrong or missing in ways that only bite once a channel exists, which
is exactly when it will be too late to notice.

- **Coexistence detection for a stray `jars/imagej-updater.jar`.**
  The hazard is two registered `UpdaterUI` plugins, two "Update…" menu entries,
  and a channel-unaware updater with write access to the same `db.xml.gz` and
  the same launcher CFG — which is a mass-downgrade bug arriving through the
  front door. A JAR can survive via a third-party site shipping its own copy,
  or a plugin declaring a dependency on it. Wanted: detection at startup,
  removal, an audit of hosted sites for third-party copies, and the coexistence
  test matrix (old obsoleted and removed; stray old JAR detected; both present
  yielding exactly one registered `UpdaterUI`).

- **Channel name case canonicalization.** Recommended in the design and never
  implemented. URLs are case-sensitive on the Linux servers but not on a
  maintainer's macOS or Windows box, so `a.punctulata/` uploaded from a dev
  machine works locally and 404s in production. Wanted: compare
  case-insensitively, always emit the canonical form. `Channels` and
  `ChannelManifest` are the places.

- **Core-site fallback asymmetry.** Every site currently falls back the same
  way. For a third-party site, falling back to the base channel is the whole
  point — base is simply what they publish for everyone. For the **core** site
  it is a genuine downgrade to Fiji-Stable-era content. The core site should
  not resolve below the installation's channel, and should say something loud
  if its own channel is missing. Needs a decision on how the core site is
  identified once `MAIN_SITE_NAME` stops being `Fiji-Latest`.

- **Decide whether `Channels.EMBEDDED` gets populated.** It is deliberately
  empty. Once channels exist it becomes the offline fallback ordering, and the
  question is whether shipping a stale list is better or worse than shipping
  none. Note the authority is the core site's manifest either way.

## Depends on `list-of-update-sites`

All three of these are the same blocker: the updater still scrapes the
wikitable, which flattens to name/url/description/maintainer and has nowhere
to put anything else.

- **Consume `sites.yml` directly.** It is live and serving at
  `https://imagej.net/list-of-update-sites/sites.yml` — **as `text/yaml`**. So
  the switch needs either a YAML dependency, which runs against the
  shed-dependencies direction, or a JSON artifact published alongside from the
  same source. That choice should be made before anyone starts.
  `AvailableSites.parseWikiPage` and the inlined `getPageSource` go away
  together when it lands.

- **Adopt the site `id` field.** Resolves the standing `// TODO use site id` at
  `AvailableSites.java:280`. Name-based matching currently needs
  `findIndexByName` plus a `makeSureNamesAreUnique` hack that appends `-2` to
  collisions, and a stable id is also the right cache key for per-site channel
  resolution.

- **Move mirrors into the site list.** `UpdateSiteNetwork.MIRROR_URL_PREFIXES`
  and `MAIN_SITE_MIRRORS` are a hardcoded stopgap, labelled as such. Mirrors
  are a property of a site and belong next to the site they mirror, at which
  point no URL needs to appear in Java at all. The end state is a mirror as a
  property of `UpdateSite` rather than a separate named site, which would also
  retire the odd bit of migration logic that infers "you are on the Europe
  mirror of Java-8, so you want the Europe mirror of Fiji-Latest".

## Server and release process

- **Retire the `Fiji` name, then follow it here.** `MAIN_SITE_NAME` is
  `Fiji-Latest` because plain `Fiji` is taken: every modern installation still
  carries a disabled entry for the legacy `update.fiji.sc` site under that
  name, and `AvailableSites` matches local against official *by name* over
  `getUpdateSites(true)`, which includes disabled ones. Claiming it today makes
  the merge replace the main site with that deactivated legacy entry. The
  rename has to happen in the published site list and on the server first.

- **Version-scope `jdk-urls.txt`.** Currently one global file at
  `downloads.imagej.net/java/jdk-urls.txt`. Wanted: one per Java major version,
  e.g. `java/21/jdk-urls.txt`, referenced from each channel's own TOML. Version-
  scoped beats channel-scoped because several channels will share a Java major
  version, and it lets a patch bump reach every channel on that version at once.

- **Per-channel `fiji.toml`.** Each channel ships its own, carrying its Java
  expectations. `JavaRequirement` reads `jvm.version-min` and
  `scijava.app.java-version-recommended` from it. Note the long-term intent to
  keep `jvm.version-min` as low as the app-launcher's own bytecode allows, so
  that the launcher always starts and only the *recommended* version moves.

- **One-line launcher change:** `'--update|net.imagej.updater.CommandLine'`
  becomes `'--update|sc.fiji.updater.CommandLine'`.

- **Publish a final `net.imagej:imagej-updater`** whose only job is to be
  obsoleted cleanly, so installations remove it rather than keeping it
  alongside the new one. See the coexistence item above.

- **Mass-alias hosted sites behind an allowlist.** Serve
  `<site>/<channel>/db.xml.gz` from `<site>/db.xml.gz` by server-side rewrite,
  for sites passing an objective criterion — no shadowing of core libraries,
  computable from the indexes already hosted. One rule covers every site and
  every future channel, so there is no re-bootstrapping treadmill. The
  allowlist must be **recomputed at each channel mint**, not inherited: it is a
  snapshot of a property that changes, and an alias asserts compatibility on
  the maintainer's behalf. A blanket rewrite would be worse than doing nothing,
  since no client could then detect non-adoption.

- **Blob garbage collection must union across channels.** Blobs stay in the
  site root while indexes are per-channel, so any pruning of unreferenced blobs
  has to union the references of *every* channel index, not just the newest. A
  channel nobody has fetched in a year is still load-bearing for the users on
  it. This is a data-loss bug if it is got wrong, and it wants writing down
  wherever such tooling lives or comes to live.

## Other repositories

- **`imagej-ui-swing`:** delete the copied `net.imagej.ui.swing.updater`
  package, and the unused `SwingTools` import in `SwingColorTableWidget` (which
  was the only reference to that package from the rest of the artifact).

- **`imagej-ui-swing`, `LauncherMigrator`:** finish the `//FIXME abort` — when
  `ResolveDependencies.resolve()` returns false, including the user cancelling,
  the code currently proceeds to `Installer.start()` and stages a half-resolved
  change set. Then ungate `UPGRADE_IMAGEJ` so Fiji-Stable installations get the
  Java-8 → Origin migration. This stays in the outgoing artifacts: fiji-updater
  targets Java 11 and so never runs on the installations that need it.

- **Downstream breakage to communicate.** JIPipe, OpenSPIM, BAR and hIPNAT
  construct `net.imagej.ui.swing.updater.ImageJUpdater` by name. We decided
  against shipping a compatibility shim forever; they get a clear
  `NoClassDefFoundError` at the point the old JAR leaves the update site.
  Worth telling them before, rather than after.

## Smaller cleanups

- **`testDowngrade` is flaky** (`CommandLineUpdaterTest`). Pre-existing: it
  sleeps one second and compares second-granularity timestamps, and the failure
  mode is the macro file vanishing after downgrade — consistent with the file's
  mtime landing before the recorded first version, so the downgrade uninstalls
  it. Failed once in this session, passed on every rerun. It will bite CI.

- **Verify `models` before removing it** from `Checksummer.directories`. Absent
  from current installations, but plausibly used by a third-party site shipping
  ML weights, which is not visible from the code.

- **`Platforms.LAUNCHERS` deprecated ImageJ entries** (`ImageJ-linux64`,
  `Contents/MacOS/ImageJ-*`, …) are candidates for removal now that
  fiji-updater never runs on a pre-Jaunch installation. Both uses are
  upload-side — assigning a platform to a file being published — so they cost
  nothing and were deliberately left alone. Separate decision, low stakes.

- **Consolidate the lagging-site check.** `ChannelUpgradePrompt.laggingSites`
  and `XMLFileDownloader.hasNotAdopted` answer the same question two ways.

- **The GUI upgrade prompt is untested**, being Swing. The decisions underneath
  it — detection, reconciliation, the gate — are covered headlessly. Worth a
  manual pass through the real app once a test channel exists, particularly
  that the session ends cleanly after an accepted upgrade.

- **Confirm the headless publishing story end to end.** Publishing follows the
  channel the installation declares, so a CI job that wants to publish for a
  new channel must run `upgrade` first. That works, but nobody has run it
  against a real site yet.
