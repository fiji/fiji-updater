# Fiji Updater: paring down the public API

State as of the fiji-updater `main` branch at `8217251`. A companion to
`REMAINING-WORK.md`, which covers the channel work; this covers the API
surface, the package layout, and the data-model warts inherited from
imagej-updater. The two interact in a few places, noted where they do.

Measured rather than estimated: `javap` over the compiled core, cross-
referenced against every call site in all four modules plus tests.

- 63 public types in `fiji-updater`, exposing 1042 public members.
- 84 of those public members are called from nowhere outside their own file.
- 23 `@Deprecated` members still ship.

## What has landed

Steps 1, 2, 4 and the `FilesCollection` half of step 5 are done; the suite
is green throughout. Not done: the package split and `module-info` (step 3),
`FileObject` encapsulation, and the bootstrap consolidation. See
"Still outstanding" at the end for what that leaves and why.

Two claims below turned out to be wrong when checked against the code, and
are corrected in place: `Diff(PrintStream)` and `UpdaterUtil.getJarDigest`'s
3-argument overload.

## Package layout for JPMS

The core's problem is not the root package, it is `sc.fiji.updater.util` --
a junk drawer of 25 classes spanning six unrelated concerns. It has to be
exported wholesale today because `UpdaterUtil` lives in it, and `UpdaterUtil`
is the most-referenced class in the build.

What the cross-module reference table actually says:

- **Referenced by nobody outside core.** `XMLFileReader`, `XMLFileWriter`,
  `XMLFileDownloader`, `XMLFileErrorHandler`, `POMParser`,
  `DependencyAnalyzer`, `Class2JarFilesMap`, `DllFile`, `Downloader`,
  `Downloadable`, `SkipHashedLines`, `FilterManifest`, `Channels`,
  `Platforms`, `UpdateSiteNetwork`, `UpToDate`, `CheckForUpdates`,
  `PromptUserToUpdate`, `StderrProgress`, `CommandLine`.
- **Referenced only by the GUI.** `Diff`, `ByteCodeAnalyzer`, `Conflicts`,
  `Installer`, `Checksummer`, `GroupAction` and `action/*`, `URLChange`,
  `ChannelUpgrade`, `AvailableSites`, `HTTPSUtil`, `JavaRequirement`,
  `ChannelManifest`, `ChannelState`, `AppLayout`.
- **Referenced by the uploaders.** `Uploadable`, `Uploader`,
  `AbstractUploader`, `FilesUploader`, `UpdateSite`, `UpdaterUserInterface`,
  `UpdaterUtil`.

### Proposed core packages

Exported unless marked otherwise; the parenthetical names which module
outside core needs it.

```
sc.fiji.updater          FilesCollection, FileObject, Dependency, UpdateSite,
                         Conflicts, Installer, Checksummer, GroupAction,
                         UpdateService, UpdaterUI
sc.fiji.updater.action   the five GroupActions                    (gui)
sc.fiji.updater.upload   Uploadable, Uploader, AbstractUploader,
                         UploadableFile, FilesUploader, UploaderService,
                         FileUploader                             (ssh, webdav)
sc.fiji.updater.progress Progress, Progressable, AbstractProgressable,
                         StderrProgress
sc.fiji.updater.ui       UpdaterUserInterface            (gui, ssh, webdav)
sc.fiji.updater.channel  Channels, ChannelState, ChannelManifest,
                         ChannelUpgrade, URLChange
sc.fiji.updater.app      AppLayout, Platforms, JavaRequirement
sc.fiji.updater.site     AvailableSites, UpdateSiteNetwork, HTTPSUtil
sc.fiji.updater.diff     Diff, ByteCodeAnalyzer                   (gui)

  -- not exported --
sc.fiji.updater.xml      XMLFileReader, XMLFileWriter, XMLFileDownloader,
                         XMLFileErrorHandler, POMParser
sc.fiji.updater.cli      CommandLine, UpToDate, CheckForUpdates,
                         PromptUserToUpdate
sc.fiji.updater.internal UpdaterUtil, SkipHashedLines, FilterManifest,
                         Downloader, Downloadable, DependencyAnalyzer,
                         Class2JarFilesMap, DllFile
```

Two things block the `internal` package as drawn, and both are worth fixing
on their own merits.

