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
import sc.fiji.updater.util.UpdateCanceledException;
import sc.fiji.updater.util.UpdaterUserInterface;

/**
 * Verifies which channel an upload publishes to.
 * <p>
 * The index being uploaded is generated from the local
 * {@link FilesCollection}, so it describes the state this installation
 * actually resolved. That makes the upload channel a consequence of the
 * installation rather than a choice: publishing elsewhere would assert, of
 * some other channel, a set of versions nobody has run.
 * </p>
 *
 * @author Curtis Rueden
 */
public class UploadChannelTest {

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

	private void declareChannel(final File ijRoot, final String channel)
		throws IOException
	{
		final File dir = new File(ijRoot, AppLayout.CONFIG_DIRECTORY);
		assertTrue(dir.exists() || dir.mkdirs());
		Files.write(new File(dir, "fiji.cfg").toPath(),
			(ChannelState.CHANNEL_KEY + "=" + channel + "\n").getBytes("UTF-8"));
	}

	private FilesUploader uploaderFor(final File ijRoot) throws Exception {
		final FilesCollection collection = collection(ijRoot);
		collection.read();
		return new FilesUploader(null, collection,
			FilesCollection.DEFAULT_UPDATE_SITE, progress);
	}

	/** No channel declared and none in existence: the base is the only target. */
	@Test
	public void testBaseChannelUpload() throws Exception {
		files = initialize("macros/macro.ijm");
		assertNull(uploaderFor(files.prefix("")).getUploadChannel());
	}

