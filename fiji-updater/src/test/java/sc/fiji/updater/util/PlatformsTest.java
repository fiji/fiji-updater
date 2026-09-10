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

import org.junit.Test;

/**
 * Tests {@link Platforms}' launcher and platform vocabulary.
 *
 * @author Curtis Rueden
 */
public class PlatformsTest {

	/**
	 * The ImageJ launcher is gone from the vocabulary. This updater targets Java
	 * 11 and so never runs on an installation that has one.
	 */
	@Test
	public void testImageJLaunchersAreNotLaunchers() {
		for (final String launcher : new String[] { "ImageJ-linux32",
			"ImageJ-linux64", "ImageJ-win32.exe", "ImageJ-win64.exe",
			"Contents/MacOS/ImageJ-macosx", "Contents/MacOS/ImageJ-tiger" })
		{
			assertFalse(launcher, Platforms.isLauncher(launcher));
			assertNull(launcher, Platforms.platformForLauncher(launcher));
			assertFalse(launcher, Platforms.launchers().contains(launcher));
		}
	}

	/** The Jaunch launchers are, of course, still launchers. */
	@Test
	public void testJaunchLaunchersAreRecognized() {
		assertTrue(Platforms.isLauncher("fiji-linux-x64"));
		assertTrue(Platforms.isLauncher("fiji-windows-x64.exe"));
		assertTrue(Platforms.isLauncher("config/jaunch-linux-x64"));
	}

	/** Everything inside a top-level .app folder still counts, code-signing. */
	@Test
	public void testAppFolderContentsAreLaunchers() {
		assertTrue(Platforms.isLauncher("Fiji.app/Contents/MacOS/fiji-macos-arm64"));
		assertEquals("macosx",
			Platforms.platformForLauncher("Fiji.app/Contents/Info.plist"));
	}

	/**
	 * Losing the last 32-bit launcher must not lose the 32-bit platform names.
	 * They are two different things: an update site can still publish
	 * {@code jars/win32} or {@code lib/linux32} content, and
	 * {@code Checksummer.guessPlatform} validates what it finds there against
	 * this list.
	 */
	@Test
	public void test32BitPlatformsSurviveTheirLaunchers() {
		assertTrue(Platforms.known().contains("linux32"));
		assertTrue(Platforms.known().contains("win32"));
	}

	/** The platform names the rest of the updater expects to exist. */
	@Test
	public void testKnownPlatforms() {
		for (final String platform : new String[] { "linux32", "linux64",
			"linux-arm64", "linuxx", "macos64", "macos-arm64", "macosx", "win32",
			"win64", "win-arm64", "winx" })
		{
			assertTrue(platform, Platforms.known().contains(platform));
		}
	}
}