- **`UpdaterUtil` cannot be internal as it stands**, but its external surface
  is tiny: the GUI and the two uploaders together use exactly 8 of its ~35
  members -- `timestamp`, `openStream`, `openConnection`, `getLastModified`,
  `useSystemProxies`, `isProtectedLocation`, `getLogService`, `PREFS_USER`.
  Split those into small exported homes (a `Timestamps` class, and the
  network helpers onto `sc.fiji.updater.site`); the other 27 -- `getJarDigest`
  three times over, `join`, `iterate`, `toCamelCase`, `realloc`, `readFile`,
  `writeFile`, the `hex` char array -- go internal or go away.
  `getPlatform()` is already deprecated in favour of `Platforms.current()`
  and is called by nobody.

- **`Installer extends Downloader`** forces `Downloader` and `Downloadable`
  to be exported. A public class inheriting from a non-exported one is legal,
  but it leaks `start(Iterable<Downloadable>)` into the signature. Make
  `Installer` *own* a `Downloader` instead: a one-field change that removes
  two types from the API.

`requires` for the core module is then `org.scijava.common` (automatic),
`app.launcher`, `java.xml`, and `java.desktop`. The last is dragged in solely
by `UpdaterUserInterface.addWindow(Frame)` and the `GraphicsEnvironment` call
in `UpdaterUtil.isGPRActivated` -- worth removing, since it puts AWT in the
requires list of a core that is otherwise headless-capable.

## Legacy API to delete

### Delete outright

Dead, or dead the moment we stop pretending imagej-updater callers exist.

- **`sc.fiji.updater.util.StderrLogService`** -- already `@Deprecated`, and
  nothing references it. Every call site (`FilesCollection`, `UpdaterUtil`,
  `SSHFileUploader`, `WebDAVUploader`) uses `org.scijava.log.StderrLogService`
  instead. A pure duplicate.
- **`UpdaterUIPlugin`** -- a `@Deprecated` marker interface with zero
  references anywhere in the build.
- **`InputStream2OutputStream`** -- zero references in core; used only by
  `SSHFileUploader`. Move the 20 lines into the ssh module.
- **`FileObject.markRemoved()`** -- `@Deprecated`, and its body is
  `throw new UnsupportedOperationException`.
- **`FileObject.Status.getActions()`** -- `@Deprecated`, with a body
  identical to `getDeveloperActions()`.
- **`FilesCollection.getActions(FileObject)` and `getActions(Iterable)`** --
  `@Deprecated`, superseded by `getValidActions`, called by nobody.
- **`CommandLine()` no-arg constructor and `CommandLine.getInstance()`** --
  both `@Deprecated`, both unused.
- **Four `@Deprecated` `Conflicts.Conflict` constructors**, on a class that
  already has two good ones.
- **The remaining `@Deprecated` set**, all with live replacements:
  `FilesUploader.initialUpload(String,String,String)`,
  `Downloader()`, `FilesCollection.getUpdateSite(String)`,
  `FilesCollection.getUpdateSiteNames()`,
  `FileObject.addPreviousVersion(String,long,String)`,
  `FileObject.isUploadable(FilesCollection)`, `UpdaterUtil.getPlatform()`.
  Also the two superseded `FilesUploader` constructors and
  `FilesCollection.get(int)`, whose body threw.

  Not `Diff(PrintStream)`: it is the class's *only* constructor, with three
  live call sites, so there is nothing to migrate to. The misleading
  `@Deprecated` came off instead.

### Demote from public to package-private

The largest single reduction, and it changes no behaviour.

- **`CommandLine`'s 19 public command methods** -- `listCurrent`,
  `listModified`, `listShadowed`, `listUptodate`, `listUpdateable`,
  `listNotUptodate`, `listFromSite`, `listLocalOnly`, `listUpdateSites`,
  `history`, `usage`, `uploadCompleteSite`, `addUploadSites`,
  `addOrEditUploadSite`, `removeUploadSite`, `revertUnrealChanges`,
  `chooseUploadSite`, `getLongUpdateSiteName`, and the nested `FileFilter`.
  They are dispatched from `main` inside the same file. They are public
  because imagej-updater users scripted against them; nothing in this build
  calls one.
- **`Checksummer.queueDir`** (both overloads).
- **`FilesCollection.analyzeDependencies`, `getFileFromDigest`,
  `getProtocols`, `getInstalledVersions`, `toRemove`**, and the `Filter`
  factories `doesPlatformMatch()`, `hasMetadataChanges()`, `isNoAction()` --
  no caller anywhere.
- **`UpToDate.haveNetworkConnection`, `isDeveloper`, `neverRemind`,
  `shouldRemindLater`** -- internal steps of `check()`.