	/** The upload follows the installation's channel, without being asked. */
	@Test
	public void testFollowsInstallationChannel() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, "A.punctulata");
		assertEquals("A.punctulata", uploaderFor(ijRoot).getUploadChannel());
	}

	/**
	 * Being on an older channel than the newest published does not redirect the
	 * upload to the newest. The maintainer publishes what they ran.
	 */
	@Test
	public void testDoesNotFollowTheNewestChannel() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		CHANNELS_IN_EXISTENCE = Arrays.asList("B.floridae", "A.punctulata");
		declareChannel(ijRoot, "A.punctulata");

		assertEquals("B.floridae", Channels.newest(CHANNELS_IN_EXISTENCE));
		assertEquals("the upload must follow the installation, not the newest " +
			"channel in existence", "A.punctulata",
			uploaderFor(ijRoot).getUploadChannel());
	}

	/**
	 * With channels in existence and no way to tell which one this installation
	 * follows, there is no honest target, so the upload must not proceed.
	 */
	@Test
	public void testRefusesWhenChannelUnknown() throws Exception {
		files = initialize("macros/macro.ijm");
		CHANNELS_IN_EXISTENCE = Arrays.asList("A.punctulata");
		try {
			uploaderFor(files.prefix("")).getUploadChannel();
			fail("expected a refusal");
		}
		catch (final IllegalStateException expected) {
			assertTrue(expected.getMessage(),
				expected.getMessage().contains("which channel to publish to"));
		}
	}

	/**
	 * Creating a site is different: its index is empty, so it asserts nothing
	 * about any channel and belongs at the site root, where every client can see
	 * that the site exists. The first real upload then adopts the maintainer's
	 * channel.
	 */
	@Test
	public void testNewSiteIsCreatedAtTheBase() throws Exception {
		CHANNELS_IN_EXISTENCE = Arrays.asList("A.punctulata");
		final FilesUploader uploader = FilesUploader.initialUploader(null,
			"file:/tmp/nonesuch/", "file:localhost", "/tmp/nonesuch/", progress);
		assertNull(uploader.getUploadChannel());
	}

	/** Records what was asked, and answers however the test wants. */
	private static class RecordingUI extends UpdaterUserInterface.StderrInterface {

		String prompt;
		boolean answer;

		RecordingUI(final boolean answer) {
			this.answer = answer;
		}

		@Override
		public boolean isBatchMode() {
			return false;
		}

		@Override
		public boolean promptYesNo(final String message, final String title) {
			prompt = message;
			return answer;
		}
	}

	/**
	 * Adopting a channel is otherwise silent but has consequences for the site's
	 * existing users, so it is confirmed, and the confirmation says what happens
	 * to them.
	 */
	@Test
	public void testFirstUploadToChannelIsConfirmed() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, "A.punctulata");

		final RecordingUI ui = new RecordingUI(false);
		final UpdaterUserInterface previous = UpdaterUserInterface.get();
		UpdaterUserInterface.set(ui);
		try {
			final FilesCollection collection = collection(ijRoot);
			collection.read();
			collection.downloadIndexAndChecksum(progress);
			final File macro = new File(ijRoot, "macros/macro.ijm");
			Files.write(macro.toPath(), "print(\"changed\");".getBytes("UTF-8"));
			collection.prefix(".checksums").delete();
			collection.downloadIndexAndChecksum(progress);
			collection.get("macros/macro.ijm").stageForUpload(collection,
				FilesCollection.DEFAULT_UPDATE_SITE);

			final FilesUploader uploader = new FilesUploader(null, collection,
				FilesCollection.DEFAULT_UPDATE_SITE, progress);
			assertTrue("the site has no A.punctulata index yet",
				uploader.isFirstUploadToChannel());
			assertTrue(uploader.login());
			try {
				uploader.upload(progress);
				fail("declining the confirmation should have cancelled the upload");
			}
			catch (final UpdateCanceledException expected) {
				// pass
			}

			assertNotNull("the user should have been asked", ui.prompt);
			assertTrue(ui.prompt, ui.prompt.contains("first upload to A.punctulata"));
			assertTrue(ui.prompt,
				ui.prompt.contains(ChannelState.BASE_CHANNEL_NAME));
		}
		finally {
			UpdaterUserInterface.set(previous);
		}
	}

	/** Uploading to the base channel is not an adoption, so nothing is asked. */
	@Test
	public void testBaseChannelUploadIsNotConfirmed() throws Exception {
		files = initialize("macros/macro.ijm");
		final FilesUploader uploader = uploaderFor(files.prefix(""));
		assertFalse(uploader.isFirstUploadToChannel());
	}

	/**
	 * An override says which remote index to read. It does not change which
	 * versions are installed here, and those are what the uploaded index
	 * describes -- so it must not redirect an upload.
	 * <p>
	 * Publishing against an override would assert, of the named channel, a set
	 * of versions this installation may never have had. The way to publish for a
	 * channel is to move the installation to it and update, which is what makes
	 * the assertion true.
	 * </p>
	 */
	@Test
	public void testOverrideDoesNotRedirectTheUpload() throws Exception {
		files = initialize("macros/macro.ijm");
		final File ijRoot = files.prefix("");
		declareChannel(ijRoot, "A.punctulata");
		CHANNELS_IN_EXISTENCE = Arrays.asList("B.floridae", "A.punctulata");

		final FilesCollection collection = collection(ijRoot);
		collection.read();
		collection.pinChannel("B.floridae");

		// Reading follows the override...
		assertEquals("B.floridae", collection.getChannel());
		// ...while publishing follows what the installation actually is.
		assertEquals("A.punctulata",
			collection.getDeclaredChannelState().channel());
		final FilesUploader uploader = new FilesUploader(null, collection,
			FilesCollection.DEFAULT_UPDATE_SITE, progress);
		assertEquals("A.punctulata", uploader.getUploadChannel());
	}

	/**
	 * And an override cannot conjure a target where the installation declares
	 * none: with channels in existence and nothing to read, there is no honest
	 * answer, and naming one for the run does not create one.
	 */
	@Test
	public void testOverrideDoesNotSatisfyAnUnknownChannel() throws Exception {
		files = initialize("macros/macro.ijm");
		CHANNELS_IN_EXISTENCE = Arrays.asList("A.punctulata");

		final FilesCollection collection = collection(files.prefix(""));
		collection.read();
		assertFalse("no launcher configuration exists here",
			collection.getDeclaredChannelState().isKnown());
		collection.pinChannel("A.punctulata");

		final FilesUploader uploader = new FilesUploader(null, collection,
			FilesCollection.DEFAULT_UPDATE_SITE, progress);
		try {
			uploader.getUploadChannel();
			fail("an override must not stand in for the installation's channel");
		}
		catch (final IllegalStateException expected) {
			// pass
		}
	}
}
