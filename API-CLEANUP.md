# Fiji Updater: paring down the public API

State as of the fiji-updater `main` branch at `b14f165`. A companion to
`REMAINING-WORK.md`, which covers the channel work; this covers the API
surface, the package layout, and the data-model warts inherited from
imagej-updater. The two interact in a few places, noted where they do.

Measured rather than estimated: `javap` over the compiled core, cross-
referenced against every call site in all four modules plus tests.

Where the pass started:

- 63 public types in `fiji-updater`, exposing 1042 public members.
- 84 of those public members are called from nowhere outside their own file.
- 23 `@Deprecated` members still ship.

Where it stands now, re-measured the same way:

- The core jar holds 98 public types, of which **63 live in an exported
  package**; those 63 expose **667 public members**. Everything else is behind
  `cli`, `xml`, `internal` or `maven`, which the module descriptor does not
  export.
- Of the exported members a name-based sweep can check, **14** have no
  reference outside their declaring file -- three of them `FileVisitor`
  overrides that an interface calls. They are listed under "Still outstanding".
- **No `@Deprecated` member ships any more**, in any of the four modules.

The `sc.fiji.updater.maven` package arrived after this document was first
written, as part of the Maven-native update site work (`doc/maven-native-
update-sites.md`). It is not exported and is not counted as API surface.

## What has landed

All five steps of the suggested order have landed except `FileObject`
encapsulation and the bootstrap consolidation:

1. **Deletions.** Every `@Deprecated` member is gone, along with
   `StderrLogService`, `UpdaterUIPlugin` and the rest of the dead set.
2. **`AppLayout.appRoot()`.** All four entry points now call it.
3. **The package split plus `module-info.java`** in all four modules.
4. **`Filter` to `Predicate`, `Installer` owning a `Downloader`, the
   `Action`/`Status` split.**
5. **`FilesCollection` composition over inheritance** -- but not `FileObject`
   encapsulation.

The suite is green throughout: 196 tests in the core, 9 in the uploaders,
`BUILD SUCCESS` on JDK 21 as of `b14f165`.

Two claims made when this was first written turned out to be wrong when
checked against the code, and were corrected in place: `Diff(PrintStream)` and
`UpdaterUtil.getJarDigest`'s 3-argument overload.

## Package layout for JPMS

The core's problem was not the root package, it was `sc.fiji.updater.util` --
a junk drawer of 25 classes spanning six unrelated concerns. It had to be
exported wholesale because `UpdaterUtil` lived in it, and `UpdaterUtil` is the
most-referenced class in the build.

What the cross-module reference table said at the time of the split:

- **Referenced by nobody outside core.** `XMLFileReader`, `XMLFileWriter`,
  `XMLFileDownloader`, `XMLFileErrorHandler`, `POMParser`,
  `DependencyAnalyzer`, `Class2JarFilesMap`, `DllFile`, `Downloader`,
  `Downloadable`, `SkipHashedLines`, `FilterManifest`, `Channels`,
  `Platforms`, `UpdateSiteNetwork`, `UpToDate`, `CheckForUpdates`,
  `PromptUserToUpdate`, `StderrProgress`, `CommandLine`.
- **Referenced only by the GUI.** `Diff`, `ByteCodeAnalyzer`, `Conflicts`,
  `Installer`, `Checksummer`, `GroupAction` and `action/*`, `URLChange`,
  `ChannelUpgrade`, `AvailableSites`, `JavaRequirement`,
  `ChannelManifest`, `ChannelState`, `AppLayout`.
- **Referenced by the uploaders.** `Uploadable`, `Uploader`,
  `AbstractUploader`, `FilesUploader`, `UpdateSite`, `UpdaterConsole`,
  `UpdaterUtil`.

### Core packages

As shipped. Exported unless marked otherwise; the parenthetical names which
module outside core needs it. The exports are unqualified: `upload` in
particular is an extension point, so a third-party uploader must be able to
reach it.