- **`Installer.getUpdaterFiles`**, **`Platforms.isLinux` and `isMac`**,
  **`Class2JarFilesMap.printJarsForClass`** (a `main`-style debug dump),
  **`ByteCodeAnalyzer.getStringConstant` and `getClassNameConstant`**,
  **`UpdaterUtil.toHex`, `stripPrefix`, `updateDigest`, `SSH_HOST`,
  `UPDATE_DIRECTORY`**.

That is roughly 80 members off the exported surface.

## Architecture: the warts worth fixing now

- **`FilesCollection extends LinkedHashMap<String, FileObject>`.** The big
  one. It exports the entire `Map` API -- `putAll`, `entrySet`, `merge`,
  `computeIfAbsent`, all of it -- as public updater API; it forces the
  `@SuppressWarnings("serial")` and the `get(Object)` / `remove(Object)` /
  `put(String,FileObject)` overrides that exist only to re-narrow types; and
  it leaves `FilesCollection` simultaneously a map, an `Iterable<FileObject>`,
  a channel resolver, an update-site registry, a filter factory and a conflict
  store, at 1496 lines. Composition (a `private final Map<String,FileObject>`)
  costs a handful of delegating methods and removes some 60 inherited public
  members from the module surface.

- **The `Filter` DSL.** `FilesCollection` carries a nested `Filter` interface,
  around 20 combinator methods (`yes`, `not`, `or`, `and`, `is`, `oneOf`
  twice, `startsWith` twice, `endsWith`, `isUpdateSite`, `doesPlatformMatch`,
  ...) and a hand-rolled `FilteredIterator`: roughly 250 lines reimplementing
  `Predicate<FileObject>` and `Stream.filter`. Moving `Filter` to
  `Predicate<FileObject>` gets `and`/`or`/`negate` for free as default
  methods, deletes `FilteredIterator`, and lets the ~20 one-line
  `toUpdate()` / `installed()` / `uninstalled()` / `locallyModified()`
  accessors become static predicate constants. Since we are taking the API
  break anyway, this is the moment.

- **`FileObject.Action` conflates statuses with actions.** Twelve constants,
  of which seven -- `LOCAL_ONLY`, `NOT_INSTALLED`, `INSTALLED`, `UPDATEABLE`,
  `MODIFIED`, `NEW`, `OBSOLETE` -- are *statuses* wearing an `Action` label,
  existing only so `Status.getNoAction()` can return `actions[0]` as a display
  string. Split it: `Action` becomes the five real verbs (`INSTALL`, `UPDATE`,
  `UNINSTALL`, `UPLOAD`, `REMOVE`), and the display label moves to `Status`,
  where it belongs. `Status`'s `boolean[] validActions` indexed by
  `Action.ordinal()` then becomes an `EnumSet<Action>`.

- **Public mutable fields.** `FileObject` exposes 12 -- `updateSite`,
  `originalUpdateSite`, `filename`, `description`, `executable`, `current`,
  `previous`, `filesize`, `metadataChanged`, `descriptionFromPOM`,
  `localFilename`, `localChecksum`, `localTimestamp` -- and
  `FileObject.Version` exposes 4 more. `Upload.setAction` reaches in and
  writes `file.updateSite` and `file.originalUpdateSite` directly, which is
  exactly the coupling that makes the site-identity work in
  `REMAINING-WORK.md` harder than it needs to be: if `updateSite` were behind
  a setter, "rewrite every file's site attribute on a URL match" would have
  one place to live.

- **Four ways to find the app root, and this one is a live bug.**
  `AppLayout.appDirectory()` consults `scijava.app.directory`, `fiji.dir`,
  `imagej.dir`, `ij.dir` in that order, and is the considered answer. None of
  the four entry points use it:

  - `FijiUpdater.getAppDirectory()` -- `imagej.dir`, else
    `AppUtils.getBaseDirectory("ij.dir", ...)`;
  - `UpToDate.check()` -- `AppUtils.getBaseDirectory("ij.dir", ...)`;
  - `CommandLine.main()` -- copies `ij.dir` or `fiji.dir` into `imagej.dir`,
    then `AppUtils.getBaseDirectory("imagej.dir", ...)`;
  - `DefaultUpdateService` -- `appService.getApp().getBaseDirectory()`.

  Jaunch sets `scijava.app.directory` and `fiji.dir`, and *not* `imagej.dir`.
  So on a real Fiji installation `UpToDate.isDeveloper()` -- which is
  `System.getProperty("imagej.dir") == null` -- returns true, and
  `UpToDate.check()` returns `DEVELOPER` before doing anything else, which
  means `CheckForUpdates` silently no-ops on every launch. The GUI path falls
  through to `AppUtils`' jar-location guessing rather than the directory the
  launcher named. Wanted: one helper, `AppLayout.appRoot()` returning a
  `File`, called from all four.

