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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.initialize;
import static sc.fiji.updater.UpdaterTestUtils.readDb;
import static sc.fiji.updater.UpdaterTestUtils.upload;
import static sc.fiji.updater.UpdaterTestUtils.writeJar;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import sc.fiji.updater.Conflicts.Conflict;
import sc.fiji.updater.Conflicts.Conflict.Severity;
import sc.fiji.updater.Conflicts.Resolution;
import sc.fiji.updater.FileObject.Status;

/**
 * Tests what happens when two artifacts share an artifactId, and so want the
 * same file name: {@code org.jetbrains:annotations} and
 * {@code software.amazon.awssdk:annotations} are both {@code annotations.jar}.
 * <p>
 * See <a href="https://github.com/imagej/imagej-updater/issues/120">
 * imagej/imagej-updater#120</a> (from the project's previous home).
 * </p>
 *
 * @author Curtis Rueden
 */
public class ClashingArtifactTest {

	private static final String JETBRAINS = "org.jetbrains:annotations";
	private static final String AWSSDK = "software.amazon.awssdk:annotations";

	private FilesCollection files;

	@After
	public void after() {
		if (files != null) cleanup(files);
	}

	/** A newer version of the same artifact is just an upload. */
	@Test
	public void testSameArtifactIsNoConflict() throws Exception {
		stageSecondJar("jars/annotations-14.0.jar", JETBRAINS);
		assertTrue(criticalConflicts().isEmpty());
	}

	/** A different artifact under the same name is refused. */
	@Test
	public void testDifferentArtifactIsRefused() throws Exception {
		final FileObject file = stageSecondJar("jars/annotations-2.46.15.jar", AWSSDK);
		assertEquals(AWSSDK, file.coordinate);
		assertEquals(JETBRAINS, file.originalCoordinate);

		final List<Conflict> conflicts = criticalConflicts();
		assertEquals(1, conflicts.size());
		final Conflict conflict = conflicts.get(0);
		assertTrue(conflict.getConflict().contains(AWSSDK));
		assertTrue(conflict.getConflict().contains(JETBRAINS));
		assertEquals("Rename it to jars/software.amazon.awssdk.annotations-2.46.15.jar",
			conflict.getResolutions()[0].getDescription());
	}

	/**
	 * Renaming turns the clashing file into an upload of its own, and leaves the
	 * file it was about to replace alone.
	 */
	@Test
	public void testRenameResolvesTheClash() throws Exception {
		stageSecondJar("jars/annotations-2.46.15.jar", AWSSDK);
		final Resolution rename = criticalConflicts().get(0).getResolutions()[0];
		rename.resolve();

		assertFalse(files.prefix("jars/annotations-2.46.15.jar").exists());
		assertTrue(files.prefix(
			"jars/software.amazon.awssdk.annotations-2.46.15.jar").exists());
		assertTrue(criticalConflicts().isEmpty());

		// The clashing file is now a file of its own, about to be uploaded.
		final FileObject renamed =
			files.get("jars/software.amazon.awssdk.annotations.jar");
		assertNotNull(renamed);
		assertEquals(AWSSDK, renamed.coordinate);
		assertEquals(FileObject.Action.UPLOAD, renamed.getAction());

		// The file it would have replaced is untouched, and simply not installed
		// any more -- its .jar is the one that got renamed away.
		final FileObject annotations = files.get("jars/annotations.jar");
		assertEquals(JETBRAINS, annotations.coordinate);
		assertNull(annotations.originalCoordinate);
		assertEquals(Status.NOT_INSTALLED, annotations.getStatus());
		assertNull(annotations.getAction());

		// And the update site ends up offering both, under their own names.
		upload(files);
		final FilesCollection reread = readDb(files);
		assertEquals(JETBRAINS, reread.get("jars/annotations.jar").coordinate);
		assertEquals(AWSSDK, reread.get(
			"jars/software.amazon.awssdk.annotations.jar").coordinate);
	}

	/**
	 * Publishes {@code jars/annotations-13.0.jar} as
	 * {@code org.jetbrains:annotations}, then replaces it on disk with a second
	 * .jar and stages that for upload.
	 *
	 * @param path the second .jar's file name
	 * @param coordinate the second .jar's {@code groupId:artifactId}
	 * @return the file object the second .jar was recorded as
	 */
	private FileObject stageSecondJar(final String path, final String coordinate)
		throws Exception
	{
		files = initialize();
		writeMavenJar("jars/annotations-13.0.jar", JETBRAINS);
		files = readDb(files);
		files.get("jars/annotations.jar").stageForUpload(files,
			FilesCollection.DEFAULT_UPDATE_SITE);
		upload(files);

		assertTrue(files.prefix("jars/annotations-13.0.jar").delete());
		writeMavenJar(path, coordinate);
		files = readDb(files);
		final FileObject file = files.get("jars/annotations.jar");
		assertEquals(JETBRAINS, file.coordinate);
		assertEquals(coordinate, file.localCoordinate);
		file.stageForUpload(files, FilesCollection.DEFAULT_UPDATE_SITE);
		return file;
	}

	private void writeMavenJar(final String path, final String coordinate)
		throws Exception
	{
		writeJar(files, path, "META-INF/maven/" +
			coordinate.replace(':', '/') + "/pom.xml", "<project/>");
	}

	private List<Conflict> criticalConflicts() {
		final List<Conflict> result = new ArrayList<>();
		for (final Conflict conflict : new Conflicts(files).getConflicts(true)) {
			if (conflict.getSeverity() == Severity.CRITICAL_ERROR) {
				result.add(conflict);
			}
		}
		return result;
	}
}