```
sc.fiji.updater          FilesCollection, FileObject, Dependency, UpdateSite,
                         Conflicts, Installer, Checksummer, GroupAction,
                         UpdateService, DefaultUpdateService, UpdaterCommand,
                         Timestamps
sc.fiji.updater.action   the five GroupActions                    (gui)
sc.fiji.updater.upload   Uploadable, Uploader, AbstractUploader,
                         UploadableFile, FilesUploader, UploaderService,
                         DefaultUploaderService, FileUploader    (ssh, webdav)
sc.fiji.updater.progress Progress, Progressable, AbstractProgressable,
                         StderrProgress, UpdateCanceledException
sc.fiji.updater.ui       UpdaterConsole, StderrConsole   (gui, ssh, webdav)
sc.fiji.updater.channel  Channels, ChannelState, ChannelManifest,
                         ChannelUpgrade, URLChange
sc.fiji.updater.app      AppLayout, Platforms, JavaRequirement
sc.fiji.updater.site     AvailableSites, UpdateSiteNetwork, Connections
sc.fiji.updater.diff     Diff, ByteCodeAnalyzer                   (gui)

  -- not exported --
sc.fiji.updater.xml      XMLFileReader, XMLFileWriter, XMLFileDownloader,
                         XMLFileErrorHandler, POMParser
sc.fiji.updater.cli      CommandLine, UpToDate, CheckForUpdates,
                         PromptUserToUpdate
sc.fiji.updater.internal UpdaterUtil, SkipHashedLines, FilterManifest,
                         Downloader, Downloadable, DependencyAnalyzer,
                         Class2JarFilesMap, DllFile
sc.fiji.updater.maven    Coordinate, GA, MavenVersion, MavenComponent,
                         ComponentCatalog, DependencyEdge, Exclusion,
                         IndexXmlReader, MVSResolver, Release
```

Two things blocked the `internal` package as drawn. Both are now done; what
they were, and what was chosen, is recorded here because the outcome is the
package boundary itself.

- **`UpdaterUtil` could not be internal as it stood**, but its external surface
  is tiny: the GUI and the two uploaders together used exactly 8 of its ~35
  members -- `timestamp`, `openStream`, `openConnection`, `getLastModified`,
  `useSystemProxies`, `isProtectedLocation`, `getLogService`, `PREFS_USER`.
  Those 8 went to small exported homes: `sc.fiji.updater.Timestamps` for the
  `db.xml.gz` timestamp format, `sc.fiji.updater.site.Connections` for the
  HTTP plumbing, `AppLayout` for `isProtectedLocation` and `isGPRActivated`,
  and `UpdaterConsole` for `PREFS_USER` and `getLogger` -- whose own default
  implementation was already the main caller of the latter. The rest --
  `getJarDigest`, `join`, `iterate`, `toCamelCase`, `realloc`, `readFile`,
  `writeFile`, the `hex` char array -- stayed with `UpdaterUtil` in
  `sc.fiji.updater.internal`.

- **`Installer extends Downloader`** forced `Downloader` and `Downloadable`
  to be exported. A public class inheriting from a non-exported one is legal,
  but it leaks `start(Iterable<Downloadable>)` into the signature. `Installer`
  now *owns* a `Downloader`: a one-field change that removed two types from
  the API.

`requires` for the core module is `org.scijava` and `org.scijava.launcher`
(both automatic, and note the names the manifests actually declare), plus
`java.xml`. `java.desktop` is gone: `addWindow(Frame)` and
`removeWindow(Frame)` are deleted, and the headless check in
`Connections.useSystemProxies` asks the `java.awt.headless` property and
`DISPLAY` rather than `GraphicsEnvironment`. Checked by running the CLI with
`--limit-modules` excluding `java.desktop`.

Three things the descriptors turned up, all recorded in the poms:
dependencies move to the module path, where javac does not look for
annotation processors, so the SciJava one must be named explicitly or the
plugin index silently stops being generated; tests compile and run against
the class path, because the core's test JAR is a split package on the module
path and because a patched module's test plugin index shadows the main one
rather than adding to it; and SciJava instantiates plugins and injects
`@Parameter` fields reflectively, so each package holding one is `opens`ed to
it. Resolving all four modules on a real module path was checked by hand: the
CLI runs, and the `UploaderService` finds both uploaders.

## Legacy API deleted

### Deleted outright

Dead, or dead the moment we stopped pretending imagej-updater callers exist.
All of the below are gone from the build.

