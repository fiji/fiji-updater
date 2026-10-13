# Manual testing protocol: update channels

How to exercise the channel-aware Fiji Updater end to end, on real Fiji-Latest
and Fiji-Stable installations, without writing to any production update site.

The unit tests cover the resolution rules. What they cannot cover is the
Swing prompt, the launcher applying staged files on restart, the hand-off from
the old `imagej-updater` to this one, and what real installations look like.
That is what this protocol is for.

## How it stays off production

- **A sandbox stands in for the core site.** A local HTTP server serves a
  copy of the production `Fiji/db.xml.gz` and whatever the test publishes.
  Blobs it does not have are streamed from `sites.fiji.sc` read-only. See
  `channel-sandbox-server.py`.
- **Every write goes to a local directory.** Sandbox sites use the upload host
  `file:localhost`, so an upload is a file copy. Never enter SSH or WebDAV
  credentials in a test installation.
- **The sandbox keeps the name `Fiji-Latest`.** When a site's URL is not the
  canonical one, the updater recognizes the core site by name
  (`UpdateSiteNetwork.isCoreSite`). A sandbox under any other name would be
  treated as a third-party site, and would test nothing.
- **Test copies only.** Unpack fresh archives or copy installations. Never
  point your everyday Fiji at the sandbox.

Two prompts will try to undo the sandbox. Decline both:

- *Review update site URLs*: the published site list says `Fiji-Latest` lives
  at `https://sites.fiji.sc/Fiji/`. Accepting it moves the installation back
  to production. Only reads are affected, so nothing breaks, but the test is
  no longer testing the sandbox. For the same reason, do not run
  `refresh-update-sites` without `--simulate`.
- *Sites not using HTTPS*: the sandbox is `http://127.0.0.1`. The warning is
  expected; dismiss it.

## Setup

Needs Java 11+, `uv`, `curl`, and about 2 GB of disk per test installation.
The examples use macOS/Linux shell syntax. On Windows, use Git Bash.

### 1. Build the updater

```sh
cd /path/to/fiji-updater
mvn clean install -DskipTests    # clean: stale jars have been seen in target/
export REPO=$PWD
```

### 2. Start the sandbox

```sh
export SANDBOX=~/channel-sandbox
mkdir -p "$SANDBOX/Fiji" "$SANDBOX/Plugin"
curl -fo "$SANDBOX/Fiji/db.xml.gz" https://sites.fiji.sc/Fiji/db.xml.gz
uv run python "$REPO/doc/channel-sandbox-server.py" "$SANDBOX" 8765
```

Leave the server running in its own terminal. Its log shows every request,
and a `proxied` line for every blob fetched from production. That makes it
the easiest place to see which index a client actually read.

Sandbox URLs:

| Site         | URL                             | Upload host      | Upload directory   |
|--------------|---------------------------------|------------------|--------------------|
| `Fiji-Latest`| `http://127.0.0.1:8765/Fiji/`   | `file:localhost` | `$SANDBOX/Fiji/`   |
| `Plugin`     | `http://127.0.0.1:8765/Plugin/` | `file:localhost` | `$SANDBOX/Plugin/` |

### 3. A way to run the new command line

Until the sandbox ships it (step 5), no installation has the new updater. So
maintainer steps run it from the build, against an installation's own
libraries:

```sh
fupd() {
  app=$(cd "$1" && pwd); shift
  "${JAVA:-java}" -Dscijava.app.directory="$app" \
    -Dscijava.app.config-file="$app/config/jaunch/fiji.cfg" \
    -cp "$REPO/core/target/fiji-updater-3.0.0-SNAPSHOT.jar:$(ls "$app"/jars/scijava-common-*.jar "$app"/jars/parsington-*.jar "$app"/jars/app-launcher-*.jar | tr '\n' :)" \
    sc.fiji.updater.cli.CommandLine "$@"
}
```

If the `java` on your `PATH` is older than 11, set `JAVA` to a newer one.
The one bundled under the installation's `java/` directory will do.

Once an installation has taken the hand-off (A1), `./fiji --update <command>`
does the same thing.

There is no command that prints the current channel. Read
`config/jaunch/fiji.cfg`: `updater-channel=` with no value, or no such line,
means the base channel (*Origin*). No file at all means *unknown*; see K1.

### 4. The maintainer installation

This one publishes to the sandbox. It stands in for the release engineer.

