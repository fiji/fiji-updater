/*
 * #%L
 * Fiji Updater, which keeps Fiji installations up to date.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/**
 * Tests {@link Checksummer}'s notion of which files the updater manages.
 *
 * @author Curtis Rueden
 */
public class ChecksummerTest {

	private Checksummer checksummer() {
		return new Checksummer(new FilesCollection(new File(".")), null);
	}

	@Test
	public void testRecognizedExtensions() {
		final Checksummer checksummer = checksummer();
		assertTrue(checksummer.isCandidate("jars/imglib2.jar"));
		assertTrue(checksummer.isCandidate("jars/bio-formats/formats-api.jar"));
		assertTrue(checksummer.isCandidate("plugins/Fiji_Plugins.jar"));
		assertTrue(checksummer.isCandidate("macros/StartupMacros.ijm"));
		assertTrue(checksummer.isCandidate("luts/fire.lut"));
		assertTrue(checksummer.isCandidate("scripts/Hello.py"));

		assertFalse(checksummer.isCandidate("jars/notes.txt"));
		assertFalse(checksummer.isCandidate("luts/readme.md"));
		assertFalse(checksummer.isCandidate("nonesuch/whatever.jar"));
	}

	@Test
	public void testUnfilteredDirectories() {
		final Checksummer checksummer = checksummer();
		// These directories declare {""}, meaning every file counts.
		assertTrue(checksummer.isCandidate("lib/linux64/libopencv.so"));
		assertTrue(checksummer.isCandidate("licenses/THIRD-PARTY"));
		assertTrue(checksummer.isCandidate("models/stardist.zip"));
	}

	@Test
	public void testLaunchersAreCandidates() {
		final Checksummer checksummer = checksummer();
		// Launchers count wherever they live, including below a subdirectory
		// and inside a macOS .app folder.
		assertTrue(checksummer.isCandidate("config/jaunch/jaunch-linux-x64"));
		assertTrue(checksummer.isCandidate("config/jaunch-linux-x64"));
		assertTrue(checksummer.isCandidate("Contents/MacOS/ImageJ-macosx"));
		assertTrue(checksummer.isCandidate("Fiji.app/Contents/MacOS/fiji-macos-arm64"));
	}

	/**
	 * The launcher's CFG file holds local machine state -- the chosen JVM, the
	 * Python directory, and eventually the installation's current update
	 * channel. It must never be discovered as an updatable file: an update site
	 * shipping one would overwrite that state for every user of the site.
	 */
	@Test
	public void testLauncherConfigIsNotManaged() {
		final Checksummer checksummer = checksummer();
		assertFalse(checksummer.isCandidate("config/jaunch/fiji.cfg"));
		assertFalse(checksummer.isCandidate("config/jaunch/imagej.cfg"));

		// But the rest of the launcher configuration is updatable.
		assertTrue(checksummer.isCandidate("config/jaunch/fiji.toml"));
		assertTrue(checksummer.isCandidate("config/jaunch/jvm.toml"));
		assertTrue(checksummer.isCandidate("config/jaunch/props.py"));
		assertTrue(checksummer.isCandidate("config/jaunch.txt"));
		assertTrue(checksummer.isCandidate("config/environment.yml"));
		assertTrue(checksummer.isCandidate("config/jaunch/Props.class"));
	}

	/** Windows path separators are normalized before matching. */
	@Test
	public void testWindowsSeparators() {
		final Checksummer checksummer = checksummer();
		assertTrue(checksummer.isCandidate("jars\\imglib2.jar"));
		assertFalse(checksummer.isCandidate("config\\jaunch\\fiji.cfg"));
	}

	/** Directories retired from the managed set are no longer discovered. */
	@Test
	public void testRetiredDirectories() {
		final Checksummer checksummer = checksummer();
		for (final String path : new String[] { "mm/MMConfig_demo.cfg",
			"mmplugins/MMPlugin.jar", "mmautofocus/Autofocus.jar",
			"retro/retrotranslator.jar", "misc/whatever.jar" })
		{
			assertFalse(path, checksummer.isCandidate(path));
		}
	}
}
