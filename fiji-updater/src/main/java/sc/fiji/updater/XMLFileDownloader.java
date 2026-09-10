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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;

import sc.fiji.updater.util.AbstractProgressable;
import sc.fiji.updater.util.ChannelManifest;
import sc.fiji.updater.util.ChannelState;
import sc.fiji.updater.util.Channels;
import sc.fiji.updater.util.UpdaterUtil;

/**
 * Directly in charge of downloading and saving start-up files (i.e.: XML file
 * and related).
 * 
 * @author Johannes Schindelin
 */
public class XMLFileDownloader extends AbstractProgressable {

	private final FilesCollection files;
	private final Collection<String> updateSites;
	private StringBuilder warnings;

	public XMLFileDownloader(final FilesCollection files) {
		this(files, files.getUpdateSiteNames(false));
	}

	public XMLFileDownloader(final FilesCollection files,
		final Collection<String> updateSites)
	{
		this.files = files;
		this.updateSites = updateSites;
	}

	public void start() {
		start(true);
	}


	public void start(boolean closeProgressAtEnd) {
		if (updateSites == null || updateSites.isEmpty()) return;

		// Refuse to resolve anything if we cannot tell which channel this
		// installation follows. Treating an undeterminable channel as the base one
		// would resolve every site to its oldest index and downgrade the whole
		// installation -- quietly, and in a way that looks like a successful
		// update. While no channel exists there is nothing to get wrong, so this
		// only bites once one does.
		final ChannelState channel = files.getChannelState();
		if (!channel.isKnown() && Channels.anyExist(files.getChannels())) {
			if (warnings == null) warnings = new StringBuilder();
			appendWarning("Cannot determine which update channel this " +
				"installation follows, so no update sites were checked.\n" +
				"This usually means the application was not started by its " +
				"launcher.\n" +
				"Run the updater from the launcher, or name the channel " +
				"explicitly.");
			if (closeProgressAtEnd) done();
			return;
		}

		setTitle("Updating the index of available files");
		final XMLFileReader reader = new XMLFileReader(files);
		final int current = 0, total = updateSites.size();
		if (warnings == null) warnings = new StringBuilder();
		else warnings.setLength(0);
		final List<String> lagging = new ArrayList<>();
		for (final String name : updateSites) {
			final UpdateSite updateSite = files.getUpdateSite(name, true);
			final String title =
				"Updating from " + (name.isEmpty() ? "main" : name) + " site: " + updateSite.getURL();
			addItem(title);
			setCount(current, total);
			read(reader, name, updateSite);
			if (hasNotAdopted(updateSite, channel.channel())) lagging.add(name);
			itemDone(title);
		}
		if (closeProgressAtEnd) {
			done();
		}
		appendWarning(laggingWarning(lagging, channel.channel()));
		appendWarning(reader.getWarnings());
	}

	/**
	 * Reads a site's index, trying each candidate channel in turn and keeping the
	 * first that works.
	 * <p>
	 * There is no separate probe. The first candidate is the installation's own
	 * channel, so in the ordinary case -- a site that has adopted the channel, or
	 * an installation on the base channel -- this makes exactly one request, the
	 * same request the updater made before channels existed.
	 * </p>
	 * <p>
	 * Every failure is treated as "this channel is not published here, try the
	 * next", because the ways a missing channel manifests are more varied than a
	 * 404: some servers answer 403 for a directory that is not there, and a
	 * misconfigured one answers 200 with an HTML error page, which fails not at
	 * the connection but inside the gzip decoder. Only when every candidate has
	 * failed is anything reported, and only then is the site treated as gone.
	 * </p>
	 */
	private void read(final XMLFileReader reader, final String name,
		final UpdateSite updateSite)
	{
		final List<String> candidates =
			Channels.candidates(files.getChannels(), files.getChannel());
		Exception failure = null;
		for (final String channel : candidates) {
			updateSite.setChannel(channel);
			try {
				final URLConnection connection =
					UpdaterUtil.openConnection(new URL(updateSite.getIndexURL()));
				final long lastModified = connection.getLastModified();
				final int fileSize = connection.getContentLength();
				final InputStream in =
					getInputStream(new GZIPInputStream(connection.getInputStream()),
						fileSize);
				reader.read(name, in, updateSite.getTimestamp());
				in.close();
				updateSite.setLastModified(lastModified);
				if (channel != null) {
					files.log.debug("Update site '" + name + "' resolved to channel '" +
						channel + "'");
				}
				return;
			}
			catch (final Exception e) {
				if (failure == null) failure = e;
				if (isUnreachable(e)) {
					// The network is down, not the channel missing. Trying the
					// remaining candidates would just repeat the same timeout.
					break;
				}
			}
		}

		// Every candidate failed, so as far as this installation is concerned the
		// site has nothing to offer it.
		updateSite.setChannel(null);
		if (failure instanceof FileNotFoundException) {
			// it was deleted
			updateSite.setLastModified(0);
			files.log.debug(failure);
		}
		else {
			files.log.error(failure);
		}
		appendWarning("Could not update from site '" + name + "': " + failure);
	}

