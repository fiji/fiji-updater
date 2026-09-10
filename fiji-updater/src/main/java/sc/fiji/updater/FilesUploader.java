/*
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2009 - 2026 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
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

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sc.fiji.updater.Conflicts.Conflict;
import sc.fiji.updater.FileObject.Action;
import sc.fiji.updater.FileObject.Status;
import sc.fiji.updater.util.ChannelManifest;
import sc.fiji.updater.util.ChannelState;
import sc.fiji.updater.util.Channels;
import sc.fiji.updater.util.Progress;
import sc.fiji.updater.util.StderrProgress;
import sc.fiji.updater.util.UpdaterUserInterface;
import sc.fiji.updater.util.UpdateCanceledException;
import sc.fiji.updater.util.UpdaterUtil;

import org.scijava.Context;
import org.scijava.log.LogService;

/**
 * This class is responsible for writing updates to server, upon given the
 * updated file records.
 * <p>
 * Note: Files are uploaded differently:
 * </p>
 * <ul>
 * <li>Local-only files &amp; new file versions will have files AND details uploaded
 * </li>
 * <li>Uninstalled &amp; up-to-date files will ONLY have their details uploaded
 * (i.e.: XML file)</li>
 * </ul>
 * 
 * @author Johannes Schindelin
 */
public class FilesUploader {

	private final FilesCollection files;
	private final Uploader uploader;

	private final String siteName;
	private final UpdateSite site;
	private List<Uploadable> uploadables;
	/**
	 * Whether this uploader exists only to bring a brand-new update site into
	 * being, rather than to publish content to an existing one.
	 */
	private boolean initialUpload;
	private boolean loggedIn;

	private static UploaderService createUploaderService() {
		setClassLoaderIfNecessary();
		final Context context = new Context(UploaderService.class);
		return context.getService(UploaderService.class);
	}

	/**
	 * Sets the context class loader if necessary.
	 * <p>
	 * If the current class cannot be found by the current Thread's context
	 * class loader, we should tell the Thread about the class loader that
	 * loaded this class.
	 * </p>
	 */
	private static void setClassLoaderIfNecessary() {
		ClassLoader thisLoader = FilesUploader.class.getClassLoader();
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		for (; loader != null; loader = loader.getParent()) {
			if (thisLoader == loader) return;
		}
		Thread.currentThread().setContextClassLoader(thisLoader);
	}

	public FilesUploader(UploaderService uploaderService,
			final FilesCollection files, final String updateSite,
			final Progress progress) {
		if (uploaderService == null) uploaderService = createUploaderService();
		this.files = files;
		siteName = updateSite;
		site = files.getUpdateSite(updateSite, false);
		final String protocol = site.getUploadProtocol();
		uploader = uploaderService.installUploader(protocol, files,
				progress == null ? new StderrProgress() : progress);
		if (uploader == null) {
			throw new IllegalArgumentException(
					"No uploader found for protocol " + protocol);
		}
	}

	public boolean hasUploader() {
		return uploader != null;
	}

	/**
	 * Gets the channel this upload publishes to, or null for the base channel.
	 * <p>
	 * This is not a choice, and deliberately so. The index being uploaded is
	 * generated from the local {@link FilesCollection} -- see the XMLFileWriter
	 * call in {@code updateUploadables} -- so it describes the state this
	 * installation actually resolved. Publishing it to any other channel would
	 * publish an index that was never true of that channel: a maintainer running
	 * A.punctulata would be asserting, of B.floridae, a set of versions they
	 * have never had installed and cannot have tested.
	 * </p>
	 * <p>
	 * The existing advice for maintainers is to upload from a Fiji that is fully
	 * up to date, with as few update sites enabled as possible, so that the
	 * generated index reflects something they actually ran. Channels extend that
	 * rather than complicate it: be up to date <em>on the channel you are
	 * publishing to</em>. Wanting to publish elsewhere is wanting to skip the
	 * testing, and the way to publish for another channel is to run one.
	 * </p>
	 *
	 * <p>
	 * Note that this reads the channel the installation <em>declares</em>, not
	 * any override given for the run. An override changes which remote index is
	 * read; it does not change which versions are installed here, and those are
	 * what the uploaded index describes. To publish for another channel, move the
	 * installation to it and update first.
	 * </p>
	 *
	 * @throws IllegalStateException if the channel cannot be determined.
	 */
	public String getUploadChannel() {
		if (initialUpload) {
			// Bringing a site into being writes an empty index, which asserts
			// nothing about any channel and so is safe to serve from the site
			// root -- and it has to go there. A site serving only a channel index
			// does not look empty to a client on the base channel: every candidate
			// fails, so the site is reported unreadable and treated as deleted.
			// See ChannelResolutionTest.testChannelOnlySiteIsUnreadableFromBase.
			//
			// Nothing more is "seeded" than that. The channel manifest is created
			// by the first upload that publishes to a channel, like any other, and
			// a site whose uploads all come from the base channel never grows one.
			//
			// NB: This case is separate only because initialUploader fabricates a
			// FilesCollection with no application root, so there is no launcher
			// configuration to read a channel from even when the caller has one.
			return null;
		}
		final ChannelState state = files.getDeclaredChannelState();
		if (!state.isKnown()) {
			// While no channel exists there is only one place to publish, so an
			// undeterminable channel costs nothing. Once channels exist, guessing
			// would mean publishing an index into a channel it was never true of.
			if (!Channels.anyExist(files.getChannels())) return null;
			throw new IllegalStateException("Cannot determine which update " +
				"channel this installation follows, so there is no way to know " +
				"which channel to publish to.");
		}
		return state.channel();
	}

