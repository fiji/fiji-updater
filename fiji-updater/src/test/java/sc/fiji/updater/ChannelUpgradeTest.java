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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.getWebRoot;
import static sc.fiji.updater.UpdaterTestUtils.initialize;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.After;
import org.junit.Test;

import sc.fiji.updater.util.AppLayout;
import sc.fiji.updater.util.ChannelState;
import sc.fiji.updater.util.Channels;
import sc.fiji.updater.util.StderrProgress;

/**
 * Verifies moving an installation between update channels.
 *
 * @author Curtis Rueden
 */
public class ChannelUpgradeTest {

	private static final String CHANNEL = "A.punctulata";

	protected FilesCollection files;
	protected StderrProgress progress = new StderrProgress();

	@After
	public void after() {
		Channels.setKnown(null);
		if (files != null) cleanup(files);
	}

	private void declareChannel(final File ijRoot, final String channel)
		throws IOException
	{
		final File dir = new File(ijRoot, AppLayout.CONFIG_DIRECTORY);
		assertTrue(dir.exists() || dir.mkdirs());
		final String value = channel == null ? "" : channel;
		Files.write(new File(dir, "fiji.cfg").toPath(),
			("launch-mode=JVM\n" + ChannelState.CHANNEL_KEY + "=" + value + "\n")
				.getBytes("UTF-8"));
	}

	/**
	 * Publishes a channel index listing only the given files, by copying the
	 * base index and removing the rest. Simulates a channel that has dropped
	 * something the base channel offers.
	 */
	private void publishChannelWithout(final File webRoot, final String channel,
		final String dropped) throws Exception
	{
		final File dir = new File(webRoot, channel);
		assertTrue(dir.exists() || dir.mkdirs());
		final FilesCollection remote = new FilesCollection(files.prefix(""));
		remote.read();
		remote.downloadIndexAndChecksum(progress);
		final FileObject drop = remote.get(dropped);
		assertNotNull(drop);
		remote.remove(dropped);
		final byte[] bytes = new XMLFileWriter(
			remote.clone(remote.forUpdateSite(
				FilesCollection.DEFAULT_UPDATE_SITE, true)))
					.toCompressedByteArray(false);
		Files.write(new File(dir, "db.xml.gz").toPath(), bytes);
	}

	private FilesCollection loaded(final File ijRoot) throws Exception {
		final FilesCollection collection = new FilesCollection(ijRoot);
		collection.read();
		collection.prefix(".checksums").delete();
		collection.downloadIndexAndChecksum(progress);
		return collection;
	}

