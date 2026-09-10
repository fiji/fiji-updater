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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests {@link UpdateSiteNetwork}'s recognition of mirrors and of the main
 * update site.
 *
 * @author Curtis Rueden
 */
public class UpdateSiteNetworkTest {

	@Test
	public void testKnownMirrorsAreRecognized() {
		assertTrue(UpdateSiteNetwork.isMirror(
			"https://downloads.micron.ox.ac.uk/fiji_update/mirrors/sites-fiji/"));
		assertTrue(UpdateSiteNetwork.isMirror(
			"https://downloads.micron.ox.ac.uk/fiji_update/mirrors/imagej/"));
		assertTrue(UpdateSiteNetwork.isMirror(
			"https://mirrors.pasteur.fr/fiji/sites/Fiji/"));
	}

	/**
	 * A mirror operator may host update sites of its own, which are not mirrors
	 * of anything. SIMcheck lives on the same host as the Europe mirrors but
	 * outside their tree, so matching on the host alone gets it wrong -- and
	 * getting it wrong means never recommending a URL correction for that site.
	 */
	@Test
	public void testSiteHostedAlongsideMirrorsIsNotAMirror() {
		assertFalse(UpdateSiteNetwork.isMirror(
			"https://downloads.micron.ox.ac.uk/fiji_update/SIMcheck/"));
	}

	@Test
	public void testCanonicalSitesAreNotMirrors() {
		assertFalse(UpdateSiteNetwork.isMirror("https://sites.imagej.net/Fiji/"));
		assertFalse(UpdateSiteNetwork.isMirror("https://sites.imagej.net/MoBIE/"));
		assertFalse(UpdateSiteNetwork.isMirror("https://update.fiji.sc/"));
		assertFalse(UpdateSiteNetwork.isMirror(null));
	}

	/**
	 * The main site is the main site whichever source it is read from. A user
	 * who has chosen a mirror is already following it, and must not be told to
	 * migrate onto the site they are already on.
	 */
	@Test
	public void testMainSiteByAnySource() {
		assertTrue(UpdateSiteNetwork.isMainSite("https://sites.imagej.net/Fiji/"));
		assertTrue(UpdateSiteNetwork.isMainSite("https://sites.imagej.net/Fiji"));
		for (final String mirror : UpdateSiteNetwork.MAIN_SITE_MIRRORS) {
			assertTrue(mirror, UpdateSiteNetwork.isMainSite(mirror));
		}
	}

	@Test
	public void testOtherSitesAreNotTheMainSite() {
		assertFalse(UpdateSiteNetwork.isMainSite("https://update.fiji.sc/"));
		assertFalse(UpdateSiteNetwork.isMainSite("https://update.imagej.net/"));
		assertFalse(UpdateSiteNetwork.isMainSite("https://sites.imagej.net/MoBIE/"));
		assertFalse(UpdateSiteNetwork.isMainSite(
			"https://downloads.micron.ox.ac.uk/fiji_update/mirrors/imagej/"));
		assertFalse(UpdateSiteNetwork.isMainSite(null));
	}

	/** Every declared main-site mirror must also register as a mirror. */
	@Test
	public void testMainSiteMirrorsAreMirrors() {
		for (final String mirror : UpdateSiteNetwork.MAIN_SITE_MIRRORS) {
			assertTrue(mirror, UpdateSiteNetwork.isMirror(mirror));
		}
	}

	/**
	 * The case the URL half of the core-site test exists for: the name has been
	 * retired, so this updater's constant and the name in an existing
	 * installation's local index no longer agree. The URL still does.
	 * <p>
	 * Getting this wrong is not cosmetic. A site not recognized as core is
	 * treated as third-party, and a third-party site is allowed to fall back to
	 * its root -- which for the core site is the previous edition of the
	 * application.
	 * </p>
	 */
	@Test
	public void testCoreSiteIsRecognizedByURLWhateverItIsCalled() {
		final String url = "https://" + UpdateSiteNetwork.MAIN_SITE_PATH;
		assertTrue(UpdateSiteNetwork.isCoreSite("Fiji-Latest", url));
		assertTrue(UpdateSiteNetwork.isCoreSite("Fiji", url));
		assertTrue(UpdateSiteNetwork.isCoreSite("whatever it was renamed to", url));
	}

	/** A mirror is a different source for the same content, not another site. */
	@Test
	public void testMirrorsOfTheCoreSiteAreCore() {
		for (final String mirror : UpdateSiteNetwork.MAIN_SITE_MIRRORS) {
			assertTrue(mirror, UpdateSiteNetwork.isCoreSite("Some-Name", mirror));
		}
	}

	/**
	 * The name is what is left for an installation whose main site is served
	 * from neither the canonical host nor a known mirror: a local mirror, a test
	 * fixture, an air-gapped deployment.
	 */
	@Test
	public void testNameIdentifiesACoreSiteServedFromElsewhere() {
		assertTrue(UpdateSiteNetwork.isCoreSite(UpdateSiteNetwork.MAIN_SITE_NAME,
			"file:/tmp/some-web-root/"));
	}

	/** An ordinary third-party site is neither. */
	@Test
	public void testThirdPartySiteIsNotCore() {
		assertFalse(UpdateSiteNetwork.isCoreSite("SIMcheck",
			"https://downloads.micron.ox.ac.uk/fiji_update/SIMcheck/"));
		assertFalse(UpdateSiteNetwork.isCoreSite(null, null));
	}
}