	/**
	 * Whether a site has engaged with channels but not published for this one.
	 * <p>
	 * The distinction that makes this worth reporting is between a site that has
	 * never heard of channels and one that has. The first is the overwhelming
	 * majority and says nothing by falling back -- its content is served from the
	 * site root exactly as it always was. The second publishes a manifest listing
	 * what it serves, and the absence of this installation's channel from that
	 * list is a real statement: the maintainer thinks about channels and has not
	 * got to this one.
	 * </p>
	 * <p>
	 * Keying on the manifest rather than on the fallback itself is what keeps
	 * this quiet. On the day channels ship, no site has a manifest, so nothing is
	 * reported; the set of sites that can trigger it is exactly the set of
	 * maintainers who have engaged with the system.
	 * </p>
	 */
	private boolean hasNotAdopted(final UpdateSite site, final String channel) {
		if (channel == null) return false; // nothing to be behind
		if (channel.equals(site.getChannel())) return false; // adopted
		return ChannelManifest.read(site.getURL()).isPresent();
	}

	/** One message for all the sites that lag, rather than one apiece. */
	private static String laggingWarning(final List<String> lagging,
		final String channel)
	{
		if (lagging.isEmpty()) return null;
		final StringBuilder sb = new StringBuilder();
		sb.append("The following update sites have not published for ")
			.append(channel).append(" yet:\n    ");
		sb.append(String.join(", ", lagging));
		sb.append("\nThey are being read from their previous release, which may ")
			.append("not work correctly with this edition.");
		return sb.toString();
	}

	/**
	 * Whether a failure means the network could not be reached at all, as opposed
	 * to this particular index not being there.
	 */
	private static boolean isUnreachable(final Exception e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof UnknownHostException) return true;
			if (t instanceof ConnectException) return true;
			if (t instanceof SocketTimeoutException) return true;
			if (t instanceof NoRouteToHostException) return true;
			if (t == t.getCause()) break;
		}
		return false;
	}

	public String getWarnings() {
		return warnings.toString();
	}

	public InputStream getInputStream(final InputStream in, final int fileSize) {
		return new InputStream() {

			int current = 0;

			@Override
			public int read() throws IOException {
				final int result = in.read();
				setItemCount(++current, fileSize);
				return result;
			}

			@Override
			public int read(final byte[] b) throws IOException {
				final int result = in.read(b);
				if (result > 0) {
					current += result;
					setItemCount(current, fileSize);
				}
				return result;
			}

			@Override
			public int read(final byte[] b, final int off, final int len)
				throws IOException
			{
				final int result = in.read(b, off, len);
				if (result > 0) {
					current += result;
					setItemCount(current, fileSize);
				}
				return result;
			}

			@Override
			public void close() throws IOException {
				in.close();
			}
		};
	}

	private void appendWarning(final String s) {
		if (s == null || s.isEmpty()) return;
		warnings.append(s);
		warnings.append(System.lineSeparator());
	}
}
