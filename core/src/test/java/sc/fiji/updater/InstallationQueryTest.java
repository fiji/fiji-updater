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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.initialize;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Test;

/**
 * Tests the read-only questions callers outside the updater ask about an
 * installation: which update sites it follows, and whether it follows a given
 * one.
 *
 * @author Curtis Rueden
 */
public class InstallationQueryTest {

	private FilesCollection files;

	@After
	public void after() {
		if (files != null) cleanup(files);
	}

	@Test
	public void testActiveSitesAreNamed() throws Exception {
		final File appRoot = installationFollowing("Groovy");

		assertTrue(FilesCollection.activeUpdateSites(appRoot).contains("Groovy"));
		assertTrue(FilesCollection.isUpdateSiteActive("Groovy", appRoot));
	}

	/** A site the installation does not follow at all. */
	@Test
	public void testUnknownSiteIsNotActive() throws Exception {
		final File appRoot = installationFollowing("Groovy");

		assertFalse(FilesCollection.activeUpdateSites(appRoot).contains("sciview"));
		assertFalse(FilesCollection.isUpdateSiteActive("sciview", appRoot));
	}

	/** A disabled site is one the installation has, but does not follow. */
	@Test
	public void testDisabledSiteIsNotActive() throws Exception {
		final File appRoot = installationFollowing("Groovy");
		files.getUpdateSite("Groovy", true).setActive(false);
		files.write();

		assertFalse(FilesCollection.activeUpdateSites(appRoot).contains("Groovy"));
		assertFalse(FilesCollection.isUpdateSiteActive("Groovy", appRoot));
	}

	/**
	 * The answer follows the installation rather than a snapshot of it: this is
	 * what the cache in the old {@code UpdateService} got wrong, since the user
	 * unsubscribing is exactly the event its callers wanted to hear about.
	 */
	@Test
	public void testAnswerFollowsLaterChanges() throws Exception {
		final File appRoot = installationFollowing("Groovy");
		assertTrue(FilesCollection.isUpdateSiteActive("Groovy", appRoot));

		files.removeUpdateSite("Groovy");
		files.write();

		assertFalse(FilesCollection.isUpdateSiteActive("Groovy", appRoot));
	}

	/** An installation with no index yet answers rather than throwing. */
	@Test
	public void testInstallationWithoutIndex() throws Exception {
		final File appRoot = installationFollowing("Groovy");
		assertTrue(new File(appRoot, "db.xml.gz").delete());

		assertFalse(FilesCollection.isUpdateSiteActive("Groovy", appRoot));
	}

	/** A malformed index is reported, never mistaken for an empty one. */
	@Test(expected = IOException.class)
	public void testMalformedIndexIsReported() throws Exception {
		final File appRoot = installationFollowing("Groovy");
		UpdaterTestUtils.writeFile(new File(appRoot, "db.xml.gz"), "not gzipped XML");

		FilesCollection.activeUpdateSites(appRoot);
	}

	/** An installation following one extra update site, written to disk. */
	private File installationFollowing(final String name) throws Exception {
		files = initialize();
		files.addUpdateSite(name, "https://sites.imagej.net/" + name + "/", //
			null, null, 0);
		files.write();
		return files.getAppRoot();
	}
}