```sh
export MAINT=~/channel-test/maint
# Unpack a fresh Fiji-Latest archive into $MAINT, then:
fupd "$MAINT" update                   # level with production (read-only)
touch "$MAINT/config/jaunch/fiji.cfg"  # see K1
fupd "$MAINT" edit-update-site Fiji-Latest http://127.0.0.1:8765/Fiji/ \
  file:localhost "$SANDBOX/Fiji/"
fupd "$MAINT" list-not-uptodate        # expect nothing
```

If `update` staged anything into `update/`, start the installation once, so
the launcher moves it into place.

If anything is listed, the installation does not match the copied index, and
uploads from it would publish that difference. Fix that before going on.

### 5. Publish the hand-off to the sandbox's base channel

This is the release that production's root will eventually carry: the new
updater in, the old one out, and the launcher's `--update` entry point
switched over.

```sh
cp "$REPO"/core/target/fiji-updater-3.0.0-SNAPSHOT.jar \
   "$REPO"/gui/target/fiji-updater-gui-3.0.0-SNAPSHOT.jar \
   "$REPO"/uploader-ssh/target/fiji-updater-uploader-ssh-3.0.0-SNAPSHOT.jar \
   "$REPO"/uploader-webdav/target/fiji-updater-uploader-webdav-3.0.0-SNAPSHOT.jar \
   "$MAINT/jars/"
rm "$MAINT"/jars/imagej-updater-*.jar "$MAINT"/jars/imagej-plugins-uploader-*.jar
sed -i.bak 's/net\.imagej\.updater\.CommandLine/sc.fiji.updater.cli.CommandLine/' \
  "$MAINT/config/jaunch/fiji.toml" && rm "$MAINT/config/jaunch/fiji.toml.bak"

HANDOFF="jars/fiji-updater.jar jars/fiji-updater-gui.jar
  jars/fiji-updater-uploader-ssh.jar jars/fiji-updater-uploader-webdav.jar
  jars/imagej-updater.jar jars/imagej-plugins-uploader-ssh.jar
  jars/imagej-plugins-uploader-webdav.jar config/jaunch/fiji.toml"
fupd "$MAINT" upload --simulate --update-site Fiji-Latest \
  --forget-missing-dependencies $HANDOFF
fupd "$MAINT" upload --update-site Fiji-Latest \
  --forget-missing-dependencies $HANDOFF
```

`--forget-missing-dependencies` is needed because `imagej-ui-swing` still
declares a dependency on `imagej-updater`. See K3.

Keep a checksum of the base index. Several cases below check that it did
not change:

```sh
shasum "$SANDBOX/Fiji/db.xml.gz" > "$SANDBOX/origin.sha"
```

### 6. Mint a channel

Minting happens on the server, so it is done here by hand: the new channel
starts as a copy of the base, and the manifest names it.

```sh
mkdir "$SANDBOX/Fiji/T.alpha"
cp "$SANDBOX/Fiji/db.xml.gz" "$SANDBOX/Fiji/T.alpha/"
printf 'T.alpha\n' > "$SANDBOX/Fiji/channels.txt"
```

Then make T.alpha differ from Origin in ways a tester can see. The maintainer
moves to the channel and publishes from there:

```sh
fupd "$MAINT" upgrade T.alpha      # nothing to install yet: T.alpha == Origin
grep updater-channel "$MAINT/config/jaunch/fiji.cfg"   # updater-channel=T.alpha

echo "Edited for T.alpha." >> "$MAINT/WELCOME.md"      # an update
rm "$MAINT/luts/glow.lut"                              # a removal
mkdir -p "$MAINT/scripts/Help"                         # an addition
echo 'showMessage("Channel T.alpha");' > "$MAINT/scripts/Help/Channel_T_alpha.ijm"

fupd "$MAINT" upload --update-site Fiji-Latest \
  WELCOME.md luts/glow.lut scripts/Help/Channel_T_alpha.ijm
shasum -c "$SANDBOX/origin.sha"   # OK: publishing to T.alpha left Origin alone
```

Each change is visible in the running application. The LUT disappears from
*Image › Lookup Tables*, the script appears under *Help*, and `WELCOME.md` is
readable.

Optional, for case A9: also change `-Dscijava.app.java-version-recommended=21`
in `config/jaunch/fiji.toml` to `25`, and upload `config/jaunch/fiji.toml` too.

### 7. A third-party site

Create a second maintainer in the same way as step 4, or reuse `$MAINT` after
moving it back with `fupd "$MAINT" upgrade Origin`. Publish one file to the
`Plugin` site from the base channel:

```sh
fupd "$MAINT" add-update-site Plugin http://127.0.0.1:8765/Plugin/ \
  file:localhost "$SANDBOX/Plugin/"
mkdir -p "$MAINT/scripts/Plugins"
echo 'showMessage("Plugin, base release");' > "$MAINT/scripts/Plugins/Sandbox_Plugin.ijm"
fupd "$MAINT" upload --update-site Plugin scripts/Plugins/Sandbox_Plugin.ijm
```

## Test installations

Make a fresh copy for each case that changes an installation. Name the copies
so the result sheet can say which one was used.

| Id        | What                                                  | Why                                                  |
|-----------|-------------------------------------------------------|------------------------------------------------------|
| `L-fresh` | Fiji-Latest, unpacked and not touched                 | What most users have; there is no `fiji.cfg` (K1)    |
| `L-cfg`   | Fiji-Latest with a `fiji.cfg`                         | e.g. after *Edit › Options › Memory & Threads*       |
| `S`       | Fiji-Stable, the ImageJ/Fiji/Java-8 trio, on Java 8   | Runs the old updater and must not notice channels    |

To point a test installation at the sandbox before the hand-off, use the old
updater:

```sh
./fiji --update edit-update-site Fiji-Latest http://127.0.0.1:8765/Fiji/
```

Or use *Help › Update… › Manage update sites* and edit the URL. Decline the URL
review afterwards.

## Track A: Fiji-Latest

Unless a case says otherwise, start from `L-cfg`, pointed at the sandbox,
before step 6 has been run.

**A1. Hand-off from the old updater.** Run *Help › Update…*. This is still
the old updater. Apply the updates it offers and restart.
- Offered: the four `fiji-updater` jars and `fiji.toml`. `imagej-updater`
  and the old uploaders are to be removed.
- After restart, `jars/` has `fiji-updater-*.jar` and no
  `imagej-updater-*.jar`.
- *Help › Update…* opens the new updater: its file table has a *Channel*
  column.
- `./fiji --update` prints a usage listing `upgrade [--simulate] [<channel>]`.
- Record every exception in the console or *Window › Console* at startup and
  on opening the updater. They are expected (K3), but it has to be known which
  ones appear.

**A2. Base channel, before any channel exists.** Open the updater.
- "Your ImageJ is up to date!"; there is no channel prompt and no channel
  warning.
- The server log shows `Fiji/db.xml.gz` read and no `channels.txt` hit
  beyond a 404.

Now run setup step 6. A3 onwards need T.alpha to exist.

**A3. The offer, declined.** Open the updater.
- A dialog headed *T.alpha is available*. It lists what would change, names
  no lagging sites, and says Fiji will need to restart.
- Click *Not now*. The main window should show the installation as up to date
  on Origin. **Known to fail** (K5): it currently shows T.alpha's changes as
  pending updates. Do not apply them. Close the updater, and check that
  `fiji.cfg` still has no channel.
- Reopen, choose *Never ask again*, then reopen once more: no dialog. To reset,
  remove `skipChannelUpgradePrompt` from the updater preferences.

**A4. The offer, accepted (GUI).** Use a fresh copy of the A1 result.
- The dialog's counts should cover the update (`WELCOME.md`), the removal
  (`glow.lut`) and the install (the script). **Known to under-report**
  (K2): expect only the install to be counted. Record what it says.
- Accept. The updater says the installation now follows T.alpha and asks for
  a restart. The session ends: no main window opens behind the message.
- `fiji.cfg` has `updater-channel=T.alpha`.
- After restart, `glow.lut` is gone from *Image › Lookup Tables*, the
  *Help › Channel T alpha* script runs, and `WELCOME.md` has the new line.
- Reopen the updater: up to date. In *Advanced mode*, core files show
  *T.alpha* in the Channel column, and a file's details name its channel.

**A5. The offer when not up to date.** Before step 6, publish one more
base-channel change from `$MAINT`, for example a line added to `README.md`.
Leave one A1 copy without that update. Mint the channel, then open the
updater on that copy.
- It explains that an upgrade to T.alpha is available but the installation
  must be updated first. There is no upgrade dialog.

**A6. Upgrade from the command line.** Use a fresh copy of the A1 result.
```sh
./fiji --update upgrade --simulate
./fiji --update upgrade
```
- The first prints `Upgrading from Origin to T.alpha` followed by the planned
  changes, and changes nothing: compare `fiji.cfg` and `update/` before and
  after. The listing under-reports (K2).