- **`sc.fiji.updater.util.StderrLogService`** -- was `@Deprecated`, and
  nothing referenced it. Every call site (`FilesCollection`, `UpdaterUtil`,
  `SSHFileUploader`, `WebDAVUploader`) used `org.scijava.log.StderrLogService`
  instead, and still does. A pure duplicate.
- **`UpdaterUIPlugin`** -- a `@Deprecated` marker interface with zero
  references anywhere in the build.
- **`InputStream2OutputStream`** -- had zero references in core; used only by
  `SSHFileUploader`. The 20 lines now live in the ssh module, package-private.
- **`FileObject.markRemoved()`** -- the no-arg `@Deprecated` one, whose body
  was `throw new UnsupportedOperationException`. The live
  `markRemoved(FilesCollection)` is untouched and still called from
  `UpdaterFrame`.
- **`FileObject.Status.getActions()`** -- was `@Deprecated`, with a body
  identical to `getDeveloperActions()`.
- **`FilesCollection.getActions(FileObject)` and `getActions(Iterable)`** --
  were `@Deprecated`, superseded by `getValidActions`, called by nobody.
- **`CommandLine()` no-arg constructor and `CommandLine.getInstance()`** --
  both were `@Deprecated`, both unused.
- **Four `@Deprecated` `Conflicts.Conflict` constructors.** `Conflict` is now
  down to the two good ones, both taking a `Severity`.
- **The remaining `@Deprecated` set**, all of which had live replacements:
  `FilesUploader.initialUpload(String,String,String)`,
  `Downloader()`, `FilesCollection.getUpdateSite(String)`,
  `FilesCollection.getUpdateSiteNames()`,
  `FileObject.addPreviousVersion(String,long,String)`,
  `FileObject.isUploadable(FilesCollection)`, `UpdaterUtil.getPlatform()`.
  Also the two superseded `FilesUploader` constructors and
  `FilesCollection.get(int)`, whose body threw.

  Not `Diff(PrintStream)`: it is the class's *only* constructor, with three
  live call sites, so there was nothing to migrate to. The misleading
  `@Deprecated` came off instead.

### Demoted from public to package-private

The largest single reduction, and it changed no behaviour. Several members
listed here turned out to have no caller at all and were deleted rather than
demoted; those are marked.

- **`CommandLine`'s 19 public command methods** -- `listCurrent`,
  `listModified`, `listShadowed`, `listUptodate`, `listUpdateable`,
  `listNotUptodate`, `listFromSite`, `listLocalOnly`, `listUpdateSites`,
  `history`, `usage`, `uploadCompleteSite`, `addUploadSites`,
  `addOrEditUploadSite`, `removeUploadSite`, `revertUnrealChanges`,
  `chooseUploadSite`, `getLongUpdateSiteName`, and the nested `FileFilter`.
  They are dispatched from `main` inside the same file, so they are now
  `private` or package-private. They had been public because imagej-updater
  users scripted against them; nothing in this build called one.
- **`Checksummer.queueDir`** (both overloads) -- package-private.
- **`FilesCollection.getInstalledVersions`** -- package-private.
  **`analyzeDependencies`, `getFileFromDigest`, `getProtocols`, `toRemove`**
  and the `Filter` factories `hasMetadataChanges()` and `isNoAction()` had no
  caller anywhere and were deleted; `doesPlatformMatch()` survives as a
  package-private `Predicate<FileObject>` factory.
- **`UpToDate.haveNetworkConnection`, `isDeveloper`, `neverRemind`,
  `shouldRemindLater`** -- internal steps of `check()`, now package-private.
- **`Platforms.isLinux` and `isMac`**, **`Class2JarFilesMap.printJarsForClass`**
  (a `main`-style debug dump), **`ByteCodeAnalyzer.getStringConstant` and
  `getClassNameConstant`**, **`UpdaterUtil.toHex`, `stripPrefix`,
  `updateDigest`, `SSH_HOST`, `UPDATE_DIRECTORY`** -- all package-private.
  **`Installer.getUpdaterFiles`** was deleted.

That is roughly 80 members off the exported surface, and it shows up in the
count at the top: 667 exported public members, against 1042 public members
before the pass.

## Architecture: the warts

The first three landed; the last two did not.

