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
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.initialize;

import org.junit.After;
import org.junit.Test;


/**
 * Tests that an installation reads its update sites from the source it chose,
 * while still recording where those sites canonically live.
 *
 * @author Curtis Rueden
 */
public class MirrorTest {

	private static final String PASTEUR = "https://mirrors.pasteur.fr/fiji/sites/";

	private FilesCollection files;

	@After
	public void after() {
		if (files != null) cleanup(files);
	}

	/** With no mirror chosen, a site is read from the site. */
	@Test
	public void testCanonicalByDefault() throws Exception {
		files = initialize();
		final UpdateSite site = site("Thing", "https://sites.fiji.sc/Thing/");

		assertNull(files.getMirror());
		assertEquals("https://sites.fiji.sc/Thing/", files.sourceURL(site));
		assertEquals("https://sites.fiji.sc/Thing/db.xml.gz", files.indexURL(site));
	}

	/** One choice covers every site the mirror carries. */
	@Test
	public void testTheMirrorCoversEverySiteItCarries() throws Exception {
		files = initialize();
		final UpdateSite one = site("One", "https://sites.fiji.sc/One/");
		final UpdateSite two = site("Two", "https://sites.fiji.sc/Two/");
		files.setMirror(PASTEUR);

		assertEquals(PASTEUR + "One/", files.sourceURL(one));
		assertEquals(PASTEUR + "Two/", files.sourceURL(two));
		assertEquals(PASTEUR + "Two/db.xml.gz", files.indexURL(two));
	}

	/** A site the mirror does not carry is read from the site. */
	@Test
	public void testSitesTheMirrorDoesNotCarry() throws Exception {
		files = initialize();
		final UpdateSite elsewhere = site("Elsewhere", "https://updates.example.org/thing/");
		files.setMirror(PASTEUR);

		assertEquals("https://updates.example.org/thing/", files.sourceURL(elsewhere));
	}

	/** A site served over plain HTTP is still read over plain HTTP. */
	@Test
	public void testAnInsecureSiteIsNotUpgradedByFetching() throws Exception {
		files = initialize();
		final UpdateSite insecure = site("Insecure", "http://updates.example.org/thing/");
		files.setMirror(PASTEUR);

		assertEquals("http://updates.example.org/thing/", files.sourceURL(insecure));
	}

	/** The site's own URL is what an upload addresses, mirror or no mirror. */
	@Test
	public void testUploadsAddressTheSite() throws Exception {
		files = initialize();
		final UpdateSite site = site("Thing", "https://sites.fiji.sc/Thing/");
		files.setMirror(PASTEUR);

		assertEquals("https://sites.fiji.sc/Thing/db.xml.gz", site.getIndexURL());
	}

	/** The channel is a subdirectory of wherever the site is read from. */
	@Test
	public void testChannelURLFollowsTheSource() throws Exception {
		files = initialize();
		final UpdateSite site = site("Thing", "https://sites.fiji.sc/Thing/");
		site.setChannel("A.punctulata");
		files.setMirror(PASTEUR);

		assertEquals(PASTEUR + "Thing/A.punctulata/", files.channelURL(site));
		assertEquals(PASTEUR + "Thing/A.punctulata/db.xml.gz", files.indexURL(site));
	}

	/** The choice is the installation's, so it survives a restart. */
	@Test
	public void testTheChoiceIsRemembered() throws Exception {
		files = initialize();
		site("Thing", "https://sites.fiji.sc/Thing/");
		files.setMirror(PASTEUR);
		files.write();

		final FilesCollection reread = new FilesCollection(files.getAppRoot());
		reread.read();
		assertEquals(PASTEUR, reread.getMirror());
		assertEquals(PASTEUR + "Thing/",
			reread.sourceURL(reread.getUpdateSite("Thing", true)));
	}

	/** Only known mirrors can be chosen, so a typo cannot redirect a fetch. */
	@Test(expected = IllegalArgumentException.class)
	public void testAnUnknownMirrorIsRefused() throws Exception {
		files = initialize();
		files.setMirror("https://mirror.example.org/fiji/");
	}

	private UpdateSite site(final String name, final String url) {
		return files.addUpdateSite(name, url, null, null, 0);
	}

}