- The second stages the changes into `update/`, writes
  `updater-channel=T.alpha`, and says to restart. After restart, check as
  in A4.

**A7. Back to Origin.** On the A4 or A6 result:
```sh
./fiji --update upgrade --simulate Origin
./fiji --update upgrade Origin
```
- Prints `Downgrading from T.alpha to Origin`.
- After restart: `glow.lut` is back, the script is gone, and `WELCOME.md` has
  lost its line. `fiji.cfg` has `updater-channel=` with no value.
- Options go before the channel name. `upgrade Origin --simulate` is a usage
  error.

**A8. Third-party site fallback.** On a T.alpha installation, add the site:
`./fiji --update add-update-site Plugin http://127.0.0.1:8765/Plugin/`.
- No manifest on `Plugin`: its script installs from the site root, and there
  is no warning.
- `printf 'S.other\n' > "$SANDBOX/Plugin/channels.txt"` makes the site look
  as if it has engaged with channels without publishing T.alpha. Reopen the
  updater, and expect: "The following update sites have not published for
  T.alpha yet: Plugin". It still reads the root. Remove the file afterwards.
- Publish to `Plugin` from `$MAINT` while `$MAINT` is on T.alpha. Then
  `$SANDBOX/Plugin/T.alpha/db.xml.gz` and a `channels.txt` listing T.alpha
  both exist, the root index is unchanged, and the T.alpha installation now
  reads `Plugin/T.alpha/db.xml.gz` (see the server log). A file's details say
  *Channel: T.alpha*.
- That upload is the site's first to T.alpha. From the command line, it
  proceeds and logs "This is the first upload to T.alpha for the 'Plugin'
  update site", with what Origin users will keep seeing. Repeat it from the
  GUI on a fresh `Plugin` site, uploading with *Advanced mode › Upload*. The
  same text should appear as a yes/no question, and *No* cancels the upload.

**A9. A channel that needs a newer Java** (only if step 6's optional change
was published). Accept the upgrade on a copy running Java 21.
- The dialog lists installing Java 25. Before asking for a restart, the
  updater downloads it; this reads `downloads.imagej.net`.
- After restart, Fiji runs on Java 25 (*Help › About*, or the console).
  `fiji.cfg` records the new `jvm-dir`.

**A10. The core site loses the channel.** On a T.alpha installation, run
`mv "$SANDBOX/Fiji/T.alpha" "$SANDBOX/Fiji/T.alpha.hidden"` and open the
updater.
- A warning starting "The core update site does not serve the T.alpha
  channel", which says it will not fall back to an older edition.
- Nothing is offered for installation or removal. In particular, nothing from
  Origin, and core files are not marked for uninstall.
- Move the directory back afterwards.

**A11. Two channels.** Mint `T.beta` from T.alpha the same way as step 6, with
`printf 'T.beta\nT.alpha\n'` for the manifest. Add a visible change from
`$MAINT` after `fupd "$MAINT" upgrade T.beta`.
- An Origin installation is offered T.beta, the newest, directly.
- A T.alpha installation is offered T.beta.
- A T.beta installation with `Plugin` added, where `Plugin` has T.alpha but
  not T.beta, reads `Plugin/T.alpha/db.xml.gz`, not the root, and warns that
  `Plugin` has not published for T.beta.
- `upgrade T.alpha` from T.beta says *Downgrading*.

**A12. Publishing stays in its lane.** For each upload in steps 6–7 and A8,
`shasum -c "$SANDBOX/origin.sha"` still passes, and only the uploading
installation's own channel directory changed.

**A13. Refusals.** On a copy of a T.alpha installation, delete
`config/jaunch/fiji.cfg`.
- Opening the updater or running `./fiji --update list` shows "Cannot
  determine which update channel this installation follows, so no update
  sites were checked." Nothing is offered.
- An upload fails with "no way to know which channel to publish to".
- `./fiji --update upgrade` fails with "Cannot determine which update channel
  this installation follows".

**A14. A fresh installation after the first channel** (K1). Take `L-fresh`,
point it at the sandbox, take the hand-off (A1), and *then* mint the
channel. Check `config/jaunch/fiji.cfg` exists before continuing. If it does
not, open the updater.
- **Currently**: every update site is refused, as in A13, and no upgrade is
  offered. That is K1. Record it, because this is the state most real
  installations would be in on minting day.

## Track B: Fiji-Stable