	/**
	 * The index's path relative to the site root: what gets uploaded, and what
	 * the final rename targets.
	 */
	private String indexPath() {
		return UpdateSite.getIndexPath(getUploadChannel());
	}

	/**
	 * Whether this upload would publish for a channel the site does not yet
	 * serve, making it the site's first release for that edition.
	 * <p>
	 * Worth knowing because adoption is otherwise silent and has consequences
	 * for other people. Nothing breaks -- the index at the site root is untouched
	 * and clients on the base channel keep reading it -- but it stops being
	 * updated, so those users are quietly frozen at whatever was published last.
	 * </p>
	 */
	public boolean isFirstUploadToChannel() {
		final String channel = getUploadChannel();
		if (channel == null) return false;
		return indexLastModified(channel) <= 0;
	}

	/**
	 * When the given channel's index was last published, in milliseconds since
	 * the epoch, or a non-positive value if there is no such index.
	 */
	private long indexLastModified(final String channel) {
		try {
			return UpdaterUtil.getLastModified(
				new URL(site.getURL() + UpdateSite.getIndexPath(channel)));
		}
		catch (final MalformedURLException e) {
			files.log.debug(e);
			return -1;
		}
	}

	/**
	 * Warns that this upload adopts a channel the site has not served before, and
	 * asks whether to go ahead.
	 *
	 * @return whether to proceed.
	 */
	private boolean confirmFirstUploadToChannel() {
		final String channel = getUploadChannel();
		final long baseModified = indexLastModified(null);
		final String baseNote = baseModified > 0
			? "Users on " + ChannelState.BASE_CHANNEL_NAME +
				" will continue to see the release from " +
				new SimpleDateFormat("d MMMM yyyy").format(new Date(baseModified)) + "."
			: "Users on " + ChannelState.BASE_CHANNEL_NAME +
				" will continue to see whatever was published there last.";

		final String message = "This is the first upload to " + channel +
			" for the '" + siteName + "' update site.\n\n" + baseNote +
			"\nThat index will no longer be updated by uploads from this " +
			"installation, which follows " + channel + ".\n\n" +
			"Upload to " + channel + "?";

		if (UpdaterUserInterface.get().isBatchMode()) {
			// Nothing can be asked, and nothing is destroyed by proceeding, so say
			// so clearly and carry on rather than breaking an automated release.
			UpdaterUserInterface.get().log(message);
			return true;
		}
		return UpdaterUserInterface.get().promptYesNo(message,
			"First upload to " + channel);
	}

	public FilesCollection getFilesCollection() {
		return files;
	}

	public String getSiteName() {
		return siteName;
	}

	public String getDefaultUsername() {
		String host = site.getHost();
		if (host.startsWith("sftp:")) host = host.substring(5);
		final int at = host.indexOf('@');
		if (at > 0) return host.substring(0, at);
		final String name = UpdaterUserInterface.get().getPref(UpdaterUtil.PREFS_USER);
		if (name == null) return "";
		return name;
	}

	public String getUploadHost() {
		String host = site.getHost();
		if (uploader != null) {
			final String protocol = uploader.getProtocol();
			if (protocol != null && host.startsWith(protocol + ":")) {
				host = host.substring(protocol.length() + 1);
			}
		}
		return host.substring(host.indexOf('@') + 1);
	}

	public String getUploadDirectory() {
		return site.getUploadDirectory();
	}

	protected class DbXmlFile implements Uploadable {

		public byte[] bytes;

		@Override
		public String getFilename() {
			return indexPath() + ".lock";
		}

		@Override
		public String getPermissions() {
			return "C0444";
		}

		@Override
		public long getFilesize() {
			return bytes.length;
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public String toString() {
			return indexPath();
		}
	}

