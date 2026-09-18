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

- **A failed fetch must never become a negative answer.** The general rule
  behind several of the hazards here, and the one worth stating once: reporting
  a failure is fine, inferring *absence* from a failure is not. It is the second
  that silently rewrites an installation.

  Not the same as failing fast on any network error, which would be a serious
  regression: a dead third-party site is routine, today yields "Could not update
  from site X" and lets the run continue, and making that fatal would let one
  abandoned site block every user of it from updating anything. Sorted by what
  each failure currently *claims*:

  - A third-party site is unreachable — reported as a failure. Correct; leave
    it.
  - The available-site list is unreachable — yields an empty list, local sites
    are all kept, nothing is deleted. Mild; leave it.
  - The core site's `channels.txt` is unreachable — yields an empty channel
    list, which *means* "no channel exists". This is the one. `ChannelManifest`
    conflates absent with unfetchable deliberately, and for a third-party site
    that is right; for the core site it stops being right the moment a channel
    exists. The case it lets through is the dangerous one: an installation that
    cannot name its own channel proceeds only because `Channels.anyExist` said
    there are none — an answer we never actually learned. That is the
    mass-downgrade path, reached by a transient network failure. Wanted:
    `ChannelManifest.read` reporting the difference, along the lines of
    `XMLFileDownloader.isUnreachable`, and the existing `XMLFileDownloader.start`
    refusal extended to cover "we could not find out".

  Note the absent case must keep working: today no site has a manifest, so an
  absent one is not an error, it is the pre-channel world, and refusing there
  would refuse on every installation alive. Note also what is *not* wanted --
  refusing whenever the manifest is unfetchable regardless of the declared
  channel. An installation that knows it is on channel X resolves the core site
  against X with or without the list; the only loss is the intermediate fallback
  steps for third-party sites, which is a mild degradation and not worth
  blocking an update over.

  Two narrower places the same rule applies. The **core site's own index** is a
  precondition for interpreting everything else, so failing to read it should
  abort the run rather than degrade it — the loud message added in "Stop the
  core site falling back to an older channel" says the right thing but still
  lets the run finish. And `XMLFileDownloader.read` marks a site
  `setLastModified(0)` — "it was deleted" — on `FileNotFoundException`, which
  a captive portal or proxy answering 404 for everything would take for every
  site at once. Not traced to a consequence yet, so not yet a claim that it is a
  bug, but it is the same shape and wants a look.

## Depends on `list-of-update-sites`

Both of these are the same blocker: the updater still scrapes the wikitable,
which flattens to name/url/description/maintainer and has nowhere to put
anything else.

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

- **Move mirrors into the site list.** `UpdateSiteNetwork.MIRROR_URL_PREFIXES`
  and `MAIN_SITE_MIRRORS` are a hardcoded stopgap, labelled as such. Mirrors
  are a property of a site and belong next to the site they mirror, at which
  point no URL needs to appear in Java at all. The end state is a mirror as a
  property of `UpdateSite` rather than a separate named site, which would also
  retire the odd bit of migration logic that infers "you are on the Europe
  mirror of Java-8, so you want the Europe mirror of Fiji-Latest".

## Update site identity

The updater keys update sites by **name**, in three places at once:
`FilesCollection.updateSites` is a `Map<String, UpdateSite>` keyed by name,
`FileObject.updateSite` is a name, and the local `db.xml.gz` stores
`<update-site name=...>` alongside `<plugin update-site="Name">`. `UpdateSite`
has no id field at all; the lone `// TODO use site id` at
`AvailableSites.java:327` is the whole of the concept's presence in this
codebase.

That is what makes renaming a site a data-model problem rather than a string
change, and it is the direct cause of the `Fiji` collision below.

- **Identify sites by URL, not by name.** The first half, and it depends on
  nothing: the URL is already in every local `db.xml.gz`, so this needs no
  `sites.yml` and no format change. `findIndexByName` becomes a URL match, and
  on a match the local site adopts the published *name*, rewriting the
  `update-site` attribute of every file that referred to it. A rename in the
  published list then propagates by itself, and the `Fiji` collision stops
  existing: the main site matches the local `Fiji-Latest` by URL and is renamed,
  while the legacy `update.fiji.sc` entry matches nothing and is left alone as
  the disabled leftover it is.

  Two things to fix on the way. `makeSureNamesAreUnique` `continue`s on active
  sites *before* `names.add`, so its set only ever holds inactive names and an
  inactive duplicate of an **active** name is never disambiguated — which is
  precisely the case here, two entries named `Fiji`. And URL matching has to
  happen after `OBSOLETE_URLS` rewriting, with mirrors accounted for, or a user
  on a mirror looks like a user of an unrelated site.

- **Then adopt the site `id`.** The second half, which does depend on consuming
  `sites.yml`, and which handles the one case URL matching cannot: a site whose
  *URL* changes. Ids also give per-site channel resolution a stable cache key,
  and retire the `-2` suffixing in `makeSureNamesAreUnique` outright. Note the
  id can never be the sole key — a user's own private site will never have one
  — so it layers over URL identity rather than replacing it.

