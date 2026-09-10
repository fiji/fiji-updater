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

import sc.fiji.updater.util.AppLayout;
import sc.fiji.updater.util.ChannelManifest;
import sc.fiji.updater.util.ChannelState;
import sc.fiji.updater.util.StderrProgress;
import sc.fiji.updater.util.UpdaterUtil;
import org.apache.commons.lang.NotImplementedException;
import org.junit.After;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static sc.fiji.updater.UpdaterTestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

/**
 * An abstract base class for testing uploader backends.
 * 
 * See {@link LocalhostUploaderTest} for an example how to use it.
 * 
 * @author Johannes Schindelin
 */
public abstract class AbstractUploaderTestBase {
	protected final String propertyPrefix, updateSiteName;
	protected String url;
	protected FilesCollection files;

	public AbstractUploaderTestBase(final String propertyPrefix) {
		this.propertyPrefix = propertyPrefix;
		updateSiteName = propertyPrefix + "-test";
	}

	@After
	public void after() {
		if (files != null) {
			files.removeUpdateSite(updateSiteName);
			cleanup(files);
		}
	}

	/**
	 * Verifies that this transport can publish an index into a channel
	 * subdirectory of an update site.
	 * <p>
	 * Channels put the index at {@code <site>/<channel>/db.xml.gz}, so both the
	 * upload target and the lock rename that publishes it acquire a directory
	 * component. Every transport already creates intermediate directories --
	 * FileUploader calls mkdirs, WebDAVUploader does a recursive MKCOL,
	 * SFTPOperations calls mkParentDirs and SSHFileUploader emits scp
	 * D-directives -- so nothing here should need transport changes. This test
	 * exists so that remains true rather than merely having been true when
	 * somebody read the code.
	 * </p>
	 * <p>
	 * Also asserts that the base index is left alone. A channel is an additional
	 * index alongside the one at the site root, not a replacement for it: sites
	 * serve the base index to every client that has not adopted the channel, and
	 * clobbering it would cut those clients off.
	 * </p>
	 */
	public void testChannelUpload(final Deleter deleter, final String host,
		final String uploadDirectory, final String channel) throws Exception
	{
		getURL();
		files = initialize();

		final File ijRoot = files.prefix("");
		CommandLine.main(ijRoot, -1, "add-update-site",
				updateSiteName, url, host, uploadDirectory);

		if (!isUpdateSiteEmpty()) {
			assertTrue(deleter.login());
			deleter.delete(UpdaterUtil.XML_COMPRESSED);
			deleter.delete(channel + "/");
			deleter.delete("plugins/");
			deleter.logout();
		}

		// Guard against a vacuous test: nothing is published yet, so the probe
		// must be able to say so.
		assertFalse("probe reports a channel index before anything was published",
			indexExists(channel));

		// Publish once to the base channel, so there is something to leave alone.
		final String basePath = "plugins/Base_Only.bsh";
		writeFile(new File(ijRoot, basePath), "print(\"base\");");
		CommandLine.main(ijRoot, -1, "upload", "--update-site", updateSiteName,
			basePath);
		assertTrue(indexExists(null));

		// Now publish to the channel.
		final String channelPath = "plugins/Channel_Only.bsh";
		writeFile(new File(ijRoot, channelPath), "print(\"channel\");");

		// Declare the installation's channel, the way the launcher would. The
		// upload target follows from this rather than being chosen: the index is
		// generated from local state, so it can only honestly be published to the
		// channel that state came from.
		declareChannel(ijRoot, channel);

		final FilesCollection published = new FilesCollection(ijRoot);
		published.read();
		published.downloadIndexAndChecksum(new StderrProgress());
		published.get(channelPath).stageForUpload(published, updateSiteName);

		final FilesUploader uploader =
			new FilesUploader(null, published, updateSiteName, new StderrProgress());
		assertEquals(channel, uploader.getUploadChannel());
		assertTrue(uploader.login());
		uploader.upload(new StderrProgress());

		// The nested index exists, and the lock file was renamed away.
		assertTrue("channel index should have been published",
			indexExists(channel));
		assertFalse("lock file should have been renamed into place",
			exists(UpdateSite.getIndexPath(channel) + ".lock"));

		// And the base index is still there for everyone who has not adopted it.
		assertTrue("base index must survive a channel upload", indexExists(null));

		// A channel nobody published to is still absent, so the assertions above
		// are about this channel rather than about any path resolving.
		assertFalse(indexExists("Z.mays"));

		// The site now announces which channel it serves, so a client that does
		// not find its own here can learn what this site does offer.
		final ChannelManifest manifest = ChannelManifest.read(url);
		assertTrue("the site should announce its channels", manifest.isPresent());
		assertTrue(channel + " should be listed", manifest.carries(channel));
		assertFalse(manifest.carries("Z.mays"));
	}

