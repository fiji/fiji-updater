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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static sc.fiji.updater.UpdaterTestUtils.assertStatus;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.getWebRoot;
import static sc.fiji.updater.UpdaterTestUtils.initialize;
import static sc.fiji.updater.UpdaterTestUtils.readDb;
import static sc.fiji.updater.UpdaterTestUtils.update;
import static sc.fiji.updater.UpdaterTestUtils.upload;
import static sc.fiji.updater.UpdaterTestUtils.writeJar;

import java.io.File;

import org.junit.After;
import org.junit.Test;

import sc.fiji.updater.FileObject.Action;
import sc.fiji.updater.FileObject.Status;

/**
 * Tests that content already on disk is copied into place rather than
 * downloaded again.
 *
 * @author Curtis Rueden
 */
public class InstallerReuseTest {

	private FilesCollection files;

	@After
	public void after() {
		if (files != null) cleanup(files);
	}

	/**
	 * Installing content the installation already has takes it from disk. The
	 * update site's copy is removed first, so a download could not succeed.
	 */
	@Test
	public void testContentOnDiskIsNotDownloadedAgain() throws Exception {
		final FileObject file = stageTwinForInstall();
		deleteUploadedFiles();

		update(files);
		assertTrue(files.prefix("jars/twin-1.0.jar").exists());
		assertStatus(Status.INSTALLED, file);
	}

	/**
	 * Content that turns out not to match is downloaded after all, rather than
	 * installed wrongly.
	 */
	@Test
	public void testUnusableContentFallsBackToDownloading() throws Exception {
		final FileObject file = stageTwinForInstall();
		// The file the installer would copy from is no longer what the
		// checksummer recorded it as.
		writeJar(files, "jars/original-1.0.jar", "shared.txt", "something else");

		update(files);
		assertTrue(files.prefix("jars/twin-1.0.jar").exists());
		assertStatus(Status.INSTALLED, file);
		assertEquals("shared", jarEntry(files.prefix("jars/twin-1.0.jar")));
	}

	/**
	 * Publishes two .jar files with identical content, keeps only one of them
	 * installed, and stages the other for installation.
	 *
	 * @return the file staged for installation
	 */
	private FileObject stageTwinForInstall() throws Exception {
		files = initialize();
		writeJar(files, "jars/original-1.0.jar", "shared.txt", "shared");
		writeJar(files, "jars/twin-1.0.jar", "shared.txt", "shared");
		files = readDb(files);
		for (final String name : new String[] { "jars/original-1.0.jar",
			"jars/twin-1.0.jar" })
		{
			files.get(name).stageForUpload(files, FilesCollection.DEFAULT_UPDATE_SITE);
		}
		upload(files);

		assertTrue(files.prefix("jars/twin-1.0.jar").delete());
		files = readDb(files);
		final FileObject original = files.get("jars/original-1.0.jar");
		final FileObject twin = files.get("jars/twin-1.0.jar");
		assertStatus(Status.INSTALLED, original);
		assertStatus(Status.NOT_INSTALLED, twin);
		assertEquals(original.localChecksum, twin.getChecksum());
		twin.setAction(files, Action.INSTALL);
		return twin;
	}

	/** Empties the update site of everything but its index. */
	private void deleteUploadedFiles() {
		final File jars = new File(getWebRoot(files), "jars");
		for (final File file : jars.listFiles()) {
			assertTrue(file.delete());
		}
		assertFalse(new File(jars, "twin-1.0.jar").exists());
	}

	private String jarEntry(final File file) throws Exception {
		try (final java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
			final java.io.InputStream in =
				jar.getInputStream(jar.getEntry("shared.txt"));
			return new String(in.readAllBytes());
		}
	}
}
