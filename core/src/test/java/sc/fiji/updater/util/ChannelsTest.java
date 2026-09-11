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
package sc.fiji.updater.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

	/** No channel exists yet, which is the world every installation is in today. */
	private static final List<String> NONE = Collections.emptyList();

	private static final List<String> THREE =
		Arrays.asList("C.elegans", "B.floridae", "A.punctulata");


	/**
	 * With no channel, there is one candidate and it is the base -- byte for
	 * byte the behavior of every updater released before channels existed.
	 */
	@Test
	public void testBaseChannelHasOnlyBaseCandidate() {
		assertEquals(Arrays.asList((String) null), Channels.candidates(NONE, null));
	}

	/** Candidates always end at the base channel, which every site serves. */
	@Test
	public void testCandidatesEndAtBase() {
		final List<String> candidates = Channels.candidates(NONE, "A.punctulata");
		assertEquals("A.punctulata", candidates.get(0));
		assertEquals(null, candidates.get(candidates.size() - 1));
	}

	/**
	 * A channel this updater has never heard of still gets tried first. We
	 * cannot order what we do not know, so the only fallback is the base.
	 */
	@Test
	public void testUnknownChannelFallsBackToBaseOnly() {
		assertEquals(Arrays.asList("Z.mays", null), Channels.candidates(NONE, "Z.mays"));
	}

	/** The installation's own channel is always tried before anything else. */
	@Test
	public void testCurrentChannelIsAlwaysFirst() {
		for (final String current : new String[] { "A.punctulata", "B.floridae" }) {
			assertEquals(current, Channels.candidates(NONE, current).get(0));
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
			for (final String candidate : Channels.candidates(NONE, current)) {
				assertFalse("candidate " + candidate + " is newer than " + current,
					Channels.isNewerThan(NONE, candidate, current));
			}
		}
	}

	/**
	 * The core site gets one candidate and no fallback. Falling back means
	 * something different for it than for anyone else: a third-party site's root
	 * is what its maintainer publishes for everyone, while the core site's root
	 * is the previous edition of the application.
	 */
	@Test
	public void testCoreCandidatesDoNotFallBack() {
		assertEquals(Arrays.asList("A.punctulata"),
			Channels.coreCandidates("A.punctulata"));
		assertEquals(Arrays.asList("B.floridae"),
			Channels.coreCandidates("B.floridae"));
	}

	/**
	 * For an installation on the base channel the root <em>is</em> its channel,
	 * so the core site reads it. The rule is "never below your own channel", not
	 * "never the root".
	 */
	@Test
	public void testCoreCandidatesOnBaseChannelAreTheBase() {
		assertEquals(Arrays.asList((String) null), Channels.coreCandidates(null));
	}

	/** The core site never resolves below the installation's channel either. */
	@Test
	public void testCoreCandidatesNeverGoBelowCurrent() {
		for (final String current : new String[] { null, "A.punctulata",
			"C.elegans" })
		{
			for (final String candidate : Channels.coreCandidates(current)) {
				assertEquals(current, candidate);
				assertFalse(Channels.isNewerThan(THREE, candidate, current));
			}
		}
	}

	@Test
	public void testIsNewerThan() {
		// The base channel is the oldest thing there is.
		assertFalse(Channels.isNewerThan(NONE, null, null));
		assertFalse(Channels.isNewerThan(NONE, null, "A.punctulata"));
		assertTrue(Channels.isNewerThan(NONE, "A.punctulata", null));

		// A channel is not newer than itself.
		assertFalse(Channels.isNewerThan(NONE, "A.punctulata", "A.punctulata"));
	}

	@Test
	public void testAnyExist() {
		assertTrue(Channels.anyExist(THREE));
		assertFalse(Channels.anyExist(NONE));
		assertFalse(Channels.anyExist(null));
	}

	@Test
	public void testNewest() {
		assertEquals("C.elegans", Channels.newest(THREE));
		assertNull(Channels.newest(NONE));
		assertNull(Channels.newest(null));
	}

	/** From the newest channel, every older one is a fallback, base last. */
	@Test
	public void testFallbackWalksDownward() {
		assertEquals(Arrays.asList("C.elegans", "B.floridae", "A.punctulata", null),
			Channels.candidates(THREE, "C.elegans"));
	}

	/** From the middle, only what is below it -- never C.elegans. */
	@Test
	public void testFallbackNeverGoesUp() {
		assertEquals(Arrays.asList("B.floridae", "A.punctulata", null),
			Channels.candidates(THREE, "B.floridae"));
	}

	/** The oldest named channel falls back only to the base. */
	@Test
	public void testOldestChannelFallsBackToBase() {
		assertEquals(Arrays.asList("A.punctulata", null),
			Channels.candidates(THREE, "A.punctulata"));
	}

	/**
	 * An installation on the base channel is offered nothing else, even with
	 * three channels published. Moving up is an upgrade, and upgrades are opt-in.
	 */
	@Test
	public void testBaseChannelStaysOnBase() {
		assertEquals(Arrays.asList((String) null), Channels.candidates(THREE, null));
	}

	@Test
	public void testIsNewerThanWithKnownChannels() {
		assertTrue(Channels.isNewerThan(THREE, "C.elegans", "A.punctulata"));
		assertTrue(Channels.isNewerThan(THREE, "B.floridae", "A.punctulata"));
		assertFalse(Channels.isNewerThan(THREE, "A.punctulata", "C.elegans"));
		assertFalse(Channels.isNewerThan(THREE, "B.floridae", "B.floridae"));
	}
}