- **A published blocklist of retired site URLs** — deferred, and deliberately
  not the mechanism for the `Fiji` rename. Anything fetched cannot be a
  precondition for a constant compiled into the same release: an offline or
  proxied machine would flip `MAIN_SITE_NAME` without ever receiving the prune
  and land in the collision the prune existed to prevent. Worth having for
  retirements after this one, with three constraints — key on URL rather than
  name, since the name is the thing being freed and users rename sites locally;
  prune only sites that are both disabled and accounting for no installed files,
  reconciling rather than dropping anything that still owns files on disk; and
  exact-match only, never prefix, because it is a remote lever that unmanages
  content on every installation at once.

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
  parses the wikitext `{| class="wikitable"` out of the XML export. Whether
  `imagej.net/List_of_update_sites` redirects or serves a static `index.html` is
  invisible to those clients — worth doing for humans, but not the
  compatibility lever.

  The lever is already in place: Apache rewrites exactly that query, matched
  condition by condition, to `list-of-update-sites/sites.xml`
  (`loci-servers/apache/sites/imagej.net.include`). So freezing the generated
  `sites.xml` is all that pre-`sites.yml` updaters need, and no MediaWiki has to
  survive for them. Should the rewrite ever be dropped, they degrade rather than
  break: `tryGetAvailableSites` logs and returns an empty list, local sites are
  all kept, and no newly minted site is ever seen again.

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

  So the rename is gated on **URL identity** (see *Update site identity*),
  shipped in or before the release that flips the constant. That is a better
  answer than a bespoke one-shot migration: it fixes the mechanism rather than
  this instance of it, needs nothing fetched at run time, and leaves every
  future rename a no-op.

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

- **One-line launcher change:** `'--update|sc.fiji.updater.CommandLine'`
  becomes `'--update|sc.fiji.updater.cli.CommandLine'`.

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

## Maven coordinates and clashing artifactIds

Landed: files record their `groupId:artifactId` (read from the `META-INF/maven/`
path), an upload whose coordinate differs from the update site's is refused with
an offer to rename, the checksummer tells clashing artifacts apart instead of
offering to delete one of them, and the installer copies content it already has
on disk rather than downloading it again. That is
[imagej/imagej-updater#120](https://github.com/imagej/imagej-updater/issues/120)
handled for filename-keyed entries, which `doc/maven-native-update-sites.md`
keeps indefinitely (design principle 4, and layer 3 of its Phase 2).

- **Backfill the coordinates of what is already published.** Both guards
  compare against the coordinate the site records, so they are silent on every
  entry uploaded before this. The core site's entries acquire one as they are
  re-uploaded, which for a clash means only after the damage. Cheapest fix that
  does not wait on the flattener: a pass over the hosted `db.xml.gz` filling in
  the coordinate of each current version from the hosted `.jar` itself, since
  the blobs are already there and the answer is in them.

- **Apply a site-side rename to the client's disk.** A file whose content
  matches but whose name differs is `INSTALLED` today: the checksummer sets
  `localFilename`, marks `metadataChanged`, and finds nothing to do. So renaming
  a file on an update site never reaches anyone's `jars/` — the installation
  keeps the old name forever, which is exactly the state the manual
  `antlr.antlr-2.7.7.jar` renames leave behind. Wanted: the installer stages
  such a file as a local move (copy to the site's name, delete the old one),
  which the content reuse already makes free. Note this changes what an ordinary
  update run does on every installation, so it wants its own decision.

- **Uniform `groupId.artifactId-version.jar` naming: rejected.** It would be
  affordable now that renames cost no bandwidth, but it buys nothing the Maven
  design does not already give: there, identity is `G:A(:C:P)` and filenames are
  derived, so names stop being identity rather than becoming a better one.
  Against it: the version-stripped name is the cross-site override key, so
  renaming core files silently stops a third-party site's copy from overriding
  them (duplicate classes, and nothing reports it); third-party indexes declare
  dependencies on core filenames; and the legacy recognition layer must keep
  matching the names old installations actually have. Prefixing stays what it is
  today — the answer to an actual clash, now applied by the updater.

- **Refresh `doc/maven-native-update-sites.md` for its new home.** Three things
  moved under it since it was written against `imagej-updater`: the repository
  and package names (its §9 work breakdown still names `imagej-updater`, which
  is now the `core`/`gui` split); the Phase 0 prerequisite of a standalone
  updater reaching existing installations, which is what the channel work on
  this branch is; and the `sc.fiji.updater.maven` package it introduces, which
  the module declaration deliberately does not export.

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

- **`scijava-maven-plugin`:** `AbstractInstallMojo.getEncroachingVersions`
  matches candidates by artifactId alone, so populating an app deletes one of
  two artifacts that share one -- the build-side half of
  [imagej/imagej-updater#120](https://github.com/imagej/imagej-updater/issues/120).
  Wanted: read the candidate's own coordinate before deleting it, and install
  under the groupId-prefixed name on a mismatch. That retires the
  `fiji/fiji` `bin/populate-app.sh` HACK that hand-installs
  `antlr.antlr-2.7.7.jar`, and it is where the names are actually chosen; the
  updater's refusal is the backstop for when install order picks wrongly.

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
