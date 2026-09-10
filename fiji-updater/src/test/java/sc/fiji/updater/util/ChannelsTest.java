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
import java.util.List;

import org.junit.After;
import org.junit.Test;

/**
 * Tests {@link Channels}' candidate ordering.
 * <p>
 * Most of these install a channel list of their own, since the embedded one is
 * empty until the first channel is minted and the ordering rules are exactly
 * what needs to work when it is not.
 * </p>
 *
 * @author Curtis Rueden
 */
public class ChannelsTest {

	/** Newest first, as the core site publishes them. */
	private static final List<String> THREE =
		Arrays.asList("C.elegans", "B.floridae", "A.punctulata");

	@After
	public void resetChannels() {
		Channels.setKnown(null);
	}

	/**
	 * With no channel, there is one candidate and it is the base -- byte for
	 * byte the behavior of every updater released before channels existed.
	 */
	@Test
	public void testBaseChannelHasOnlyBaseCandidate() {
		assertEquals(Arrays.asList((String) null), Channels.candidates(null));
	}

	/** Candidates always end at the base channel, which every site serves. */
	@Test
	public void testCandidatesEndAtBase() {
		final List<String> candidates = Channels.candidates("A.punctulata");
		assertEquals("A.punctulata", candidates.get(0));
		assertEquals(null, candidates.get(candidates.size() - 1));
	}

	/**
	 * A channel this updater has never heard of still gets tried first. We
	 * cannot order what we do not know, so the only fallback is the base.
	 */
	@Test
	public void testUnknownChannelFallsBackToBaseOnly() {
		assertEquals(Arrays.asList("Z.mays", null), Channels.candidates("Z.mays"));
	}

	/** The installation's own channel is always tried before anything else. */
	@Test
	public void testCurrentChannelIsAlwaysFirst() {
		for (final String current : new String[] { "A.punctulata", "B.floridae" }) {
			assertEquals(current, Channels.candidates(current).get(0));
		}
	}

	/**
	 * No candidate is ever newer than the installation's channel. A site that
	 * has published for a newer channel must not be picked up: that would be an
	 * upgrade, performed silently and without consent.
	 */
	@Test
	public void testNeverResolvesAboveCurrentChannel() {
		for (final String current : new String[] { null, "A.punctulata" }) {
			for (final String candidate : Channels.candidates(current)) {
				assertFalse("candidate " + candidate + " is newer than " + current,
					Channels.isNewerThan(candidate, current));
			}
		}
	}

	@Test
	public void testIsNewerThan() {
		// The base channel is the oldest thing there is.
		assertFalse(Channels.isNewerThan(null, null));
		assertFalse(Channels.isNewerThan(null, "A.punctulata"));
		assertTrue(Channels.isNewerThan("A.punctulata", null));

		// A channel is not newer than itself.
		assertFalse(Channels.isNewerThan("A.punctulata", "A.punctulata"));
	}

	/** No channels exist yet, so nothing can be unknown in a dangerous way. */
	@Test
	public void testNoneExistByDefault() {
		assertFalse(Channels.anyExist());
		assertTrue(Channels.known().isEmpty());
	}

	@Test
	public void testSetKnown() {
		Channels.setKnown(THREE);
		assertTrue(Channels.anyExist());
		assertEquals(THREE, Channels.known());

		// Null or empty restores the compiled-in fallback.
		Channels.setKnown(null);
		assertEquals(Channels.EMBEDDED, Channels.known());
	}

	/** From the newest channel, every older one is a fallback, base last. */
	@Test
	public void testFallbackWalksDownward() {
		Channels.setKnown(THREE);
		assertEquals(Arrays.asList("C.elegans", "B.floridae", "A.punctulata", null),
			Channels.candidates("C.elegans"));
	}

	/** From the middle, only what is below it -- never C.elegans. */
	@Test
	public void testFallbackNeverGoesUp() {
		Channels.setKnown(THREE);
		assertEquals(Arrays.asList("B.floridae", "A.punctulata", null),
			Channels.candidates("B.floridae"));
	}

	/** The oldest named channel falls back only to the base. */
	@Test
	public void testOldestChannelFallsBackToBase() {
		Channels.setKnown(THREE);
		assertEquals(Arrays.asList("A.punctulata", null),
			Channels.candidates("A.punctulata"));
	}

	/**
	 * An installation on the base channel is offered nothing else, even with
	 * three channels published. Moving up is an upgrade, and upgrades are opt-in.
	 */
	@Test
	public void testBaseChannelStaysOnBase() {
		Channels.setKnown(THREE);
		assertEquals(Arrays.asList((String) null), Channels.candidates(null));
	}

	@Test
	public void testIsNewerThanWithKnownChannels() {
		Channels.setKnown(THREE);
		assertTrue(Channels.isNewerThan("C.elegans", "A.punctulata"));
		assertTrue(Channels.isNewerThan("B.floridae", "A.punctulata"));
		assertFalse(Channels.isNewerThan("A.punctulata", "C.elegans"));
		assertFalse(Channels.isNewerThan("B.floridae", "B.floridae"));
	}
}
