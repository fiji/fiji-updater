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
import static org.junit.Assert.assertNull;

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
