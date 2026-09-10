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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.getWebRoot;
import static sc.fiji.updater.UpdaterTestUtils.initialize;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.junit.After;
import org.junit.Test;

import sc.fiji.updater.util.AppLayout;
import sc.fiji.updater.util.ChannelState;
import sc.fiji.updater.util.Channels;
import sc.fiji.updater.util.StderrProgress;

/**
 * Verifies that an update site is resolved against the installation's channel,
 * and falls back correctly when it has not published for that channel.
 * <p>
 * These run against real update sites on the filesystem, so they exercise the
 * actual read path -- connection, gzip, parse -- rather than a probe in
 * isolation. The channels used are ones {@link sc.fiji.updater.util.Channels}
 * does not know, which is deliberate: an unrecognized channel yields exactly
 * the two interesting candidates, the channel itself and the base.
 * </p>
 *
 * @author Curtis Rueden
 */
public class ChannelResolutionTest {

	private static final String CHANNEL = "A.punctulata";

	protected FilesCollection files;
	protected StderrProgress progress = new StderrProgress();

	@After
	public void after() {
		Channels.setKnown(null);
		if (files != null) cleanup(files);
	}

	/** Declares the installation's channel, the way the launcher would. */
	private void setChannel(final File ijRoot, final String channel)
		throws IOException
	{
		final File dir = new File(ijRoot, AppLayout.CONFIG_DIRECTORY);
		assertTrue(dir.exists() || dir.mkdirs());
		final File cfg = new File(dir, "fiji.cfg");
		Files.write(cfg.toPath(), ("launch-mode=JVM\n" +
			ChannelState.CHANNEL_KEY + "=" + channel + "\n").getBytes("UTF-8"));
	}

	/** Publishes the site's index into a channel subdirectory as well. */
	private void publishChannel(final File webRoot, final String channel)
		throws IOException
	{
		final File dir = new File(webRoot, channel);
		assertTrue(dir.mkdirs());
		Files.copy(new File(webRoot, "db.xml.gz").toPath(),
			new File(dir, "db.xml.gz").toPath(),
			StandardCopyOption.REPLACE_EXISTING);
	}

	private FilesCollection reread(final File ijRoot) throws Exception {
		final FilesCollection reread = new FilesCollection(ijRoot);
		reread.prefix(".checksums").delete();
		reread.downloadIndexAndChecksum(progress);
		return reread;
	}