- **`FilesCollection extends LinkedHashMap<String, FileObject>`. Landed.**
  The big one. It exported the entire `Map` API -- `putAll`, `entrySet`,
  `merge`, `computeIfAbsent`, all of it -- as public updater API; it forced the
  `@SuppressWarnings("serial")` and the `get(Object)` / `remove(Object)` /
  `put(String,FileObject)` overrides that existed only to re-narrow types.
  `FilesCollection` is now `implements Iterable<FileObject>` over a
  `private final Map<String, FileObject> byFilename`, at 1290 lines rather
  than 1496, with some 60 inherited public members off the module surface. It
  is still simultaneously a collection, a channel resolver, an update-site
  registry, a predicate factory and a conflict store; splitting *that* up was
  not part of this pass.

- **The `Filter` DSL. Landed.** `FilesCollection` carried a nested `Filter`
  interface, around 20 combinator methods and a hand-rolled
  `FilteredIterator`: roughly 250 lines reimplementing `Predicate<FileObject>`
  and `Stream.filter`. `Filter` is now `Predicate<FileObject>`, which brings
  `and`/`or`/`negate` as default methods; `FilteredIterator` is gone.

- **`FileObject.Action` conflated statuses with actions. Landed.** `Action` is
  now the five real verbs -- `UNINSTALL`, `INSTALL`, `UPDATE`, `UPLOAD`,
  `REMOVE` -- each carrying its own label. The display label moved to
  `Status.getLabel()`, and `Status`'s `boolean[] validActions` indexed by
  `Action.ordinal()` became an `EnumSet<Action>` behind `isValid(Action)`.
  `Status` also grew the three `OBSOLETE*` constants it needed once it stopped
  borrowing `Action`'s.

- **Public mutable fields. Not done.** `FileObject` exposes 16 --
  `updateSite`, `originalUpdateSite`, `filename`, `description`, `executable`,
  `coordinate`, `originalCoordinate`, `current`, `previous`, `filesize`,
  `metadataChanged`, `descriptionFromPOM`, `localFilename`, `localChecksum`,
  `localTimestamp`, `localCoordinate` -- and `FileObject.Version` exposes 4
  more (`checksum`, `timestamp`, `timestampObsolete`, `filename`). The three
  `coordinate` fields are new since this document was written, added by the
  Maven coordinate work, so the wart grew rather than shrank.
  `Upload.setAction` still reaches in and writes `file.updateSite` and
  `file.originalUpdateSite` directly, which is exactly the coupling that makes
  the site-identity work in `REMAINING-WORK.md` harder than it needs to be: if
  `updateSite` were behind a setter, "rewrite every file's site attribute on a
  URL match" would have one place to live.

- **Four ways to find the app root, and this one was a live bug. Fixed.**
  `AppLayout.appDirectory()` consults `scijava.app.directory`, `fiji.dir`,
  `imagej.dir`, `ij.dir` in that order. None of the four entry points used it:

  - `FijiUpdater.getAppDirectory()` -- `imagej.dir`, else
    `AppUtils.getBaseDirectory("ij.dir", ...)`;
  - `UpToDate.check()` -- `AppUtils.getBaseDirectory("ij.dir", ...)`;
  - `CommandLine.main()` -- copied `ij.dir` or `fiji.dir` into `imagej.dir`,
    then `AppUtils.getBaseDirectory("imagej.dir", ...)`;
  - `DefaultUpdateService` -- `appService.getApp().getBaseDirectory()`.

  Jaunch sets `scijava.app.directory` and `fiji.dir`, and *not* `imagej.dir`.
  So on a real Fiji installation `UpToDate.isDeveloper()` -- which was
  `System.getProperty("imagej.dir") == null` -- returned true, and
  `UpToDate.check()` returned `DEVELOPER` before doing anything else, which
  meant `CheckForUpdates` silently no-opped on every launch.
  `AppLayout.appRoot()` now returns the `File`, falling back to
  `AppUtils.getBaseDirectory` only when no property is set at all, and all four
  entry points call it: `FijiUpdater:212`, `UpToDate:94`, `CommandLine:1584`,
  `DefaultUpdateService:128`. `UpToDate.isDeveloper()` delegates to
  `AppLayout.isDeveloperSetup()`, which asks the same four properties rather
  than `imagej.dir` alone. `AppLayoutTest` pins the precedence against
  regression.