	/**
	 * The site's list of channels, published beside its index.
	 * <p>
	 * Written only when publishing to a channel. A base-channel upload leaves it
	 * alone, so a site that has never used a channel never grows one -- which is
	 * what makes its presence meaningful to clients.
	 * </p>
	 */
	protected class ChannelManifestFile implements Uploadable {

		private final byte[] bytes;

		ChannelManifestFile(final byte[] bytes) {
			this.bytes = bytes;
		}

		@Override
		public String getFilename() {
			return ChannelManifest.FILENAME + ".lock";
		}

		@Override
		public String getPermissions() {
			return "C0444";
		}

		@Override
		public long getFilesize() {
			return bytes.length;
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public String toString() {
			return ChannelManifest.FILENAME;
		}
	}

	public void upload(final Progress progress) throws Exception {
		if (uploader == null) throw new RuntimeException("No uploader set for " +
			site.getHost());
		if (!loggedIn) throw new RuntimeException("Not logged in!");
		final Iterable<Conflict> conflicts = new Conflicts(files).getConflicts(true);
		if (Conflicts.needsFeedback(conflicts)) {
			throw new RuntimeException("Unresolved upload conflicts!\n\n"
				+ UpdaterUtil.join("\n", conflicts));
		}
		if (isFirstUploadToChannel() && !confirmFirstUploadToChannel()) {
			throw new UpdateCanceledException();
		}

		uploader.addProgress(progress);
		uploader.addProgress(new VerifyTimestamp());

		uploadables = new ArrayList<>();
		final List<String> locks = new ArrayList<>();
		uploadables.add(new DbXmlFile());

		// Announce the channel beside the index, so clients that do not find
		// their own channel here can learn what this site does serve instead of
		// walking the whole list of channels ever minted.
		final String channel = getUploadChannel();
		if (channel != null) {
			final ChannelManifest manifest =
				ChannelManifest.read(site.getURL()).with(channel);
			uploadables.add(new ChannelManifestFile(manifest.toByteArray()));
			locks.add(ChannelManifest.FILENAME);
		}

		/*
		 * Stage new versions of otherwise unchanged files for upload (Bio-Formats, I
		 * am looking at you!).
		 */
		for (final FileObject file : files.forUpdateSite(siteName)) {
			if (!file.actionSpecified() && file.getStatus() == Status.INSTALLED &&
				file.metadataChanged &&
				file.localFilename != null && !file.localFilename.equals(file.filename))
			{
				file.addPreviousVersion(file.current.checksum, file.current.timestamp,
					file.filename, 0);
				file.setAction(files, Action.UPLOAD);
			}
		}

		for (final FileObject file : files.toUpload(siteName)) {
			// remove obsolete/invalid dependencies
			for (Iterator<Dependency> iter = file.getDependencies().iterator(); iter.hasNext(); ) {
				final String filename = iter.next().filename;
				final FileObject other = files.get(filename);
				if (other == null || other.isObsolete()) {
					files.log.warn("Removed obsolete dependency " + filename + " of " + file.filename);
					iter.remove();
				}
			}
			uploadables.add(new UploadableFile(files, file));
		}

		// must be last lock
		locks.add(indexPath());

		// verify that the files have not changed in the meantime
		final long[] timestamps = new long[uploadables.size()];
		int counter = 0;
		for (final Uploadable uploadable : uploadables) {
			if (uploadable instanceof UploadableFile) {
				final UploadableFile file = (UploadableFile) uploadable;
				timestamps[counter] = UpdaterUtil.getTimestamp(file.source);
			}
			verifyUnchanged(uploadable, true);
			counter++;
		}

		uploader.upload(uploadables, locks);

		// verify that the files have not changed in the meantime
		counter = 0;
		for (final Uploadable uploadable : uploadables) {
			if (uploadable instanceof UploadableFile) {
				final UploadableFile file = (UploadableFile) uploadable;
				if (timestamps[counter] != UpdaterUtil.getTimestamp(file.source)) throw new RuntimeException(
					"Timestamp of " + file.getFilename() +
						"changed since being checksummed (was " + timestamps[counter] +
						" but is " + UpdaterUtil.getTimestamp(file.source) + "!)");
			}
			counter++;
		}

		site.setLastModified(getCurrentLastModified());
	}

	protected void verifyUnchanged(final Uploadable file,
		final boolean checkTimestamp)
	{
		if (!(file instanceof UploadableFile)) return;
		final UploadableFile uploadable = (UploadableFile) file;
		final long size = uploadable.source.length();
		if (uploadable.filesize != size) throw new RuntimeException(
			"File size of " + uploadable.file.filename +
				" changed since being checksummed (was " + uploadable.filesize +
				" but is " + size + ")!");
		if (checkTimestamp) {
			final long stored =
				uploadable.file.getStatus() == FileObject.Status.LOCAL_ONLY
					? uploadable.file.current.timestamp : uploadable.file.localTimestamp;
			if (stored != UpdaterUtil.getTimestamp(uploadable.source)) throw new RuntimeException(
				"Timestamp of " + uploadable.file.filename +
					" changed since being checksummed (was " + stored + " but is " +
					UpdaterUtil.getTimestamp(uploadable.source) + ")!");
		}
	}

