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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.initialize;

import java.util.Collections;

import org.junit.Test;

/**
 * Tests {@link UpdateSite}, in particular the construction of index URLs.
 *
 * @author Curtis Rueden
 */
public class UpdateSiteTest {

	private UpdateSite site(final String url) {
		return new UpdateSite("test", url, null, null, null, null, 0);
	}

	/**
	 * Plain HTTP is allowed, and named as such. The updater installs what a
	 * site serves, so both user interfaces say which sites cannot be
	 * authenticated -- but nothing refuses them, and a site on a local network
	 * may well have no certificate.
	 */
	@Test
	public void testInsecureSitesAreAllowedAndNamed() {
		assertTrue(site("http://updates.example.org/fiji/").isInsecure());
		assertFalse(site("https://sites.imagej.net/MoBIE/").isInsecure());
		// A file: URL is not served over the network at all.
		assertFalse(site("file:/mnt/share/site/").isInsecure());

		final UpdateSite insecure = site("http://updates.example.org/fiji/");
		assertEquals("test: http://updates.example.org/fiji/",
			UpdateSite.names(Collections.singletonList(insecure)));
	}

	/** Only the active sites are worth warning about. */
	@Test
	public void testInsecureSitesSkipsDisabledOnes() throws Exception {
		final FilesCollection files = initialize();
		files.addUpdateSite("insecure", "http://updates.example.org/fiji/", //
			null, null, 0);
		files.addUpdateSite("disabled", "http://disabled.example.org/fiji/", //
			null, null, 0);
		files.getUpdateSite("disabled", true).setActive(false);

		assertEquals(1, files.insecureSites().size());
		assertEquals("insecure", //
			files.insecureSites().iterator().next().getName());
		cleanup(files);
	}

	/** A trailing slash is decoration, not identity. */
	@Test
	public void testSameURLIgnoresTrailingSlash() {
		assertTrue(UpdateSite.sameURL("https://sites.imagej.net/MoBIE",
			"https://sites.imagej.net/MoBIE/"));
	}

	/**
	 * An installation left alone since before HTTPS holds {@code http://} for a
	 * site now served over {@code https://}. It is the same site.
	 */
	@Test
	public void testSameURLIgnoresScheme() {
		assertTrue(UpdateSite.sameURL("http://sites.imagej.net/MoBIE/",
			"https://sites.imagej.net/MoBIE/"));
	}

	/** A mirror is a different source for the same site. */
	@Test
	public void testSameURLAcceptsAMirrorOfTheMainSite() {
		assertTrue(UpdateSite.sameURL("https://sites.imagej.net/Fiji/",
			"https://downloads.micron.ox.ac.uk/fiji_update/mirrors/sites-fiji/"));
	}

	/**
	 * A mirror carries whatever is under it, so a mirror of a site that is not
	 * the main one is recognized too. A hand-listed set of mirrored URLs could
	 * only ever reach the main site.
	 */
	@Test
	public void testSameURLAcceptsAMirrorOfAnyMirroredSite() {
		assertTrue(UpdateSite.sameURL("https://sites.imagej.net/MoBIE/",
			"https://mirrors.pasteur.fr/fiji/sites/MoBIE/"));
		assertTrue(UpdateSite.sameURL("https://sites.imagej.net/Fiji-Legacy/",
			"https://mirrors.pasteur.fr/fiji/sites/Fiji-Legacy/"));
	}

	/** Two sites read through the same mirror are still two sites. */
	@Test
	public void testSameURLRejectsTwoSitesBehindOneMirror() {
		assertFalse(UpdateSite.sameURL("https://mirrors.pasteur.fr/fiji/sites/MoBIE/",
			"https://mirrors.pasteur.fr/fiji/sites/Fiji-Legacy/"));
	}

	/**
	 * A mirror serving one site only is the same arrangement with nothing left
	 * over, so it resolves to that site and to nothing else.
	 */
	@Test
	public void testSingleSiteMirrorResolvesToItsSite() {
		assertEquals("https://sites.imagej.net/Fiji/", UpdateSite.canonicalURL(
			"https://downloads.micron.ox.ac.uk/fiji_update/mirrors/sites-fiji/"));
	}

	/** The URL as written is kept apart from the site it resolves to. */
	@Test
	public void testNormalizedURLDoesNotResolveMirrors() {
		final String mirror = "https://mirrors.pasteur.fr/fiji/sites/MoBIE/";
		assertEquals(mirror, UpdateSite.normalizedURL(mirror));
		assertEquals("https://sites.imagej.net/MoBIE/", UpdateSite.canonicalURL(mirror));
	}

	/**
	 * A mirror operator's own sites live alongside the mirrored tree, not
	 * inside it, so hosting a mirror does not make everything on that host one.
	 */
	@Test
	public void testSameURLRejectsUnrelatedSitesOnAMirrorHost() {
		assertFalse(UpdateSite.sameURL("https://sites.imagej.net/Fiji/",
			"https://downloads.micron.ox.ac.uk/fiji_update/SIMcheck/"));
	}

	/** A URL that has moved is rewritten before being compared. */
	@Test
	public void testSameURLFollowsObsoleteURLs() {
		assertTrue(UpdateSite.sameURL("http://fiji.sc/update/",
			"https://update.fiji.sc/"));
	}

	@Test
	public void testSameURLRejectsUnrelatedSites() {
		assertFalse(UpdateSite.sameURL("https://sites.imagej.net/MoBIE/",
			"https://sites.imagej.net/Fiji/"));
		assertFalse(UpdateSite.sameURL(null, "https://sites.imagej.net/Fiji/"));
	}

	/**
	 * With no channel, the index URL is exactly what the call sites used to
	 * build by hand. This is what makes the change to centralize them inert.
	 */
	@Test
	public void testBaseChannelIndexURL() {
		final UpdateSite site = site("https://sites.imagej.net/MoBIE/");
		assertNull(site.getChannel());
		assertEquals("https://sites.imagej.net/MoBIE/db.xml.gz", site.getIndexURL());
		assertEquals("https://sites.imagej.net/MoBIE/", site.getChannelURL());
	}

	/** A trailing slash is added to the site URL, so the join stays correct. */
	@Test
	public void testIndexURLWithoutTrailingSlash() {
		assertEquals("https://sites.imagej.net/MoBIE/db.xml.gz",
			site("https://sites.imagej.net/MoBIE").getIndexURL());
	}

	/** A channel is a subdirectory of the site, not a separate site. */
	@Test
	public void testChannelIndexURL() {
		final UpdateSite site = site("https://sites.imagej.net/MoBIE/");
		site.setChannel("A.punctulata");
		assertEquals("https://sites.imagej.net/MoBIE/A.punctulata/db.xml.gz",
			site.getIndexURL());
		assertEquals("https://sites.imagej.net/MoBIE/A.punctulata/",
			site.getChannelURL());
	}

	/**
	 * The site URL itself never changes with the channel. The list of update
	 * sites is matched by URL, so a channel that altered it would make every
	 * local site disagree with the official list.
	 */
	@Test
	public void testChannelDoesNotAffectSiteURL() {
		final UpdateSite site = site("https://sites.imagej.net/MoBIE/");
		final String before = site.getURL();
		site.setChannel("B.floridae");
		assertEquals(before, site.getURL());
	}

	/** Resolution state survives cloning, or a cloned collection re-resolves. */
	@Test
	public void testCloneKeepsChannel() {
		final UpdateSite site = site("https://sites.imagej.net/MoBIE/");
		site.setChannel("A.punctulata");
		assertEquals("A.punctulata", ((UpdateSite) site.clone()).getChannel());
	}
}