- **The bootstrap sequence, done four different ways.** The same four steps --
  `tryLoadingCollection`, `checkHTTPSSupport`, `initializeAndAddSites`,
  `applySitesURLUpdates` -- appear in `CommandLine.refreshUpdateSites`,
  `FijiUpdater.run` together with its `refreshUpdateSites`,
  `DefaultUpdateService.initFilesCollection`, and `UpToDate.check`, in four
  different orders, with three different logger-passing conventions (`log`,
  `(Logger) log`, `null`). `DefaultUpdateService` does not call
  `applySitesURLUpdates` at all; `UpToDate` calls `hasUpdateSiteURLUpdates`
  instead. Wanted: one `FilesCollection.bootstrap(...)`, or a small builder,
  with the URL-change review supplied as a callback -- the GUI's dialog, the
  CLI's `--updateall` flag, headless's auto-approve. This is also what makes
  "Consume `sites.yml` directly" and "Identify sites by URL, not by name"
  land in one place instead of four.

### Smaller DRY

- `HTTPSUtil.checkHTTPSSupport` is called from four places and is
  idempotent by accident; fold it into the bootstrap.
- `Diff` has 11 `protected static` helpers -- `copy`, `getClassVersion`
  twice, `offsetOfFirstDiff`, `isLocal`, `cacheFile` -- that are generic IO,
  not diffing.
- `UpdaterUtil.getJarDigest` has three overloads chaining into one; the 3-arg
  form is called only from tests, which can pass the flag explicitly.
- `Conflicts` has six `Conflict` constructors for what is now one `Severity`
  enum plus two shapes.
- The five `GroupAction` implementations each restate `toString()` as either
  a literal or `getLabel(null, emptyList())`. They cannot be collapsed into a
  default method: Java forbids an interface from defaulting an `Object`
  method, so this needs `GroupAction` to become an abstract class first.
  `KeepAsIs` and `Uninstall` are stateless and are `new`'d on every
  `getValidActions()` call.

## Suggested order

1. **Deletions.** Pure subtraction, no design decisions, and it makes
   everything after it smaller.
2. **`AppLayout.appRoot()`.** A bug fix, and independent of the rest.
3. **Package split plus `module-info.java`.** Do this *before* the data-model
   work, because the compiler then reports every place the new boundaries are
   violated. Adding `module-info` to all four modules at once is what turns
   "should this be public" from a judgement call into a build error.
4. **`Filter` to `Predicate`, `Installer` owning a `Downloader`, the
   `Action`/`Status` split.** Mechanical, and well covered by the existing
   suite (the 1312-line `UpdaterTest` plus the channel tests).
5. **`FilesCollection` composition over inheritance, `FileObject`
   encapsulation.** The largest change, and the one that should land before
   the update-site-identity work in `REMAINING-WORK.md` rather than after.

Note the two documents interact: the bootstrap consolidation and the
`FileObject.updateSite` encapsulation are both prerequisites in spirit for
"Identify sites by URL, not by name". If that work is happening anyway, doing
it after this pass is meaningfully cheaper.

## Still outstanding

What steps 1, 2, 4 and the `FilesCollection` composition did not cover, and
what each is waiting on.

- **The package split and `module-info.java`** (step 3). The largest churn
  in the document and the one with a decision in it: the package names above
  are a proposal, and every import in 118 files follows whatever is chosen.
  Two of its prerequisites are now in place -- `Installer` no longer extends
  `Downloader`, so `Downloader` and `Downloadable` are free to be internal --
  but `UpdaterUtil` still needs its 8 externally-used members split out
  before `sc.fiji.updater.internal` can hold it.

- **`FileObject`'s public mutable fields.** Mechanical but very wide:
  `filename`, `updateSite`, `current`, `previous` and the rest are read
  directly from every module. Worth doing, and worth doing with the
  accessor names agreed first, since they are the API that replaces them.

- **The bootstrap sequence.** Still four orderings in four entry points.
  This one is not mechanical -- it needs the shape of the URL-change
  callback decided -- and it is the place where `REMAINING-WORK.md`'s
  "Identify sites by URL, not by name" wants to land. Doing them together
  is what makes both cheap.

- **Smaller DRY not yet taken:** folding `HTTPSUtil.checkHTTPSSupport` into
  the bootstrap (waits on the bootstrap), `Diff`'s generic-IO helpers (they
  are `protected static`, so not public surface, and moving them is a
  judgement call about where they belong), and the stateless `GroupAction`
  singletons (three allocations per `getValidActions()` call, against a new
  public constant on each of three classes).