**B1. Stable does not notice channels.** On `S`, add the `Plugin` site after
A8 has given it a T.alpha index and a manifest. Use the old updater's
*Manage update sites*, or `./ImageJ-<platform> --update add-update-site
Plugin http://127.0.0.1:8765/Plugin/`.
- It installs `Plugin`'s base release, not T.alpha's, with no errors and no
  mention of channels.
- The server log shows `Plugin/db.xml.gz` read and nothing under
  `Plugin/T.alpha/`.

**B2. An old updater publishing to a site with channels.** From `S`, or any
installation still running `imagej-updater`, upload a change to `Plugin`
with the upload host `file:localhost`.
- The root `db.xml.gz` changes. `Plugin/T.alpha/db.xml.gz` and
  `channels.txt` are byte-for-byte unchanged.
- A T.alpha client still reads the T.alpha index. An Origin client sees the
  new upload.

**B3. From Stable to Fiji-Latest, then onto the sandbox.** On a copy of `S`,
run the `imagej-ui-swing` migration: start Fiji with `UPGRADE_IMAGEJ=1` in
the environment. The migration targets production Fiji-Latest, which it only
reads.
- Record the dialogs and the outcome, including whether the
  `ResolveDependencies` step reported problems (the `//FIXME abort` in
  `LauncherMigrator`).
- After restart: Fiji-Latest active, the ImageJ/Fiji/Java-8 sites disabled,
  and Java 21.
- Does `config/jaunch/fiji.cfg` exist? If the migration installed Java, it
  should, because app-launcher records `jvm-dir` there. Record the answer
  either way: without the file the migrated installation is in K1.
- Then point Fiji-Latest at the sandbox and continue with A1 and A3–A7. That
  is the path a Fiji-Stable user actually takes to reach a channel.

## Known gaps: expected failures

These are known and listed in `REMAINING-WORK.md`. The protocol runs into
them on purpose, so that each one is seen once on a real installation.

- **K1. No `fiji.cfg` means an unknown channel.** Fiji-Latest does not ship
  `config/jaunch/fiji.cfg`. It appears only when something writes it: the
  memory dialog, or app-launcher installing a Java. Without it
  `ChannelState` answers *unknown*, and once any channel exists the updater
  refuses every site and cannot upgrade (A13, A14).
- **K2. The upgrade preview under-reports.** The GUI dialog, `upgrade` and
  `upgrade --simulate` list the installs and the stranded files, but not
  updates or removals of obsoleted files. Those actions are only decided in
  `ChannelUpgrade.stage`. What gets applied is correct; what is shown before
  it is not.
- **K3. Coexistence with `imagej-updater`.** `imagej-ui-swing` still carries
  the old updater UI and `LauncherMigrator`, which need `imagej-updater`.
  After the hand-off, expect errors when those classes are loaded. A stray
  `imagej-updater` jar puts two *Update…* commands and a channel-unaware
  writer into the same installation. Detecting it is not implemented.
- **K4. Channel name case.** Not canonicalized. On macOS and Windows the
  sandbox serves `t.alpha/` for `T.alpha/`; Linux servers do not. To see
  production behavior, run the sandbox on Linux.
- **K5. A declined offer leaves the session on the offered channel.**
  `ChannelUpgrade.reconcile` pins the collection to the target, and
  `ChannelUpgradePrompt` does not undo that when the user says *Not now*,
  chooses *Never ask again*, or the examination fails. The main window then
  offers the target channel's files as updates, on an installation that
  still declares the old channel (A3).
- **K6. An unreachable manifest reads as "no channels".** Demonstrate it on a
  T.alpha copy: delete `fiji.cfg`, then
  `mv "$SANDBOX/Fiji/channels.txt" "$SANDBOX/Fiji/channels.txt.hidden"`.
  Instead of refusing as in A13, the updater now reads the installation as
  Origin. It offers to install `glow.lut` again, shows T.alpha's changed
  files as locally modified, and shows the T.alpha-only script as local-only.
  From there, `update-force` would downgrade the installation. This is the
  mass-downgrade path, reached here through a 404 where in the field it would
  be a network failure. Restore both files afterwards.

Not covered: mirrors (these are compiled in, point at production, and have no
selector yet), the `sites.yml` switch, and site ids.

## Recording results

For each case, record: the case id, the installation id, the OS and
architecture, pass/fail/known, and, for anything other than a pass, the
console output and the matching lines of the server log. The server log is
the most useful attachment. It shows which index each client read, and that
nothing wrote to production: every `proxied` line is a GET.
