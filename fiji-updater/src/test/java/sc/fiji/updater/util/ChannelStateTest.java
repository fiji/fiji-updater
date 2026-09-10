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
package sc.fiji.updater.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests {@link ChannelState}, in particular that an undeterminable channel is
 * never mistaken for the base channel.
 *
 * @author Curtis Rueden
 */
public class ChannelStateTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	@After
	public void clearProperties() {
		System.clearProperty(AppLayout.CONFIG_FILE_PROPERTY);
		System.clearProperty("scijava.app.name");
	}

	private File appRootWithConfig(final String name, final String... lines)
		throws IOException
	{
		final File root = tmp.newFolder();
		final File dir = new File(root, AppLayout.CONFIG_DIRECTORY);
		assertTrue(dir.mkdirs());
		final File cfg = new File(dir, name);
		Files.write(cfg.toPath(), String.join("\n", lines).getBytes("UTF-8"));
		return root;
	}

	/**
	 * The case that must never be conflated with the base channel: no launcher,
	 * no configuration file, therefore no way to know.
	 */
	@Test
	public void testUnknownWhenNoConfigFile() throws IOException {
		final ChannelState state = ChannelState.read(tmp.newFolder());
		assertFalse(state.isKnown());
		assertEquals("unknown", state.displayName());
	}

	@Test
	public void testUnknownWhenNoAppRoot() {
		assertFalse(ChannelState.read(null).isKnown());
	}

	/** A configuration file with no channel key means the base channel. */
	@Test
	public void testBaseChannel() throws IOException {
		final File root = appRootWithConfig("fiji.cfg", "python-dir=python/linux64");
		final ChannelState state = ChannelState.read(root);
		assertTrue(state.isKnown());
		assertNull(state.channel());
		assertEquals(ChannelState.BASE_CHANNEL_NAME, state.displayName());
	}

	/** An empty value is the base channel too, not a channel named "". */
	@Test
	public void testEmptyValueIsBaseChannel() throws IOException {
		final File root = appRootWithConfig("fiji.cfg",
			ChannelState.CHANNEL_KEY + "=");
		final ChannelState state = ChannelState.read(root);
		assertTrue(state.isKnown());
		assertNull(state.channel());
	}

	@Test
	public void testNamedChannel() throws IOException {
		final File root = appRootWithConfig("fiji.cfg", "launch-mode=JVM",
			ChannelState.CHANNEL_KEY + "=A.punctulata");
		final ChannelState state = ChannelState.read(root);
		assertTrue(state.isKnown());
		assertEquals("A.punctulata", state.channel());
		assertEquals("A.punctulata", state.displayName());
	}

	/** The launcher-declared path wins over searching the installation. */
	@Test
	public void testConfigFileProperty() throws IOException {
		final File root = appRootWithConfig("fiji.cfg",
			ChannelState.CHANNEL_KEY + "=FromAppRoot");
		final File elsewhere = tmp.newFile("elsewhere.cfg");
		Files.write(elsewhere.toPath(),
			(ChannelState.CHANNEL_KEY + "=FromProperty").getBytes("UTF-8"));
		System.setProperty(AppLayout.CONFIG_FILE_PROPERTY,
			elsewhere.getAbsolutePath());
		assertEquals("FromProperty", ChannelState.read(root).channel());
	}

	/** Two configuration files with nothing to choose between them: unknown. */
	@Test
	public void testAmbiguousConfigIsUnknown() throws IOException {
		final File root = appRootWithConfig("fiji.cfg",
			ChannelState.CHANNEL_KEY + "=One");
		final File dir = new File(root, AppLayout.CONFIG_DIRECTORY);
		Files.write(new File(dir, "other.cfg").toPath(),
			(ChannelState.CHANNEL_KEY + "=Two").getBytes("UTF-8"));
		assertFalse(ChannelState.read(root).isKnown());
	}

	/** Unless the application names itself, in which case that one wins. */
	@Test
	public void testAmbiguityResolvedByAppName() throws IOException {
		final File root = appRootWithConfig("fiji.cfg",
			ChannelState.CHANNEL_KEY + "=Mine");
		final File dir = new File(root, AppLayout.CONFIG_DIRECTORY);
		Files.write(new File(dir, "other.cfg").toPath(),
			(ChannelState.CHANNEL_KEY + "=Theirs").getBytes("UTF-8"));
		System.setProperty("scijava.app.name", "Fiji");
		assertEquals("Mine", ChannelState.read(root).channel());
	}

	/** Writing preserves the rest of the file, which is local machine state. */
	@Test
	public void testWritePreservesOtherEntries() throws IOException {
		final File root = appRootWithConfig("fiji.cfg",
			"python-dir=python/linux64", "launch-mode=JVM");
		final ChannelState updated =
			ChannelState.read(root).write("B.floridae");
		assertEquals("B.floridae", updated.channel());

		final ChannelState reread = ChannelState.read(root);
		assertEquals("B.floridae", reread.channel());

		final String contents = new String(
			Files.readAllBytes(reread.configFile().toPath()), "UTF-8");
		assertTrue(contents, contents.contains("python-dir=python/linux64"));
		assertTrue(contents, contents.contains("launch-mode=JVM"));
	}

	/** With nowhere to record it, changing channel must fail loudly. */
	@Test
	public void testWriteWithoutConfigFileFails() throws IOException {
		try {
			ChannelState.read(tmp.newFolder()).write("C.elegans");
			fail("expected an IOException");
		}
		catch (final IOException expected) {
			// pass
		}
	}

	/** A named channel is known even with no configuration file anywhere. */
	@Test
	public void testPinnedChannel() {
		final ChannelState state = ChannelState.pinned("A.punctulata");
		assertTrue(state.isKnown());
		assertEquals("A.punctulata", state.channel());
	}

	/** The base channel can be named explicitly, by its display name or empty. */
	@Test
	public void testPinnedBaseChannel() {
		for (final String name : new String[] { null, "", "  ",
			ChannelState.BASE_CHANNEL_NAME,
			ChannelState.BASE_CHANNEL_NAME.toLowerCase() })
		{
			final ChannelState state = ChannelState.pinned(name);
			assertTrue(String.valueOf(name), state.isKnown());
			assertNull(String.valueOf(name), state.channel());
		}
	}

	/**
	 * Pinning is an override for one run, not a change to the installation.
	 * Writing it back would turn a command-line flag into an upgrade.
	 */
	@Test
	public void testPinnedChannelCannotBeWritten() throws IOException {
		try {
			ChannelState.pinned("A.punctulata").write("B.floridae");
			fail("expected an IOException");
		}
		catch (final IOException expected) {
			// pass
		}
	}
}