	/** Writes a launcher configuration declaring the installation's channel. */
	protected static void declareChannel(final File ijRoot, final String channel)
		throws IOException
	{
		final File dir = new File(ijRoot, AppLayout.CONFIG_DIRECTORY);
		assertTrue(dir.exists() || dir.mkdirs());
		final String contents = ChannelState.CHANNEL_KEY + "=" + channel + "\n";
		java.nio.file.Files.write(new File(dir, "fiji.cfg").toPath(),
			contents.getBytes("UTF-8"));
	}

	/** Whether the index for the given channel is readable on the site. */
	protected boolean indexExists(final String channel) {
		return exists(UpdateSite.getIndexPath(channel));
	}

	/** Whether the given site-relative path is readable. */
	protected boolean exists(final String path) {
		try {
			return UpdaterUtil.getLastModified(new URL(url + path)) != -1;
		}
		catch (final MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	public void test(final Deleter deleter, final String host, final String uploadDirectory) throws Exception {
		getURL();
		files = initialize();

		File ijRoot = files.prefix("");
		CommandLine.main(ijRoot, -1, "add-update-site",
				updateSiteName, url, host, uploadDirectory);

		if (!isUpdateSiteEmpty()) {
			assertTrue(deleter.login());
			deleter.delete(UpdaterUtil.XML_COMPRESSED);
			deleter.delete("plugins/");
			assertTrue(deleter.isDeleted(UpdaterUtil.XML_COMPRESSED));
			assertTrue(deleter.isDeleted("plugins/"));
			deleter.logout();
		}

		final String path = "plugins/Say_Hello.bsh";
		final String contents = "print(\"Hello, world!\");";
		final File file = new File(ijRoot, path);
		writeFile(file, contents);

		final String path2 = "macros/Has (Spaces).ijm";
		final String contents2 = "print(\"Space. The final frontier.\");";
		final File file2 = new File(ijRoot, path2);
		writeFile(file2, contents2);

		CommandLine.main(ijRoot, -1, "upload", "--update-site", updateSiteName, path, path2);

		assertFalse(isUpdateSiteEmpty());

		files.read();
		files.clear();
		files.downloadIndexAndChecksum(new StderrProgress());
		final long timestamp = files.get(path).current.timestamp;
		final long minimalTimestamp = 20130322000000l;
		assertTrue("" + timestamp + " >= " + minimalTimestamp,
				timestamp >= minimalTimestamp);

		assertTrue(file.delete());
		assertTrue(file2.delete());
		CommandLine.main(ijRoot, -1, "update", path, path2);
		assertTrue(file.exists());
		assertTrue(file2.exists());
	}

	public String getURL() {
		return url = getDirectoryProperty("url");
	}

	public String getDirectoryProperty(final String key) {
		final String directory = getProperty(key);
		return directory.endsWith("/") ? directory : directory + "/";
	}

	public String getProperty(final String key) {
		final String result = System.getProperty(propertyPrefix + ".test." + key);
		assumeNotNull(result);
		return result;
	}

	public boolean isUpdateSiteEmpty() throws MalformedURLException, IOException {
		if (url.startsWith("file:")) {
			final File[] list = new File(url.substring(5)).listFiles();
			return list == null || list.length == 0;
		}
		HttpURLConnection connection = (HttpURLConnection) new URL(url + UpdaterUtil.XML_COMPRESSED).openConnection();
		return 404 == connection.getResponseCode();
	}

	public interface Deleter {
		boolean login();
		void delete(final String path) throws IOException;
		void logout();
		default boolean isDeleted(String path) throws IOException {
			throw new NotImplementedException();
		}
	}
}
