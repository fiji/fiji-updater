/*
 * #%L
 * Fiji software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2026 Fiji developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package sc.fiji.updater;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipException;

import sc.fiji.updater.Conflicts.Conflict.Severity;
import sc.fiji.updater.Conflicts.Conflict;
import sc.fiji.updater.Conflicts.Resolution;
import sc.fiji.updater.FileObject.Status;
import sc.fiji.updater.app.AppLayout;
import sc.fiji.updater.app.Platforms;
import sc.fiji.updater.internal.UpdaterUtil;
import sc.fiji.updater.progress.AbstractProgressable;
import sc.fiji.updater.progress.Progress;
import sc.fiji.updater.xml.POMParser;

/**
 * A class to checksum and timestamp all the files shown in the Updater's UI.
 * <p>
 * This class essentially determines which files are recognized and evaluated
 * by the updater. The general process is to use the {@link #queue} series of
 * methods to register files of interest, and then the {@link #handle} methods
 * to do the checksumming, flagging "conflicts" such as obsolete or local-only
 * files in the process.
 * </p>
 * 
 * @author Johannes Schindelin
 * @author Yap Chin Kiet
 */
public class Checksummer extends AbstractProgressable {

	private final FilesCollection files;
	private int counter, total;
	private Map<String, FileObject.Version> cachedChecksums;
	private final boolean isWindows; // time tax for Redmond
	private Map<String, List<StringAndFile>> queue;

	public Checksummer(final FilesCollection files, final Progress progress) {
		this.files = files;
		if (progress != null) addProgress(progress);
		setTitle("Checksummer");
		isWindows = Platforms.isWindows(files.platform());
	}

	protected static class StringAndFile {

		private final String path;
		private final File file;
		public long timestamp;
		public String checksum;
		public String coordinate;

		protected StringAndFile(final String path, final File file) {
			this.path = path;
			this.file = file;
		}