	private UpdateSite mainSite(final FilesCollection files) {
		final UpdateSite site =
			files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, false);
		assertNotNull(site);
		return site;
	}

	/** Nothing declares a channel, so nothing changes. */
	@Test
	public void testBaseChannelUnchanged() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");

		final FilesCollection after = reread(ijRoot);
		assertNull(after.getChannel());
		assertNull(mainSite(after).getChannel());
		assertNotNull(after.get("macros/macro.ijm"));
	}

	/** A site that has published for the channel is read from the channel. */
	@Test
	public void testResolvesToChannelWhenPublished() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		publishChannel(getWebRoot(files), CHANNEL);
		setChannel(ijRoot, CHANNEL);

		final FilesCollection after = reread(ijRoot);
		assertEquals(CHANNEL, after.getChannel());
		assertEquals(CHANNEL, mainSite(after).getChannel());
		assertNotNull(after.get("macros/macro.ijm"));
		assertTrue(mainSite(after).getIndexURL().contains("/" + CHANNEL + "/"));
	}

	/** A site that has not falls back to the index at its root. */
	@Test
	public void testFallsBackToBaseWhenChannelAbsent() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		setChannel(ijRoot, CHANNEL);

		final FilesCollection after = reread(ijRoot);
		assertEquals(CHANNEL, after.getChannel());
		assertNull("site should have fallen back to the base channel",
			mainSite(after).getChannel());
		assertNotNull(after.get("macros/macro.ijm"));
	}

	/**
	 * The failure mode that a 404 check alone would miss: the channel index
	 * exists and is served, but is not actually a gzipped index -- the shape of
	 * a misconfigured server answering 200 with an HTML error page. The failure
	 * surfaces inside the gzip decoder, well past the connection, and must still
	 * fall back rather than leaving the site unusable.
	 */
	@Test
	public void testFallsBackWhenChannelIndexIsGarbage() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		final File webRoot = getWebRoot(files);
		final File dir = new File(webRoot, CHANNEL);
		assertTrue(dir.mkdirs());
		Files.write(new File(dir, "db.xml.gz").toPath(),
			"<html><body>404 Not Found</body></html>".getBytes("UTF-8"));
		setChannel(ijRoot, CHANNEL);

		final FilesCollection after = reread(ijRoot);
		assertNull("garbage channel index should fall back",
			mainSite(after).getChannel());
		assertNotNull(after.get("macros/macro.ijm"));
	}

	/**
	 * An empty channel index is a valid index that happens to list nothing, not
	 * a failure -- a maintainer may legitimately publish an empty channel. It
	 * must be used rather than silently falling back to the richer base index.
	 * <p>
	 * This also pins the orphaning behavior that channel upgrades will have to
	 * reckon with. A file the resolved index does not mention keeps its local
	 * record and becomes LOCAL_ONLY with no update site, which means it stays on
	 * disk, and on the classpath, managed by nobody. That is tolerable here,
	 * where the site simply has less to offer this channel. It will not be
	 * tolerable when an installation moves between channels: reconciling the
	 * before and after file sets, and staging removals for what the new channel
	 * drops, is a prerequisite for that step. When it lands, this assertion is
	 * the one to revisit.
	 * </p>
	 */
	@Test
	public void testEmptyChannelIndexIsHonored() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		final File webRoot = getWebRoot(files);
		final File dir = new File(webRoot, CHANNEL);
		assertTrue(dir.mkdirs());
		UpdaterTestUtils.writeGZippedFile(dir, "db.xml.gz", "<pluginRecords />");
		setChannel(ijRoot, CHANNEL);

		final FilesCollection after = reread(ijRoot);
		assertEquals("the empty channel index should have been used, not skipped",
			CHANNEL, mainSite(after).getChannel());

		// The file is no longer offered by any site, but it is still on disk.
		final FileObject orphan = after.get("macros/macro.ijm");
		assertNotNull(orphan);
		assertEquals(FileObject.Status.LOCAL_ONLY, orphan.getStatus());
		assertNull(orphan.updateSite);
	}

	/**
	 * The regression test for the worst thing that can go wrong.
	 * <p>
	 * An installation that has been upgraded to a channel, run somewhere its
	 * launcher configuration cannot be found -- PyImageJ, CI, an IDE -- must not
	 * be resolved as though it were on the base channel. Doing so would point
	 * every site at its oldest index and roll the entire installation backwards,
	 * while reporting a perfectly successful update.
	 * </p>
	 * <p>
	 * Here the channel is genuinely undeterminable and channels exist, so the
	 * updater must decline to check anything and say why.
	 * </p>
	 */
	@Test
	public void testRefusesToResolveWhenChannelUnknown() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		publishChannel(getWebRoot(files), CHANNEL);

		// Channels exist in the world, but this installation cannot say which
		// one it follows: there is no launcher configuration anywhere.
		Channels.setKnown(java.util.Arrays.asList(CHANNEL));

		final FilesCollection after = new FilesCollection(ijRoot);
		after.tryLoadingCollection();
		assertFalse(after.getChannelState().isKnown());

		final XMLFileDownloader downloader = new XMLFileDownloader(after);
		downloader.start(false);

		final String warnings = downloader.getWarnings();
		assertTrue("expected a refusal, got: " + warnings,
			warnings.contains("Cannot determine which update channel"));
		assertNull("no site may be resolved when the channel is unknown",
			mainSite(after).getChannel());
	}

	/**
	 * The same situation before any channel exists is not dangerous: the base
	 * channel is the only one there is, so an unknown channel and the base
	 * channel are the same thing. The updater must not refuse here, or it would
	 * refuse on every installation in the world today.
	 */
	@Test
	public void testDoesNotRefuseWhenNoChannelsExist() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");

		final FilesCollection after = new FilesCollection(ijRoot);
		after.tryLoadingCollection();
		assertFalse(after.getChannelState().isKnown());

		final XMLFileDownloader downloader = new XMLFileDownloader(after);
		downloader.start(false);

		assertEquals("", downloader.getWarnings().trim());
		assertNotNull(after.get("macros/macro.ijm"));
	}

	/**
	 * What a base-channel client sees when a site serves only a channel index.
	 * <p>
	 * Not "an empty site": every candidate fails, so the site is reported as
	 * unreadable and treated as deleted. That is the argument for creating a
	 * site's first index at its root rather than in the creating maintainer's
	 * channel -- an empty index asserts nothing about any channel, and it is the
	 * difference between the site existing with nothing in it and the site
	 * appearing to have gone away.
	 * </p>
	 */
	@Test
	public void testChannelOnlySiteIsUnreadableFromBase() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		final File webRoot = getWebRoot(files);

		// Move the whole index into a channel, leaving the site root bare.
		publishChannel(webRoot, CHANNEL);
		assertTrue(new File(webRoot, "db.xml.gz").delete());

		// This installation is on the base channel and knows of no other.
		final FilesCollection after = new FilesCollection(ijRoot);
		after.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(after);
		downloader.start(false);

		final String warnings = downloader.getWarnings();
		assertTrue("expected the site to be reported unreadable, got: " + warnings,
			warnings.contains("Could not update from site"));
	}
}
