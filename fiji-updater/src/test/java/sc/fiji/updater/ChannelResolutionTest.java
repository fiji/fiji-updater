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
import sc.fiji.updater.util.ChannelManifest;
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

	private static final String THIRD_PARTY = "Third-Party";

	/**
	 * The channels this test pretends exist, standing in for the list the core
	 * update site would publish. Applied to every collection the test builds.
	 */
	protected java.util.List<String> CHANNELS_IN_EXISTENCE = java.util.Collections.emptyList();

	/** A collection that sees the channels this test says exist. */
	private FilesCollection collection(final java.io.File ijRoot) {
		final FilesCollection collection = new FilesCollection(ijRoot);
		collection.setChannels(CHANNELS_IN_EXISTENCE);
		return collection;
	}

	protected FilesCollection files;
	protected StderrProgress progress = new StderrProgress();

	@After
	public void after() {
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
		final FilesCollection reread = collection(ijRoot);
		reread.prefix(".checksums").delete();
		reread.downloadIndexAndChecksum(progress);
		return reread;
	}

	/**
	 * Adds a third-party update site and returns its web root.
	 * <p>
	 * Several of these tests are about what a site that is <em>not</em> the core
	 * one does, and the main site can no longer stand in for that: it is the
	 * core site, and the core site is the one place fallback does not happen.
	 * </p>
	 */
	private File thirdPartySite() throws Exception {
		return UpdaterTestUtils.addUpdateSite(files, THIRD_PARTY);
	}

	private UpdateSite thirdParty(final FilesCollection files) {
		final UpdateSite site = files.getUpdateSite(THIRD_PARTY, false);
		assertNotNull(site);
		return site;
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

	/** A third-party site that has not falls back to the index at its root. */
	@Test
	public void testFallsBackToBaseWhenChannelAbsent() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		thirdPartySite();
		publishChannel(getWebRoot(files), CHANNEL);
		setChannel(ijRoot, CHANNEL);

		final FilesCollection after = reread(ijRoot);
		assertEquals(CHANNEL, after.getChannel());
		assertEquals("the core site publishes the channel and must be read there",
			CHANNEL, mainSite(after).getChannel());
		assertNull("a third-party site should fall back to the base channel",
			thirdParty(after).getChannel());
		assertNotNull(after.get("macros/macro.ijm"));
	}

	/**
	 * The asymmetry that makes the core site different from every other one.
	 * <p>
	 * For a third-party site, the index at the root is what its maintainer
	 * publishes for everyone, so reading it there is the whole point of the
	 * fallback. The core site's root is not that: it is the previous edition of
	 * the application. Falling back to it would replace a current installation
	 * with an older one -- a downgrade of the application itself, performed
	 * silently, in the course of what the user asked to be an update.
	 * </p>
	 * <p>
	 * So the core site resolves to this installation's channel or to nothing,
	 * and nothing is said loudly. The installation is left as it was, which is
	 * the worse-looking but far better outcome.
	 * </p>
	 */
	@Test
	public void testCoreSiteDoesNotFallBack() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		setChannel(ijRoot, CHANNEL);
		CHANNELS_IN_EXISTENCE = java.util.Arrays.asList(CHANNEL);

		final FilesCollection fresh = collection(ijRoot);
		fresh.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(fresh);
		downloader.start(false);

		final String warnings = downloader.getWarnings();
		assertTrue("the core site must say so, loudly: " + warnings,
			warnings.contains("The core update site does not serve the " + CHANNEL));
		assertFalse("the core site is not merely lagging: " + warnings,
			warnings.contains("have not published for"));
		assertNull("the core site must not have resolved to anything",
			mainSite(fresh).getChannel());
	}

	/**
	 * A core site on the base channel still reads its root, because for a
	 * base-channel installation the root <em>is</em> its channel. The rule is
	 * "never below your own channel", not "never the root".
	 */
	@Test
	public void testCoreSiteOnBaseChannelReadsTheRoot() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");

		final FilesCollection fresh = collection(ijRoot);
		fresh.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(fresh);
		downloader.start(false);

		assertEquals("", downloader.getWarnings().trim());
		assertNull(mainSite(fresh).getChannel());
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
		final File thirdPartyRoot = thirdPartySite();
		publishChannel(getWebRoot(files), CHANNEL);
		final File dir = new File(thirdPartyRoot, CHANNEL);
		assertTrue(dir.mkdirs());
		Files.write(new File(dir, "db.xml.gz").toPath(),
			"<html><body>404 Not Found</body></html>".getBytes("UTF-8"));
		setChannel(ijRoot, CHANNEL);

		final FilesCollection after = reread(ijRoot);
		assertNull("garbage channel index should fall back",
			thirdParty(after).getChannel());
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
		CHANNELS_IN_EXISTENCE = java.util.Arrays.asList(CHANNEL);

		final FilesCollection after = collection(ijRoot);
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

		final FilesCollection after = collection(ijRoot);
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
		final FilesCollection after = collection(ijRoot);
		after.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(after);
		downloader.start(false);

		final String warnings = downloader.getWarnings();
		assertTrue("expected the site to be reported unreadable, got: " + warnings,
			warnings.contains("Could not update from site"));
	}

	/**
	 * Resolving against a channel the installation does not follow, which is how
	 * an upgrade works out what moving would do before committing to it.
	 */
	@Test
	public void testOverrideDrivesResolution() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		publishChannel(getWebRoot(files), CHANNEL);
		CHANNELS_IN_EXISTENCE = java.util.Arrays.asList(CHANNEL);

		// Without it, the run declines rather than guessing.
		final FilesCollection unpinned = collection(ijRoot);
		unpinned.tryLoadingCollection();
		final XMLFileDownloader refused = new XMLFileDownloader(unpinned);
		refused.start(false);
		assertTrue(refused.getWarnings().contains("Cannot determine"));

		// With it, the named channel is used.
		final FilesCollection pinned = collection(ijRoot);
		pinned.pinChannel(CHANNEL);
		pinned.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(pinned);
		downloader.start(false);

		assertEquals("", downloader.getWarnings().trim());
		assertEquals(CHANNEL, mainSite(pinned).getChannel());
	}

	/** An override writes nothing back: the installation still declares what it
	 * declared, so an abandoned upgrade leaves it knowing what it is. */
	@Test
	public void testOverrideDoesNotChangeTheInstallation() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");

		final FilesCollection pinned = collection(ijRoot);
		pinned.pinChannel(CHANNEL);
		assertEquals(CHANNEL, pinned.getChannel());

		// A fresh look at the installation still finds no channel declared.
		assertFalse(collection(ijRoot).getChannelState().isKnown());
	}

	/** Publishes a manifest declaring which channels a site carries. */
	private void publishManifest(final File webRoot, final String... channels)
		throws IOException
	{
		Files.write(new File(webRoot, ChannelManifest.FILENAME).toPath(),
			ChannelManifest.of(java.util.Arrays.asList(channels)).toByteArray());
	}

	/**
	 * The property that keeps this warning credible: a site that has never
	 * engaged with channels says nothing by falling back, and is not reported.
	 * On the day channels ship this is every site in existence, so a rule keyed
	 * on the fallback itself would produce hundreds of warnings that everyone
	 * would learn to ignore.
	 */
	@Test
	public void testSiteWithoutManifestIsNotReported() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		thirdPartySite();
		publishChannel(getWebRoot(files), CHANNEL);
		setChannel(ijRoot, CHANNEL);
		CHANNELS_IN_EXISTENCE = java.util.Arrays.asList(CHANNEL);

		final FilesCollection after = reread(ijRoot);
		assertNull(thirdParty(after).getChannel());

		final FilesCollection fresh = collection(ijRoot);
		fresh.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(fresh);
		downloader.start(false);
		assertEquals("", downloader.getWarnings().trim());
	}

	/**
	 * A site that publishes a manifest has engaged with channels, so the absence
	 * of this one from it is a statement rather than a silence.
	 */
	@Test
	public void testSiteWithManifestLackingOurChannelIsReported()
		throws Exception
	{
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		final File thirdPartyRoot = thirdPartySite();
		publishChannel(getWebRoot(files), CHANNEL);
		publishChannel(thirdPartyRoot, "B.floridae");
		publishManifest(thirdPartyRoot, "B.floridae");
		setChannel(ijRoot, CHANNEL);
		CHANNELS_IN_EXISTENCE = java.util.Arrays.asList("B.floridae", CHANNEL);

		final FilesCollection fresh = collection(ijRoot);
		fresh.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(fresh);
		downloader.start(false);

		final String warnings = downloader.getWarnings();
		assertTrue(warnings, warnings.contains("have not published for " + CHANNEL));
		assertTrue(warnings, warnings.contains(THIRD_PARTY));
	}

	/** A site that has published for this channel is not reported. */
	@Test
	public void testAdoptedSiteIsNotReported() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		final File webRoot = getWebRoot(files);
		publishChannel(webRoot, CHANNEL);
		publishManifest(webRoot, CHANNEL);
		setChannel(ijRoot, CHANNEL);
		CHANNELS_IN_EXISTENCE = java.util.Arrays.asList(CHANNEL);

		final FilesCollection fresh = collection(ijRoot);
		fresh.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(fresh);
		downloader.start(false);
		assertEquals("", downloader.getWarnings().trim());
	}

	/** An installation on the base channel has nothing to be behind. */
	@Test
	public void testBaseChannelInstallationIsNeverWarned() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		final File webRoot = getWebRoot(files);
		publishChannel(webRoot, CHANNEL);
		publishManifest(webRoot, CHANNEL);

		final FilesCollection fresh = collection(ijRoot);
		fresh.tryLoadingCollection();
		final XMLFileDownloader downloader = new XMLFileDownloader(fresh);
		downloader.start(false);
		assertEquals("", downloader.getWarnings().trim());
	}
}
