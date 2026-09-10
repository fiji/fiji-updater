package sc.fiji.updater.util;

import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * @author Curtis Rueden
 * @see ChannelState
 */
public final class Channels {

	private Channels() {
		// NB: prevent instantiation of utility class
	}

	/**
	 * The channels compiled into this updater, newest first.
	 * <p>
	 * The base channel is not listed: it is the implicit last resort, and is
	 * represented throughout as a null channel name.
	 * </p>
	 * <p>
	 * This is a fallback for when the authoritative list -- published by the core
	 * update site -- cannot be fetched. It is deliberately empty until the first
	 * channel is minted; with no channels in existence, every installation is on
	 * the base channel and resolution is exactly what it always was.
	 * </p>
	 */
	public static final List<String> EMBEDDED =
		Collections.unmodifiableList(Arrays.<String> asList());

	private static volatile List<String> known = EMBEDDED;

	/**
	 * The channels this updater currently knows about, newest first.
	 * <p>
	 * Mutable because the list is meant to be discovered rather than compiled in:
	 * an updater can only ever have been built before the channels that come
	 * after it, so a fixed list would dead-end every installation whose updater
	 * predates the next codename. The core update site publishes the
	 * authoritative, ordered list, and {@link #setKnown} replaces the embedded
	 * fallback with it.
	 * </p>
	 */
	public static List<String> known() {
		return known;
	}

	/**
	 * Replaces the known channel list, as published by the core update site.
	 *
	 * @param channels the channels, newest first; null or empty restores the
	 *          {@link #EMBEDDED} fallback.
	 */
	public static void setKnown(final List<String> channels) {
		known = channels == null || channels.isEmpty() ? EMBEDDED
			: Collections.unmodifiableList(new ArrayList<>(channels));
	}

	/**
	 * Whether any channel exists at all.
	 * <p>
	 * While this is false, an installation whose channel cannot be determined is
	 * indistinguishable from one on the base channel, because the base channel is
	 * the only one there is. Once it is true, that is no longer so, and guessing
	 * becomes dangerous; see {@link ChannelState}.
	 * </p>
	 */
	public static boolean anyExist() {
		return !known().isEmpty();
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
	public static List<String> candidates(final String current) {
		final List<String> candidates = new ArrayList<>();
		if (current != null) {
			candidates.add(current);
			// Everything below the installation's own channel, in order. A channel
			// this updater has never heard of yields no intermediate steps -- we
			// cannot order what we do not know -- leaving the declared channel and
			// then the base, which is the oldest thing any site serves.
			boolean below = false;
			for (final String channel : known()) {
				if (below) candidates.add(channel);
				else if (channel.equals(current)) below = true;
			}
		}
		candidates.add(null);
		return Collections.unmodifiableList(candidates);
	}

	/**
	 * Whether the given channel is newer than the installation's, and therefore
	 * represents an available upgrade rather than something to resolve against.
	 *
	 * @param channel the channel to consider; null is the base channel.
	 * @param current the installation's channel; null is the base channel.
	 */
	public static boolean isNewerThan(final String channel, final String current) {
		if (channel == null) return false; // base is the oldest there is
		if (current == null) return true; // anything named beats the base
		if (channel.equals(current)) return false;
		for (final String known : known()) {
			// The list is newest first, so whichever we meet first is the newer one.
			if (known.equals(channel)) return true;
			if (known.equals(current)) return false;
		}
		return false; // neither known; no basis to call it an upgrade
	}
}
