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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.initialize;
import static sc.fiji.updater.UpdaterTestUtils.readDb;
import static sc.fiji.updater.UpdaterTestUtils.upload;
import static sc.fiji.updater.UpdaterTestUtils.writeJar;

import org.junit.After;
import org.junit.Test;

/**
 * Tests that the {@code groupId:artifactId} of a Maven-built .jar is read from
 * the file and recorded on the update site.
 *
 * @author Curtis Rueden
 */
public class CoordinateTest {

	private FilesCollection files;

	@After
	public void after() {
		if (files != null) cleanup(files);
	}

	/** The coordinate comes from the {@code META-INF/maven/} path. */
	@Test
	public void testCoordinateFromJar() throws Exception {
		final FileObject file = checksum("jars/annotations-13.0.jar",
			"META-INF/maven/org.jetbrains/annotations/pom.xml", "<project/>");
		assertEquals("org.jetbrains:annotations", file.getLocalCoordinate());
		// Nothing has been uploaded, so the site knows no coordinate yet.
		assertNull(file.getCoordinate());
	}

	/** A .jar that is not Maven-built has no coordinate at all. */
	@Test
	public void testNoCoordinateWithoutMavenMetadata() throws Exception {
		final FileObject file = checksum("jars/hand-built.jar", "Whatever.class",
			"not really a class");
		assertNull(file.getLocalCoordinate());
	}

	/**
	 * A shaded .jar carries the coordinates of everything it absorbed, so the
	 * one it is known by cannot be told from the ones it contains.
	 */
	@Test
	public void testShadedJarHasNoCoordinate() throws Exception {
		final FileObject file = checksum("jars/shaded-1.0.0.jar",
			"META-INF/maven/org.example/shaded/pom.xml", "<project/>",
			"META-INF/maven/com.google.guava/guava/pom.xml", "<project/>");
		assertNull(file.getLocalCoordinate());
	}

	/** Uploading adopts the local coordinate, and the index records it. */
	@Test
	public void testCoordinateSurvivesUpload() throws Exception {
		final FileObject file = checksum("jars/annotations-13.0.jar",
			"META-INF/maven/org.jetbrains/annotations/pom.xml", "<project/>");
		file.stageForUpload(files, FilesCollection.DEFAULT_UPDATE_SITE);
		assertEquals("org.jetbrains:annotations", file.getCoordinate());
		upload(files);

		final FilesCollection reread = readDb(files);
		assertEquals("org.jetbrains:annotations", //
			reread.get("jars/annotations.jar").getCoordinate());
	}

	/**
	 * Checksums a .jar written from the given entry name/content pairs.
	 *
	 * @param path where to write the .jar
	 * @param args the .jar's entries, as name/content pairs
	 * @return the file object the checksummer recorded for it
	 */
	private FileObject checksum(final String path, final String... args)
		throws Exception
	{
		files = initialize();
		writeJar(files, path, args);
		// NB: re-reading the database checksums the local files, the .jar
		// included, which is what fills in the coordinate.
		files = readDb(files);
		final FileObject file = files.get(path);
		assertTrue(path + " was not checksummed", file != null);
		return file;
	}
}