		@Override
		public String toString() {
			return "{" + path + " - " + file + "}";
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof StringAndFile)) return false;
			StringAndFile that = (StringAndFile) o;
			return Objects.equals(this.path, that.path) && Objects.equals(this.file, that.file);
		}

		@Override
		public int hashCode() {
			return Objects.hash(path, file);
		}
	}

	public Map<String, FileObject.Version> getCachedChecksums() {
		return cachedChecksums;
	}

	/* follows symlinks */
	protected boolean exists(final File file) {
		try {
			return file.getCanonicalFile().exists();
		}
		catch (final IOException e) {
			files.log.error(e);
		}
		return false;
	}

	void queueDir(final String[] dirs, final String[] extensions) {
		final Set<String> set = new HashSet<>();
		Collections.addAll(set, extensions);
		for (final String dir : dirs)
			queueDir(dir, set);
	}

	void queueDir(final String dir, final Set<String> extensions) {
		File file = files.prefix(dir);
		if (!exists(file)) return;
		for (final String item : file.list()) {
			final String path = dir + "/" + item;
			file = files.prefix(path);
			if (item.startsWith(".")) continue;
			if (file.isDirectory()) {
				queueDir(path, extensions);
				continue;
			}
			if (!matchesExtension(extensions, item)) continue;
			if (exists(file)) queue(path, file);
		}
	}

	protected void queueIfExists(final String path) {
		final File file = files.prefix(path);
		if (file.exists()) queue(path, file);
	}

	protected void queue(final String path) {
		queue(path, files.prefix(path));
	}

	protected void queue(final String path, final File file) {
		String unversioned = FileObject.getFilename(path, true);
		// Any .old file is a backup of an executable that was left after an update.
		// These will always be considered "local-only" so we want to exclude them
		// from the consideration.
		if (unversioned.contains(".old")) return;
		if (!queue.containsKey(unversioned))
			queue.put(unversioned, new ArrayList<>());
		List<StringAndFile> list = queue.get(unversioned);
		StringAndFile entry = new StringAndFile(path, file);
		if (!list.contains(entry)) list.add(entry);
	}

	/**
	 * Handle a single component, adding conflicts if there are multiple
	 * versions.
	 *
	 * @param unversioned
	 *            the unversioned name of the component
	 */
	protected void handle(final String unversioned) {
		final List<StringAndFile> pairs = queue.get(unversioned);
		for (final StringAndFile pair : pairs) {
			addItem(pair.path);

			if (pair.file.exists()) try {
				pair.timestamp = Timestamps.getTimestamp(pair.file);
				pair.checksum = getDigest(pair.path, pair.file, pair.timestamp);
			}
			catch (final ZipException e) {
				files.log.error("Problem digesting " + pair.file);
			}
			catch (final Exception e) {
				files.log.error(e);
			}

			counter += (int) pair.file.length();
			itemDone(pair.path);
			setCount(counter, total);
		}

		if (pairs.size() == 1) {
			handle(pairs.get(0));
			return;
		}

		if (unversioned.endsWith(".jar") && handleClash(unversioned, pairs)) return;

		// there are multiple versions of the same component;
		StringAndFile pair = null;
		FileObject object = files.get(unversioned);
		if (object == null || object.isObsolete()) {
			// the component is local-only; fall back to using the newest one
			for (StringAndFile p : pairs)
				if (pair == null || (p.file.lastModified() > pair.file.lastModified()
						&& pair.path.equals(FileObject.getFilename(pair.path, true))))
					pair = p;
			final List<File> obsoletes = new ArrayList<>();
			for (StringAndFile p : pairs)
				if (p != pair)
					obsoletes.add(p.file);
			if (pair != null) addConflict(pair.path, "", false, obsoletes);
		} else {
			// let's find out whether there are obsoletes or locally-modified versions
			final List<StringAndFile> upToDates = new ArrayList<>();
			final List<StringAndFile> obsoletes = new ArrayList<>();
			final List<StringAndFile> locallyModifieds = new ArrayList<>();
			for (final StringAndFile p : pairs) {
				if (object.getCurrentVersion().getChecksum().equals(p.checksum))
					upToDates.add(p);
				else if (object.hasPreviousVersion(p.checksum))
					obsoletes.add(p);
				else
					locallyModifieds.add(p);
			}
			Comparator<StringAndFile> comparator = (a, b) -> {
				long diff = a.file.lastModified() - b.file.lastModified();
				return diff < 0 ? +1 : diff > 0 ? -1 : 0;
			};
			upToDates.sort(comparator);
			obsoletes.sort(comparator);
			locallyModifieds.sort(comparator);
			if (!upToDates.isEmpty())
				pair = pickNewest(upToDates);
			else if (!obsoletes.isEmpty())
				pair = pickNewest(obsoletes);
			else
				pair = pickNewest(locallyModifieds);
			if (!locallyModifieds.isEmpty())
				addConflict(pair.path, "locally-modified", true, convert(locallyModifieds));
			if (!obsoletes.isEmpty())
				addConflict(pair.path, "obsolete", false, convert(obsoletes));
			if (!upToDates.isEmpty())
				addConflict(pair.path, "up-to-date", false, convert(upToDates));
		}
		handle(pair);
	}

	/**
	 * Handles .jar files that are not versions of one component at all, but
	 * different artifacts whose artifactIds happen to match.
	 * <p>
	 * Telling them apart matters because the alternative on offer is deleting
	 * one -- and deleting an unrelated library is exactly the damage of
	 * <a href="https://github.com/imagej/imagej-updater/issues/120">
	 * imagej/imagej-updater#120</a>. The one the update site knows keeps the
	 * plain name; anything else is offered the groupId-prefixed name that such
	 * pairs have been kept apart by all along.
	 * </p>
	 *
	 * @param unversioned the name the files share
	 * @param pairs the files sharing it
	 * @return whether the files were different artifacts, and so handled here
	 */
	protected boolean handleClash(final String unversioned,
		final List<StringAndFile> pairs)
	{
		final Set<String> coordinates = new LinkedHashSet<>();
		for (final StringAndFile pair : pairs) {
			pair.coordinate = coordinateOf(pair.file);
			if (pair.coordinate != null) coordinates.add(pair.coordinate);
		}
		if (coordinates.size() < 2) return false;

		final FileObject object = files.get(unversioned);
		final String known = object == null ? null : object.getCoordinate();
		StringAndFile keeper = null;
		for (final StringAndFile pair : pairs) {
			if (pair.coordinate != null && pair.coordinate.equals(known)) keeper = pair;
		}
		// With no record of which artifact owns the name, the newest file keeps
		// it, as it would if these really were versions of one component.
		if (keeper == null) keeper = pickNewest(new ArrayList<>(pairs));

		final Map<String, String> renames = new LinkedHashMap<>();
		final StringBuilder message = new StringBuilder();
		message.append("Different artifacts are sharing the name ")
			.append(unversioned).append(":");
		for (final StringAndFile pair : pairs) {
			message.append("\n").append(pair.path).append(" is ")
				.append(pair.coordinate == null ? "not a Maven artifact"
					: pair.coordinate);
			if (pair == keeper || pair.coordinate == null) continue;
			renames.put(pair.path, FileObject.disambiguate(pair.path,
				pair.coordinate));
		}
		message.append("\nOnly one of them can be ").append(unversioned).append(".");
		addClashConflict(keeper.path, message.toString(), renames);

		handle(keeper);
		return true;
	}

	private String coordinateOf(final File file) {
		try {
			return POMParser.readCoordinate(file);
		}
		catch (final IOException e) {
			files.log.error("Could not read the coordinate of " + file, e);
			return null;
		}
	}

	/**
	 * Reports clashing artifacts, offering to give all but one of them the
	 * groupId-prefixed name.
	 *
	 * @param filename the file the conflict is reported against
	 * @param message what the clash is
	 * @param renames the files to rename, mapped to their new names
	 */
	protected void addClashConflict(final String filename, final String message,
		final Map<String, String> renames)
	{
		final Resolution ignore = new Resolution("Ignore for now") {
			@Override
			public void resolve() {
				removeConflict(filename);
			}
		};
		final Resolution rename = new Resolution("Rename to " +
			UpdaterUtil.join(", ", renames.values()))
		{
			@Override
			public void resolve() {
				for (final Map.Entry<String, String> entry : renames.entrySet()) {
					if (!files.prefix(entry.getKey()).renameTo(files.prefix(entry
						.getValue())))
					{
						throw new RuntimeException("Could not rename '" +
							entry.getKey() + "' to '" + entry.getValue() + "'");
					}
				}
				new Checksummer(files, null).updateFromLocal(new ArrayList<>(renames
					.values()));
				removeConflict(filename);
			}
		};
		files.conflicts.add(new Conflict(Severity.CRITICAL_ERROR, filename,
			message, rename, ignore));
	}

	protected static StringAndFile pickNewest(final List<StringAndFile> list) {
		int index = 0;
		if (list.size() > 1) {
			final String filename = list.get(0).path;
			if (filename.equals(FileObject.getFilename(filename, true)))
				index++;
		}

		final StringAndFile result = list.get(index);
		list.remove(index);
		return result;
	}

	protected static List<File> convert(final List<StringAndFile> pairs) {
		final List<File> result = new ArrayList<>();
		for (final StringAndFile pair : pairs)
			result.add(pair.file);
		return result;
	}

	protected void addConflict(final String filename, String adjective, boolean isCritical, final List<File> toDelete) {
		if (!adjective.isEmpty() && !adjective.endsWith(" "))
			adjective += " ";
		String conflictMessage = "Multiple " + adjective + "versions of " + filename + " exist: " + UpdaterUtil.join(", ", toDelete);
		Resolution ignore = new Resolution("Ignore for now") {
			@Override
			public void resolve() {
				removeConflict(filename);
			}
		};
		Resolution delete = new Resolution("Delete!") {
			@Override
			public void resolve() {
				for (final File file : toDelete) {
					if (!file.delete()) {
						final String prefix =
							files.prefix("").getAbsolutePath() + File.separator;
						final String absolute = file.getAbsolutePath();
						if (absolute.startsWith(prefix)) try {
							FileObject.touch(files.prefixUpdate(absolute.substring(prefix
								.length())));
						}
						catch (IOException e) {
							files.log.error(e);
							file.deleteOnExit();
						}
						else {
							file.deleteOnExit();
						}
					}
				}
				removeConflict(filename);
			}
		};
		files.conflicts.add(new Conflict(isCritical ? Severity.CRITICAL_ERROR
			: Severity.ERROR, filename, conflictMessage, ignore, delete));
	}

	protected void removeConflict(final String filename) {
		if (files.conflicts == null)
			return;
		Iterator<Conflict> iterator = files.conflicts.iterator();
		while (iterator.hasNext()) {
			Conflict conflict = iterator.next();
			if (conflict.filename.equals(filename)) {
				iterator.remove();
				return;
			}
		}
	}

	protected void handle(final StringAndFile pair) {
		if (pair.checksum != null) {
			FileObject object = files.get(pair.path);
			if (object == null) {
				object =
					new FileObject(null, pair.path, pair.file.length(), pair.checksum, pair.timestamp,
						Status.LOCAL_ONLY);
				object.setLocalFilename(pair.path);
				object.setLocalChecksum(pair.checksum);
				object.setLocalTimestamp(pair.timestamp);
				if ((!isWindows && UpdaterUtil.canExecute(pair.file)) || pair.path.endsWith(".exe"))
					object.setExecutable(true);
				guessPlatform(object);
				files.add(object);
			}
			else {
				final FileObject.Version obsoletes =
						cachedChecksums.get(":" + pair.checksum);
				if (!object.hasPreviousVersion(pair.checksum)) {
					if (obsoletes != null) {
						for (final String obsolete : obsoletes.getChecksum().split(":")) {
							if (object.hasPreviousVersion(obsolete)) {
								pair.checksum = obsolete;
								break;
							}
						}
					}
				} else if (object.getCurrentVersion() != null && obsoletes != null
						&& (":" + obsoletes.getChecksum() + ":").contains(":" + object.getCurrentVersion().getChecksum() + ":")) {
					// if the recorded checksum is an obsolete equivalent of the current one, use the obsolete one
					pair.checksum = object.getCurrentVersion().getChecksum();
				}
				object.setLocalVersion(pair.path, pair.checksum, pair.timestamp);
				if (object.getStatus() == Status.OBSOLETE_UNINSTALLED) object
					.setStatus(Status.OBSOLETE);
			}
			if (pair.path.endsWith((".jar"))) try {
				POMParser.fillMetadataFromJar(object, pair.file);
			} catch (Exception e) {
				files.log.error("Could not read pom.xml from " + pair.path);
			}
		}
		else {
			final FileObject object = files.get(pair.path);
			if (object != null) {
				switch (object.getStatus()) {
					case OBSOLETE:
					case OBSOLETE_MODIFIED:
						object.setStatus(Status.OBSOLETE_UNINSTALLED);
						break;
					case INSTALLED:
					case MODIFIED:
					case UPDATEABLE:
						object.setStatus(Status.NOT_INSTALLED);
						break;
					case LOCAL_ONLY:
						files.remove(pair.path);
						break;
					case NEW:
					case NOT_INSTALLED:
					case OBSOLETE_UNINSTALLED:
						// leave as-is
						break;
					default:
						throw new RuntimeException("Unhandled status!");
				}
			}
		}
	}

	protected void handleQueue() {
		total = 0;
		for (final String unversioned : queue.keySet())
			for (final StringAndFile pair : queue.get(unversioned))
				total += (int) pair.file.length();
		counter = 0;
		for (final String unversioned : queue.keySet())
			handle(unversioned);
		done();
		writeCachedChecksums();
	}

	public void updateFromLocal(final List<String> files) {
		queue = new LinkedHashMap<>();
		for (final String file : files)
			queue(file);
		handleQueue();
	}

	protected boolean guessPlatform(final FileObject file) {
		// Look for platform names as subdirectories of jars/ and lib/
		String platform;
		if (file.isExecutable()) {
			platform = Platforms.platformForLauncher(file.getFilename());
			if (platform == null) return false;
		}
		else {
			if (file.getFilename().startsWith("jars/")) {
				platform = file.getFilename().substring(5);
			}
			else if (file.getFilename().startsWith("lib/")) {
				platform = file.getFilename().substring(4);
			}
			else if (file.getFilename().startsWith("mm/")) {
				platform = file.getFilename().substring(3);
			}
			else return false;

			final int slash = platform.indexOf('/');
			if (slash < 0) return false;
			platform = platform.substring(0, slash);
		}

		if (platform.equals("linux")) platform = "linux32";

		for (final String valid : Platforms.known())
			if (platform.equals(valid)) {
				file.addPlatform(platform);
				return true;
			}
		return false;
	}

	/**
	 * The directories whose contents the updater manages, paired with the file
	 * extensions it recognizes in each. An extension list of <code>{ "" }</code>
	 * means every file in that directory counts.
	 * <p>
	 * Note that this table governs the <em>discovery</em> of files not yet known
	 * to the {@link FilesCollection} only. Files already recorded in an index are
	 * queued regardless of where they live; see {@link #initializeQueue()}.
	 * </p>
	 * <p>
	 * Entries here are cheap to add and invisible to remove, so the table only
	 * shrinks when somebody looks. Audit it against a real installation from time
	 * to time -- but note that "absent from my installation" and "safe to remove"
	 * are different questions; see the note on <code>Contents</code> below.
	 * </p>
	 */
	public static final String[][] directories = {
		{ "jars" }, { ".jar", ".class" },
		// NB: Deliberately an allow list, not a catch-all: config/jaunch/*.cfg is
		// local machine state (the launcher's chosen JVM, the Python directory,
		// and -- once channels land -- the installation's current channel). It
		// must never become an updatable file, because an update site shipping
		// one would overwrite that state for every user of the site.
		{ "config" }, { ".toml", ".class", ".py", ".txt", ".yml" },
		{ "plugins" }, { ".jar", ".class", ".txt", ".ijm", ".py", ".rb", ".clj", ".js", ".bsh", ".groovy", ".gvy" },
		{ "scripts" }, { ".m",                     ".ijm", ".py", ".rb", ".clj", ".js", ".bsh", ".groovy", ".gvy" },
		{ "macros" }, { ".txt", ".ijm", ".png" },
		// Note: models/ is absent from a stock installation, but two hosted sites
		// ship model weights there -- GutAnalysisToolbox and MultiCellPlugins --
		// so it stays a managed directory.
		{ "models" }, { "" },
		{ "luts" }, { ".lut" },
		{ "images" }, { ".png", ".tif", ".txt", ".ico" },
		{ "lib" }, { "" },
		{ "licenses" }, { "" }
	};

	protected static final Map<String, Set<String>> extensions;

	static {
		extensions = new HashMap<>();
		for (int i = 0; i < directories.length; i += 2) {
			final Set<String> set = new HashSet<>(Arrays.asList(directories[i + 1]));
			for (final String dir : directories[i])
				extensions.put(dir, set);
		}
	}

	/**
	 * Whether a file name matches a directory's recognized extensions. An
	 * extension set containing the empty string matches every file.
	 */
	private static boolean matchesExtension(final Set<String> extensions,
		final String filename)
	{
		if (extensions.contains("")) return true;
		final int dot = filename.lastIndexOf('.');
		return dot >= 0 && extensions.contains(filename.substring(dot));
	}

	/**
	 * Whether the updater would discover the given path while scanning the
	 * application directory -- i.e. whether it lies in a managed directory with a
	 * recognized extension, or is a launcher.
	 */
	public boolean isCandidate(String path) {
		path = path.replace('\\', '/'); // Microsoft time toll
		if (Platforms.isLauncher(path)) return true;
		final int slash = path.indexOf('/');
		if (slash < 0) return false;
		final Set<String> exts = extensions.get(path.substring(0, slash));
		if (exts == null) return false;
		return matchesExtension(exts, path.substring(path.lastIndexOf('/') + 1));
	}

	protected void initializeQueue() {
		queue = new LinkedHashMap<>();

		queueIfExists("README.md");
		queueIfExists("WELCOME.md");
		queueIfExists("fiji");
		queueIfExists("fiji.bat");
		queueIfExists("ImageJ.sh"); // deprecated
		for (final String launcher : Platforms.launchers())
			queueIfExists(launcher);

		// Queue any macOS .app folders.
		File appDir = files.getAppRoot();
		if (appDir != null) {
			Set<String> allExtensions = Collections.singleton("");
			for (File file : appDir.listFiles()) {
				if (file.isDirectory() && file.getName().endsWith(".app")) {
					queueDir(file.getName(), allExtensions);
				}
			}
		}

		for (int i = 0; i < directories.length; i += 2)
			queueDir(directories[i], directories[i + 1]);

		for (final FileObject file : files)
			if (!queue.containsKey(file.getFilename(true)))
				queue(file.getFilename());
	}

	public void updateFromLocal() {
		initializeQueue();
		handleQueue();
	}

	protected void readCachedChecksums() {
		cachedChecksums = new TreeMap<>();
		final File file = files.prefix(".checksums");
		if (!file.exists()) return;
		try {
			final BufferedReader reader = new BufferedReader(new FileReader(file));
			String line;
			while ((line = reader.readLine()) != null)
				try {
					final int space = line.indexOf(' ');
					if (space < 0) continue;
					final String checksum = line.substring(0, space);
					final int space2 = line.indexOf(' ', space + 1);
					if (space2 < 0) continue;
					final long timestamp =
						Long.parseLong(line.substring(space + 1, space2));
					final String filename = line.substring(space2 + 1);
					cachedChecksums.put(filename, new FileObject.Version(checksum,
						timestamp));
				}
				catch (final NumberFormatException e) {
					/* ignore line */
				}
			reader.close();
		}
		catch (final IOException e) {
			// ignore
		}
	}

	protected void writeCachedChecksums() {
		if (cachedChecksums == null) return;
		final File file = files.prefix(".checksums");
		// file.canWrite() not applicable, as the file need not exist
		try {
			final Writer writer = new FileWriter(file);
			for (final String filename : cachedChecksums.keySet())
				if (filename.startsWith(":") || files.prefix(filename).exists()) {
					final FileObject.Version version = cachedChecksums.get(filename);
					writer.write(version.getChecksum() + " " + version.getTimestamp() + " " +
						filename + "\n");
				}
			writer.close();
		}
		catch (final IOException e) {
			// ignore
		}
	}

	protected String getDigest(final String path, final File file,
		final long timestamp) throws IOException, NoSuchAlgorithmException
	{
		if (cachedChecksums == null) readCachedChecksums();
		FileObject.Version version = cachedChecksums.get(path);
		if (version == null || timestamp != version.getTimestamp()) {
			final String checksum = path.equals(AppLayout.LEGACY_UPDATER_JAR) ?
				UpdaterUtil.getJarDigest(file, false, false, false) :
				UpdaterUtil.getDigest(path, file);
			version = new FileObject.Version(checksum, timestamp);
			cachedChecksums.put(path, version);
		}
		if (!cachedChecksums.containsKey(":" + version.getChecksum())) {
			final List<String> obsoletes = UpdaterUtil.getObsoleteDigests(path, file);
			if (obsoletes != null) {
				final StringBuilder builder = new StringBuilder();
				for (final String obsolete : obsoletes) {
					if (builder.length() > 0) builder.append(':');
					builder.append(obsolete);
				}
				cachedChecksums.put(":" + version.getChecksum(), new FileObject.Version(
					builder.toString(), timestamp));
			}
		}
		return version.getChecksum();
	}
}