- **The bootstrap sequence, done four different ways. Not done.** The same
  three steps -- `tryLoadingCollection`, `initializeAndAddSites`,
  `applySitesURLUpdates` -- still appear in `CommandLine.refreshUpdateSites`,
  `FijiUpdater.run` together with its `refreshUpdateSites`,
  `DefaultUpdateService.initFilesCollection`, and `UpToDate.check`, in four
  different orders, with three different logger-passing conventions (`log`,
  `(Logger) log`, `null`). `DefaultUpdateService` still does not call
  `applySitesURLUpdates` at all; `UpToDate` still calls
  `hasUpdateSiteURLUpdates` instead. Wanted: one
  `FilesCollection.bootstrap(...)`, or a small builder, with the URL-change
  review supplied as a callback -- the GUI's dialog, the CLI's `--updateall`
  flag, headless's auto-approve. This is also what makes "Consume `sites.yml`
  directly" and "Identify sites by URL, not by name" land in one place instead
  of four.

### Smaller DRY

Taken:

- `UpdaterUtil.getJarDigest` had three overloads chaining into one. It is now
  two: the 1-argument convenience form and the 4-argument workhorse, which is
  what the tests pass their flags to.
- `Conflicts` had six `Conflict` constructors. It now has two, distinguished
  by whether the conflict names a `FileObject` or a bare filename, with the
  `Severity` enum carrying what the other four encoded.

Not taken:

- `Diff` still has its generic-IO helpers -- `copy`, `getClassVersion` twice,
  `offsetOfFirstDiff`, `isLocal`, `cacheFile` -- which are `protected static`,
  not diffing, and not public surface.
- The five `GroupAction` implementations each still restate `toString()` as
  either a literal or `getLabel(null, emptyList())`. They cannot be collapsed
  into a default method: Java forbids an interface from defaulting an `Object`
  method, so this needs `GroupAction` to become an abstract class first.
  `KeepAsIs` and `Uninstall` are stateless and are still `new`'d on every
  `getValidActions()` call, and again in `UpdaterFrame`'s button setup.

## Still outstanding

What the pass did not cover, and what each is waiting on.

- **`FileObject`'s public mutable fields.** Mechanical but very wide: the 16
  fields above are read directly from every module, and `Version`'s 4 with
  them. Worth doing, and worth doing with the accessor names agreed first,
  since they are the API that replaces them. Note that the coordinate work
  added three more fields in the meantime, so this gets no cheaper by waiting.

- **The bootstrap sequence.** Still four orderings in four entry points.
  This one is not mechanical -- it needs the shape of the URL-change
  callback decided -- and it is the place where `REMAINING-WORK.md`'s
  "Identify sites by URL, not by name" wants to land. Doing them together
  is what makes both cheap.

- **Filename-derived automatic modules.** `miglayout-swing`, `jsch` and
  `jackrabbit-webdav` declare no `Automatic-Module-Name`, so the names the
  GUI and the uploaders `requires` are derived from JAR filenames and are not
  stable. The build says so on every compile: "Required filename-based
  automodules detected", at `WARNING` for the GUI and `INFO` for the two
  uploaders. The fix is upstream, or a shaded dependency, or dropping the
  descriptor from those three modules -- the core, where the encapsulation
  argument actually bites, is unaffected.

- **Exported members with no caller outside their own file.** Down from 84 to
  14, and the survivors are small enough to decide one at a time:
  `FileObject.isForPlatform`, `isUninstallable` and `toDebug`;
  `Conflicts.hasDownloadConflicts`, `hasUploadConflicts` and the `NOTICE`
  constant; `Diff.HEX_DIFF`; `ByteCodeAnalyzer.FIELDS`;
  `ChannelState.CHANNEL_PROPERTY`; `UpdateSiteNetwork.MIRROR_URL_PREFIXES`;
  `AvailableSites.getAvailableSites`. Plus `Installer`'s three `FileVisitor`
  overrides, which are public because the interface is and should stay.

- **Smaller DRY not yet taken:** `Diff`'s generic-IO helpers and the stateless
  `GroupAction` singletons, both described above.

Note the two documents interact: the bootstrap consolidation and the
`FileObject.updateSite` encapsulation are both prerequisites in spirit for
"Identify sites by URL, not by name". If that work is happening anyway, doing
it after this pass is meaningfully cheaper.
