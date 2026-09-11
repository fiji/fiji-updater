/*-
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
package sc.fiji.updater.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The update channels this updater knows about, and the order in which to try
 * them.
 * <p>
 * A channel partitions an update site: where a site once served a single
 * {@code db.xml.gz} at its root, it may now also serve one per channel, in a
 * subdirectory named for the channel. An installation follows exactly one
 * channel -- see {@link ChannelState} -- and updates within it. Moving to a
 * newer channel is an upgrade: deliberate, prompted, and never automatic.
 * </p>
 * <h2>Resolution</h2>
 * <p>
 * A site is resolved by trying its candidate indexes in order and using the
 * first that reads. There is no separate probe: in the common case the first
 * candidate is the installation's own channel and it succeeds, so resolution
 * costs exactly the one request the updater was going to make anyway.
 * </p>
 * <p>
 * Two rules shape {@link #candidates}, and both matter:
 * </p>
 * <ul>
 * <li><b>Never resolve above the installation's channel.</b> A site publishing
 * for a newer channel than this installation follows must not be picked up:
 * that would be an upgrade, applied silently, without consent, from a
 * third-party site. Newer channels are how an upgrade is <em>detected</em>, not
 * how one is performed.</li>
 * <li><b>Fall back downward, ending at the base channel.</b> Most sites will
 * not have adopted channels at all, and their content is served from the site
 * root exactly as before. Falling back is what lets an installation on a
 * current channel keep using the hundreds of sites that have never heard of
 * one.</li>
 * </ul>
 * <p>
 * The core update site is the exception, and gets {@link #coreCandidates}
 * instead: it does not fall back at all.
 * </p>
 *
 * @author Curtis Rueden
 * @see ChannelState
 */
public final class Channels {

	private Channels() {
		// NB: prevent instantiation of utility class
	}

	/**
	 * Whether any channel exists at all.
	 * <p>
	 * While this is false, an installation whose channel cannot be determined is
	 * indistinguishable from one on the base channel, because the base channel is
	 * the only one there is. Once it is true, that is no longer so, and guessing
	 * becomes dangerous; see {@link ChannelState}.
	 * </p>
	 *
	 * @param known the channels in existence, newest first.
	 */
	public static boolean anyExist(final List<String> known) {
		return known != null && !known.isEmpty();
	}

	/**
	 * The newest channel, or null if none exists.
	 *
	 * @param known the channels in existence, newest first.
	 */
	public static String newest(final List<String> known) {
		return anyExist(known) ? known.get(0) : null;
	}

	/**
	 * The channels to try for an installation following the given channel, in
	 * order, ending with the base channel.
	 * <p>
	 * The base channel appears as null, which is also what an installation on the
	 * base channel is given: a single-element list containing only null, i.e.
	 * exactly the pre-channel behavior.
	 * </p>
	 *
	 * @param current the installation's channel, or null for the base channel.
	 * @return the candidate channels, newest first, always ending in null.
	 */
	public static List<String> candidates(final List<String> known,
		final String current)
	{
		final List<String> candidates = new ArrayList<>();
		if (current != null) {
			candidates.add(current);
			// Everything below the installation's own channel, in order. A channel
			// this updater has never heard of yields no intermediate steps -- we
			// cannot order what we do not know -- leaving the declared channel and
			// then the base, which is the oldest thing any site serves.
			boolean below = false;
			for (final String channel : known) {
				if (below) candidates.add(channel);
				else if (channel.equals(current)) below = true;
			}
		}
		candidates.add(null);
		return Collections.unmodifiableList(candidates);
	}

	/**
	 * The channels to try for the <em>core</em> update site: exactly one, the
	 * installation's own.
	 * <p>
	 * Falling back means something different for the core site than for anyone
	 * else. A third-party site that has not adopted this channel is served from
	 * its root, which is simply what it publishes for everyone -- reading it
	 * there is the whole point of the fallback, and the content is the content
	 * its maintainer stands behind. The core site's root is not that. It is the
	 * previous edition of the application, so falling back to it would replace a
	 * current installation with an older one: a downgrade of the application
	 * itself, performed silently, in the course of what the user asked to be an
	 * update.
	 * </p>
	 * <p>
	 * So the core site resolves to the installation's channel or to nothing. The
	 * failure is loud by construction -- no index read means no files offered,
	 * and the caller says why -- which is the correct outcome, because a core
	 * site not serving a channel its own installations follow is a fault in the
	 * update site, not a condition for a client to paper over.
	 * </p>
	 *
	 * @param current the installation's channel, or null for the base channel.
	 * @return a single-element list containing that channel.
	 */
	public static List<String> coreCandidates(final String current) {
		return Collections.singletonList(current);
	}

	/**
	 * Whether the given channel is newer than the installation's, and therefore
	 * represents an available upgrade rather than something to resolve against.
	 *
	 * @param channel the channel to consider; null is the base channel.
	 * @param current the installation's channel; null is the base channel.
	 */
	public static boolean isNewerThan(final List<String> known,
		final String channel, final String current)
	{
		if (channel == null) return false; // base is the oldest there is
		if (current == null) return true; // anything named beats the base
		if (channel.equals(current)) return false;
		for (final String candidate : known) {
			// The list is newest first, so whichever we meet first is the newer one.
			if (candidate.equals(channel)) return true;
			if (candidate.equals(current)) return false;
		}
		return false; // neither known; no basis to call it an upgrade
	}
}
