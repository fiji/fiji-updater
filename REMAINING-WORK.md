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

- **Fail fast when the core manifest cannot be *reached*.** There is no longer
  a compiled-in channel list; a core site with no readable `channels.txt` yields
  an empty list, i.e. "no channel exists". That is right for the reason it looks
  wrong: today no site has a manifest, so an absent one is not an error, it is
  the pre-channel world, and refusing there would refuse on every installation
  alive.

  What the empty list cannot distinguish is **absent** (a 404 — channels were
  never minted) from **unreachable** (DNS, timeout, a captive portal answering
  200 with HTML). `ChannelManifest.read` conflates them deliberately, and for a
  third-party site that is correct. For the core site it stops being correct the
  moment a channel exists, and the case it lets through is the dangerous one:
  an installation whose own channel is undeterminable is only allowed to proceed
  because `Channels.anyExist` said no channels exist — an answer we did not
  actually learn. That is the mass-downgrade path, reached by a transient
  network failure.

  So the rule wanted is: if the core site's manifest could not be *fetched*, as
  opposed to being *absent*, and this installation cannot say which channel it
  follows, refuse — the same refusal `XMLFileDownloader.start` already issues,
  extended to cover "we could not find out". Needs `ChannelManifest.read` to
  report the difference, along the lines of `XMLFileDownloader.isUnreachable`.

  Note what is *not* wanted: refusing whenever the manifest is unfetchable
  regardless of the declared channel. An installation that knows it is on
  channel X resolves the core site against X with or without the list — the only
  loss is the intermediate fallback steps for third-party sites, which is a mild
  degradation and not worth blocking an update over.

## Depends on `list-of-update-sites`

All three of these are the same blocker: the updater still scrapes the
wikitable, which flattens to name/url/description/maintainer and has nowhere
to put anything else.

- **Consume `sites.yml` directly.** It is live and serving at
  `https://imagej.net/list-of-update-sites/sites.yml`. No YAML dependency and no
  JSON artifact published alongside: the file's shape is validated by CI on
  every change, so the updater can parse a deliberately narrow subset by hand,
  which is the shed-dependencies direction. The served content type (`text/yaml`)
  stops mattering once nothing is asking a library to parse it.

  Two things the hand-rolled parser has to get right. It must **fail loudly on
  anything outside the subset** rather than skipping the line: a silently
  dropped entry is a site that has vanished as far as clients are concerned,
  which is the failure mode `testChannelOnlySiteIsUnreadableFromBase` documents.
  And it wants the **real `sites.yml` as a test fixture**, refreshed
  occasionally, because the subset it accepts is only correct relative to what
  the file actually contains.

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
  `Fiji-Latest` because plain `Fiji` is taken. The server-side half of the plan:
  land and release "Consume `sites.yml` directly"; stop syncing `sites.yml` into
  `index.html` and `sites.xml`; then drop the legacy `ImageJ`
  (`update.imagej.net`), `Fiji` (`update.fiji.sc`) and `Java-8` entries from the
  published list, putting `sites.imagej.net/Fiji` first under the name `Fiji`.

  Two corrections to that sequence, both about clients rather than the server.

  **Old updaters read `api.php`, not the page.** `AvailableSites.getPageSource`
  fetches
  `imagej.net/api.php?action=query&export=true&titles=List of update sites` and
  parses the wikitext `{| class="wikitable"` out of the XML export. So what
  keeps a pre-`sites.yml` updater working is that *export query* continuing to
  answer with a table carrying Name / Site-or-URL / Description / Maintainer
  columns. Whether `imagej.net/List_of_update_sites` 301s or serves a static
  `index.html` is invisible to those clients — a good idea for humans, but not
  the compatibility lever. If `api.php` does stop answering, old updaters
  degrade rather than break: `tryGetAvailableSites` logs and returns an empty
  list, local sites are all kept, and no newly minted site is ever seen again.

  **Removing the legacy entries from the published list is not enough**, because
  the collision is with local state. `AvailableSites` seeds the list with
  `initializeMainUpdateSite()` at index 0, then merges every *local* site —
  disabled ones included — by name, with the local entry replacing the seeded
  one. Flipping `MAIN_SITE_NAME` to `Fiji` therefore does two bad things at once
  on an existing installation, and the published list has no say in either:

  - the disabled legacy `Fiji` entry in the local `db.xml.gz` matches index 0 by
    name and **replaces the main site** with a deactivated `update.fiji.sc`
    entry; and
  - the installation's real main site, still named `Fiji-Latest` locally,
    matches nothing and is **appended as a second entry**, so the installation
    ends up following the main site twice under two names, with every
    `FileObject.updateSite` still saying `Fiji-Latest`.

  So the rename needs a **client-side migration shipped in the same release that
  flips the constant**, running before `initializeAndAddSites`: rename the local
  `Fiji-Latest` site to `Fiji` and rewrite the `update-site` attribute of every
  file that names it, and retire the legacy entries. Retiring them is not simply
  deletion — an inactive legacy site with no installed files can go, but one
  that is somehow still active, or still accounting for files on disk, must be
  renamed and reported instead, on the same reasoning as `ChannelUpgrade`'s
  stranded-file handling: never silently unmanage content.

  Note this is independent of recognizing the core site, which no longer depends
  on the name at all: `UpdateSiteNetwork.isCoreSite` asks the URL first.

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

- **Automate the adoption attestation; keep the treadmill.** The rejected
  version of this was a server-side rewrite serving `<site>/<channel>/db.xml.gz`
  from `<site>/db.xml.gz` for every site passing a computed criterion. The
  objection is not the mechanism but the claim: whether a site still works with
  a new edition is the maintainer's assertion to make, and there is value in
  their having to make it each time. What should go is the *manual step*, not
  the checkpoint.

  The shape: a maintainer commits a `validate.groovy` for their site, run as
  `fiji --headless validate.groovy` against a fresh installation on the newly
  minted channel with that site activated and nothing else. A zero exit
  publishes the channel into the site's `channels.txt` on their behalf — which
  is all that silences the warning, since `hasNotAdopted` keys on the manifest.
  The maintainer opted in by writing the script, so the attestation is still
  theirs; and because the manifest is the only artifact touched, revoking it is
  a one-line edit rather than an unpublish.

  Worth deciding alongside it:

  - **The alias still has to exist.** Listing a channel in `channels.txt`
    silences the warning but does not serve `<channel>/db.xml.gz`, so a client
    on that channel still falls back to the site root. Either the rewrite
    happens for validated sites, or the runner copies the index — the manifest
    edit alone would be a lie the client can detect.
  - **A cheap objective pre-filter**, before running anything: no class
    shadowing a core library, and no class file with a major version above what
    the channel's Java can load. Both are computable from the hosted indexes,
    and they catch the two most common breakages without executing a line of a
    maintainer's code. Useful as a gate on *whether to bother* running the
    script, not as a substitute for it.
  - **A standing opt-in** (`auto-adopt: true` in the site's `sites.yml` entry)
    for maintainers who would rather declare once than script anything. That
    turns the treadmill into opt-out for the sites that want it, while leaving
    silence to mean what it means today.
  - **Run it continuously, not only at mint.** The same harness answers "does
    this site still work" on any day, which is worth more than the once-per-
    channel answer and makes the mint-day run uneventful.

  What does not work: aliasing on *activity* — newest blob postdating the last
  mint — which asserts that someone is still around, not that anything still
  loads. And a blanket rewrite remains worse than doing nothing, since no client
  could then detect non-adoption at all.

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