	/**
	 * The defect this whole operation exists to prevent. A file the target
	 * channel does not offer must be removed, not left on disk as LOCAL_ONLY
	 * where it stays on the classpath and no future update ever touches it.
	 */
	@Test
	public void testFilesDroppedByTheTargetChannelAreRemoved() throws Exception {
		files = initialize("macros/keep.ijm", "macros/drop.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, null);
		Channels.setKnown(Arrays.asList(CHANNEL));
		publishChannelWithout(getWebRoot(files), CHANNEL, "macros/drop.ijm");

		final FilesCollection collection = loaded(ijRoot);
		final ChannelUpgrade upgrade = new ChannelUpgrade(collection, CHANNEL);
		upgrade.reconcile(progress);

		assertEquals(Arrays.asList("macros/drop.ijm"), upgrade.stranded());

		upgrade.stage();
		assertEquals(FileObject.Action.UNINSTALL,
			collection.get("macros/drop.ijm").getAction());
	}

	/** A file both channels offer is not disturbed. */
	@Test
	public void testFilesTheTargetChannelKeepsAreNotStranded() throws Exception {
		files = initialize("macros/keep.ijm", "macros/drop.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, null);
		Channels.setKnown(Arrays.asList(CHANNEL));
		publishChannelWithout(getWebRoot(files), CHANNEL, "macros/drop.ijm");

		final FilesCollection collection = loaded(ijRoot);
		final ChannelUpgrade upgrade = new ChannelUpgrade(collection, CHANNEL);
		upgrade.reconcile(progress);
		assertFalse(upgrade.stranded().contains("macros/keep.ijm"));
	}

	/** The move is recorded only on commit, and read back afterwards. */
	@Test
	public void testCommitRecordsTheChannel() throws Exception {
		files = initialize("macros/keep.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, null);
		Channels.setKnown(Arrays.asList(CHANNEL));
		publishChannelWithout(getWebRoot(files), CHANNEL, "macros/keep.ijm");

		final FilesCollection collection = loaded(ijRoot);
		final ChannelUpgrade upgrade = new ChannelUpgrade(collection, CHANNEL);
		upgrade.reconcile(progress);

		// Not yet: an abandoned upgrade must leave the installation knowing
		// what it is.
		assertNull(new FilesCollection(ijRoot).getDeclaredChannelState().channel());

		upgrade.commit();
		assertEquals(CHANNEL,
			new FilesCollection(ijRoot).getDeclaredChannelState().channel());
	}

	/** Reconciling alone changes nothing on disk. */
	@Test
	public void testReconcileDoesNotTouchTheInstallation() throws Exception {
		files = initialize("macros/keep.ijm", "macros/drop.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, null);
		Channels.setKnown(Arrays.asList(CHANNEL));
		publishChannelWithout(getWebRoot(files), CHANNEL, "macros/drop.ijm");

		final FilesCollection collection = loaded(ijRoot);
		new ChannelUpgrade(collection, CHANNEL).reconcile(progress);

		assertTrue("the file must still be on disk until the move is applied",
			new File(ijRoot, "macros/drop.ijm").exists());
		assertNull(new FilesCollection(ijRoot).getDeclaredChannelState().channel());
	}

	/** Moving to the channel already followed is refused rather than staged. */
	@Test
	public void testMovingToTheCurrentChannelIsRefused() throws Exception {
		files = initialize("macros/keep.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, CHANNEL);
		try {
			new ChannelUpgrade(loaded(ijRoot), CHANNEL);
			fail("expected a refusal");
		}
		catch (final IllegalArgumentException expected) {
			assertTrue(expected.getMessage(),
				expected.getMessage().contains("already"));
		}
	}

	/** With no declared channel there is nothing to move from, and nowhere to
	 * record the move, so it is refused rather than guessed. */
	@Test
	public void testUnknownChannelIsRefused() throws Exception {
		files = initialize("macros/keep.ijm");
		try {
			new ChannelUpgrade(loaded(files.prefix("")), CHANNEL);
			fail("expected a refusal");
		}
		catch (final IllegalStateException expected) {
			assertTrue(expected.getMessage(),
				expected.getMessage().contains("Cannot determine"));
		}
	}

	/** Moving to an older channel is recognized as a downgrade. */
	@Test
	public void testDowngradeIsRecognized() throws Exception {
		files = initialize("macros/keep.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, "B.floridae");
		Channels.setKnown(Arrays.asList("B.floridae", CHANNEL));

		final ChannelUpgrade down =
			new ChannelUpgrade(loaded(ijRoot), CHANNEL);
		assertTrue(down.isDowngrade());

		declareChannel(ijRoot, CHANNEL);
		final ChannelUpgrade up =
			new ChannelUpgrade(loaded(ijRoot), "B.floridae");
		assertFalse(up.isDowngrade());
	}

	/** The stored per-site timestamps describe the old channel's indexes, so
	 * they are cleared rather than compared against a different index. */
	@Test
	public void testPerSiteTimestampsAreReset() throws Exception {
		files = initialize("macros/keep.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, null);
		Channels.setKnown(Arrays.asList(CHANNEL));
		publishChannelWithout(getWebRoot(files), CHANNEL, "macros/keep.ijm");

		final FilesCollection collection = loaded(ijRoot);
		final UpdateSite site =
			collection.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, false);
		site.setTimestamp(29991231235959L); // absurdly far in the future

		new ChannelUpgrade(collection, CHANNEL).reconcile(progress);
		assertTrue("a timestamp from the old channel must not survive",
			site.getTimestamp() < 29991231235959L);
	}

	/**
	 * The whole operation through the command line, which is what a headless
	 * installation or a CI job actually runs -- and, since publishing follows
	 * the declared channel, the only way such a job can come to publish for a
	 * new channel at all.
	 */
	@Test
	public void testUpgradeCommand() throws Exception {
		files = initialize("macros/keep.ijm", "macros/drop.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, null);
		Channels.setKnown(Arrays.asList(CHANNEL));
		publishChannelWithout(getWebRoot(files), CHANNEL, "macros/drop.ijm");

		CommandLine.main(ijRoot, -1, progress, "upgrade", CHANNEL);

		// The installation now says what it is, so the next run agrees.
		assertEquals(CHANNEL,
			new FilesCollection(ijRoot).getDeclaredChannelState().channel());

		// And the dropped file is gone. Note that removal is staged differently
		// by file type: a JAR cannot be deleted while the running JVM holds it,
		// so it is marked in the update directory and removed on restart, whereas
		// anything else is renamed to .old straight away.
		assertFalse("the dropped file should no longer be in place",
			new File(ijRoot, "macros/drop.ijm").exists());
		assertTrue(new File(ijRoot, "macros/drop.ijm.old").exists());
		assertTrue("expected the removal to be recorded for the next launch",
			new File(ijRoot, "update/macros/drop.ijm.old").exists());

		// The file the target channel does keep is untouched.
		assertTrue(new File(ijRoot, "macros/keep.ijm").exists());
	}

	/** Simulating reports the same conclusions and changes nothing. */
	@Test
	public void testUpgradeSimulate() throws Exception {
		files = initialize("macros/keep.ijm", "macros/drop.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, null);
		Channels.setKnown(Arrays.asList(CHANNEL));
		publishChannelWithout(getWebRoot(files), CHANNEL, "macros/drop.ijm");

		CommandLine.main(ijRoot, -1, progress, "upgrade", "--simulate", CHANNEL);

		assertNull("simulating must not move the installation",
			new FilesCollection(ijRoot).getDeclaredChannelState().channel());
		assertFalse(new File(ijRoot, "update/macros/drop.ijm").exists());
		assertTrue(new File(ijRoot, "macros/drop.ijm").exists());
	}

	/** With no channel named, the newest one is the destination. */
	@Test
	public void testUpgradeDefaultsToNewest() throws Exception {
		files = initialize("macros/keep.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, null);
		Channels.setKnown(Arrays.asList(CHANNEL));
		publishChannelWithout(getWebRoot(files), CHANNEL, "macros/keep.ijm");

		CommandLine.main(ijRoot, -1, progress, "upgrade");
		assertEquals(CHANNEL,
			new FilesCollection(ijRoot).getDeclaredChannelState().channel());
	}
}
