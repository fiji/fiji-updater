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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import sc.fiji.updater.internal.UpdaterUtil;

/**
 * The channels an update site carries.
 * <p>
 * A site publishes this alongside its index, listing the channels it serves.
 * It is written by the upload path rather than by hand, so it appears on
 * exactly those sites that have published through a channel-aware updater, and
 * on no others. That is what keeps it meaningful: a site with a manifest has
 * engaged with channels, so the absence of <em>your</em> channel from it says
 * something, whereas a site with no manifest at all says nothing.
 * </p>
 * <h2>Format</h2>
 * <p>
 * One channel name per line, newest first, {@code #} comments and blank lines
 * ignored. Plain text rather than anything structured, for the same reason the
 * updater sheds dependencies generally: this file is read by the tool that
 * repairs an installation whose libraries disagree, and a list of names does
 * not justify a parser.
 * </p>
 * <p>
 * The base channel is never listed. It is the implicit last resort, served from
 * the site root, and every site has it whether or not it has heard of channels.
 * </p>
 *
 * @author Curtis Rueden
 * @see Channels
 */
public final class ChannelManifest {

	/** Name of the manifest, relative to the update site root. */
	public static final String FILENAME = "channels.txt";

	private static final String HEADER =
		"# Update channels served by this site, newest first.\n" +
		"# Written by the Fiji Updater when publishing; edit with care.\n";

	private final List<String> channels;
	private final boolean present;

	private ChannelManifest(final List<String> channels, final boolean present) {
		this.channels = Collections.unmodifiableList(channels);
		this.present = present;
	}

	/** A manifest that does not exist: the site has not engaged with channels. */
	public static ChannelManifest absent() {
		return new ChannelManifest(new ArrayList<>(), false);
	}

	/** A manifest listing exactly the given channels. */
	public static ChannelManifest of(final List<String> channels) {
		return new ChannelManifest(dedupe(channels), true);
	}

	/**
	 * Reads the manifest of the update site at the given URL.
	 * <p>
	 * A site with no manifest is the ordinary case and not an error, so any
	 * failure to read one yields {@link #absent()}. That deliberately conflates
	 * "no manifest" with "could not be fetched": both mean we have learned
	 * nothing about this site's channels, and neither justifies interrupting an
	 * update.
	 * </p>
	 *
	 * @param siteURL the update site's URL, ending in a slash.
	 */
	public static ChannelManifest read(final String siteURL) {
		try (final InputStream in =
			UpdaterUtil.openConnection(new URL(siteURL + FILENAME)).getInputStream();
				final BufferedReader reader = new BufferedReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			final List<String> channels = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				final int hash = line.indexOf('#');
				if (hash >= 0) line = line.substring(0, hash);
				line = line.trim();
				if (!line.isEmpty()) channels.add(line);
			}
			return of(channels);
		}
		catch (final IOException e) {
			return absent();
		}
	}

	/** Whether this site published a manifest at all. */
	public boolean isPresent() {
		return present;
	}

	/** The channels this site carries, newest first; never null. */
	public List<String> channels() {
		return channels;
	}

	/** Whether the site carries the given channel. The base channel always is. */
	public boolean carries(final String channel) {
		return channel == null || channels.contains(channel);
	}

	/**
	 * This manifest with the given channel added at the front if it is not
	 * already listed, leaving every other entry alone.
	 * <p>
	 * Preserving the rest matters: a site may serve channels this updater has
	 * never heard of, published by a newer updater than this one, and dropping
	 * them because we do not recognize them would make the site look abandoned to
	 * everyone reading those channels.
	 * </p>
	 *
	 * @param channel the channel to include; null is the base channel, which is
	 *          never listed, and yields this manifest unchanged.
	 */
	public ChannelManifest with(final String channel) {
		if (channel == null || channels.contains(channel)) {
			return present ? this : of(channels);
		}
		final List<String> updated = new ArrayList<>();
		updated.add(channel);
		updated.addAll(channels);
		return of(updated);
	}

	/** Renders the manifest for publication. */
	public byte[] toByteArray() {
		final StringBuilder sb = new StringBuilder(HEADER);
		for (final String channel : channels) {
			sb.append(channel).append('\n');
		}
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static List<String> dedupe(final List<String> channels) {
		final Set<String> seen = new LinkedHashSet<>();
		for (final String channel : channels) {
			if (channel != null && !channel.trim().isEmpty()) seen.add(channel.trim());
		}
		return new ArrayList<>(seen);
	}
}