	protected void updateUploadTimestamp(final long timestamp) throws Exception {
		for (final Uploadable f : uploadables) {
			if (!(f instanceof UploadableFile)) continue;
			final UploadableFile uploadable = (UploadableFile) f;
			final FileObject file = uploadable.file;
			if (file == null) continue;
			file.filesize = uploadable.filesize = uploadable.source.length();
			file.localTimestamp = timestamp;
			uploadable.filename = file.filename + "-" + timestamp;
			if (file.getStatus() == FileObject.Status.LOCAL_ONLY) {
				file.setStatus(FileObject.Status.INSTALLED);
				file.current.timestamp = timestamp;
			}
		}

		final XMLFileWriter writer =
			new XMLFileWriter(files.clone(files.forUpdateSite(siteName, true)));
		if (!files.isEmpty()) writer.validate(false);
		((DbXmlFile) uploadables.get(0)).bytes =
			writer.toCompressedByteArray(false);

		uploader.calculateTotalSize(uploadables);
	}

	/*
	 * This class serves two purposes:
	 *
	 * - after locking, it ensures that the timestamp of db.xml.gz is the
	 *   same as when it was last downloaded, to prevent race-conditions
	 *
	 * - it takes the timestamp of the lock file and updates the timestamps
	 *   of all files to be uploaded, so that local time skews do not
	 *   harm
	 */
	protected class VerifyTimestamp implements Progress {

		@Override
		public void addItem(final Object item) {
			if (item != uploadables.get(0)) return;
			verifyTimestamp();
		}

		@Override
		public void setTitle(final String string) {
			try {
				updateUploadTimestamp(uploader.getTimestamp());
			}
			catch (final Exception e) {
				files.log.error(e);
				throw new RuntimeException("Could not update "
					+ "the timestamps in db.xml.gz");
			}
		}

		@Override
		public void itemDone(final Object item) {
			if (item instanceof UploadableFile) verifyUnchanged(
				(UploadableFile) item, false);
		}

		@Override
		public void setCount(final int count, final int total) {}

		@Override
		public void setItemCount(final int count, final int total) {}

		@Override
		public void done() {}
	}

	protected long getCurrentLastModified() {
		try {
			URLConnection connection;
			try {
				connection = UpdaterUtil.openConnection(new URL(site.getIndexURL()));
			}
			catch (final FileNotFoundException e) {
				files.log.error(e);
				Thread.sleep(500);
				connection = UpdaterUtil.openConnection(new URL(site.getIndexURL()));
			}
			connection.setUseCaches(false);
			final long lastModified = connection.getLastModified();
			connection.getInputStream().close();
			UpdaterUserInterface.get().debug(
				"got last modified " + lastModified + " = timestamp " +
					UpdaterUtil.timestamp(lastModified));
			return lastModified;
		}
		catch (final Exception e) {
			UpdaterUserInterface.get().debug(e.getMessage());
			if (files.isEmpty()) return -1; // assume initial upload
			if (e instanceof FileNotFoundException) {
				files.log.debug(e);
			} else {
				files.log.error(e);
			}
			return 0;
		}
	}

	protected void verifyTimestamp() {
		if (site.getTimestamp() == 0) return;
		final long lastModified = getCurrentLastModified();
		if (!site.isLastModified(lastModified)) throw new RuntimeException(
			"db.xml.gz was " + "changed in the meantime (was " + site.getTimestamp() +
				" but now is " + UpdaterUtil.timestamp(lastModified) + ")");
	}

	public boolean login() {
		if (loggedIn) return loggedIn;
		loggedIn = uploader.login(this);
		return loggedIn;
	}

	public void logout() {
		if (uploader != null)
			uploader.logout();
	}

	public static FilesUploader initialUploader(
		final UploaderService uploaderService, final String url,
		final String sshHost, final String uploadDirectory, final Progress progress)
	{
		final String updateSiteName = "Dummy";
		final FilesCollection files = new FilesCollection(null);
		files.addUpdateSite(updateSiteName, url, sshHost, uploadDirectory, Long
			.parseLong(UpdaterUtil.timestamp(-1)));
		final FilesUploader uploader =
			new FilesUploader(uploaderService, files, updateSiteName, progress);
		uploader.initialUpload = true;
		return uploader;
	}

	public LogService getLog() {
		return files.log;
	}
}
