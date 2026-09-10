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
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * Tests {@link ChannelManifest}.
 *
 * @author Curtis Rueden
 */
public class ChannelManifestTest {

	private static String render(final ChannelManifest manifest) {
		return new String(manifest.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
	}

	/** A site with no manifest has told us nothing, which is not the same as
	 * telling us it carries no channels. */
	@Test
	public void testAbsent() {
		final ChannelManifest absent = ChannelManifest.absent();
		assertFalse(absent.isPresent());
		assertTrue(absent.channels().isEmpty());
	}

	/** The base channel is served by every site, listed or not. */
	@Test
	public void testBaseChannelIsAlwaysCarried() {
		assertTrue(ChannelManifest.absent().carries(null));
		assertTrue(ChannelManifest.of(Arrays.asList("A.punctulata")).carries(null));
	}

	@Test
	public void testCarries() {
		final ChannelManifest manifest =
			ChannelManifest.of(Arrays.asList("B.floridae", "A.punctulata"));
		assertTrue(manifest.carries("A.punctulata"));
		assertTrue(manifest.carries("B.floridae"));
		assertFalse(manifest.carries("C.elegans"));
	}

	@Test
	public void testWithAddsNewestFirst() {
		final ChannelManifest manifest =
			ChannelManifest.of(Arrays.asList("A.punctulata")).with("B.floridae");
		assertEquals(Arrays.asList("B.floridae", "A.punctulata"),
			manifest.channels());
	}

	@Test
	public void testWithIsIdempotent() {
		final ChannelManifest once =
			ChannelManifest.of(Arrays.asList("A.punctulata")).with("A.punctulata");
		assertEquals(Arrays.asList("A.punctulata"), once.channels());
	}

	/** The base channel is never listed; it is the implicit last resort. */
	@Test
	public void testWithBaseChannelChangesNothing() {
		final ChannelManifest manifest =
			ChannelManifest.of(Arrays.asList("A.punctulata")).with(null);
		assertEquals(Arrays.asList("A.punctulata"), manifest.channels());
	}

	/** Publishing to a channel makes an absent manifest exist. */
	@Test
	public void testWithOnAbsentManifest() {
		final ChannelManifest manifest =
			ChannelManifest.absent().with("A.punctulata");
		assertTrue(manifest.isPresent());
		assertEquals(Arrays.asList("A.punctulata"), manifest.channels());
	}

	/**
	 * A site may serve channels this updater has never heard of, published by a
	 * newer updater than this one. Dropping them because we do not recognize
	 * them would make the site look abandoned to everyone reading them.
	 */
	@Test
	public void testUnknownChannelsSurvive() {
		final ChannelManifest manifest = ChannelManifest
			.of(Arrays.asList("Z.mays", "Y.lipolytica")).with("A.punctulata");
		assertEquals(Arrays.asList("A.punctulata", "Z.mays", "Y.lipolytica"),
			manifest.channels());
	}

	@Test
	public void testRenderedFormat() {
		final String text =
			render(ChannelManifest.of(Arrays.asList("B.floridae", "A.punctulata")));
		assertTrue(text, text.contains("\nB.floridae\nA.punctulata\n"));
		assertTrue("should be commented for whoever opens it",
			text.startsWith("#"));
	}

	@Test
	public void testEmptyManifestRendersOnlyComments() {
		final String text = render(ChannelManifest.of(Collections.<String> emptyList()));
		for (final String line : text.split("\n")) {
			assertTrue(line, line.isEmpty() || line.startsWith("#"));
		}
	}
}
