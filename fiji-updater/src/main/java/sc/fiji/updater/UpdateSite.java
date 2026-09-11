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

import sc.fiji.updater.util.HTTPSUtil;
import sc.fiji.updater.util.UpdateSiteNetwork;
import sc.fiji.updater.util.UpdaterUtil;

/**
 * Update sites for the updater.
 * 
 * <p>
 * An update site is a set of files served by a web server that conforms to a
 * certain set of rules defined by the ImageJ updater: a <i>db.xml.gz</i> file
 * in the root directory contains detailed information about available file
 * versions, past file versions, descriptions, authors, links, etc.
 * 
 * @author Johannes Schindelin
 */
public class UpdateSite implements Cloneable, Comparable<UpdateSite> {

	boolean active;
	private boolean official;
	private String name;
	private String url;
	private String channel;
	private boolean keepURLModification;

	private String host;
	private String uploadDirectory;
	private String description;
	private String maintainer;
	private long timestamp;
	int rank;

	public UpdateSite(final String name, String url, final String sshHost, String uploadDirectory,
	                  final String description, final String maintainer, final long timestamp)
	{
		setName(name);
		setURL(url);
		setUploadDirectory(uploadDirectory);
		setHost(sshHost);
		setDescription(description);
		setMaintainer(maintainer);
		setTimestamp(timestamp);
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(final boolean active) {
		this.active = active;
	}

	/**
	 * Gets whether the site is one of those listed on the <a
	 * href="http://imagej.net/List_of_update_sites">canonical list of update
	 * sites</a>.
	 */
	public boolean isOfficial() {
		return official;
	}

	/**
	 * Sets the flag indicating whether the site is listed on the <a
	 * href="http://imagej.net/List_of_update_sites">canonical list of update
	 * sites</a>.
	 */
	public void setOfficial(final boolean official) {
		this.official = official;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getURL() {
		return url;
	}

	/**
	 * Gets the channel this site is currently resolved to, or {@code null} for
	 * the base channel -- the index at the site root, which is what every site
	 * served before channels existed.
	 *
	 * @see #getIndexURL()
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Sets the channel this site is resolved to. {@code null} means the base
	 * channel.
	 * <p>
	 * This is resolution state rather than configuration: it records which of the
	 * site's indexes was actually found, which may be older than the
	 * installation's own channel if this site has not published for it.
	 * </p>
	 */
	public void setChannel(final String channel) {
		this.channel = channel;
	}

	/**
	 * Gets the URL of this site's index of available files.
	 * <p>
	 * This is the one place the remote index URL is constructed. It used to be
	 * assembled by hand at each call site -- in XMLFileDownloader, XMLFileReader,
	 * UpToDate, FilesUploader and, in a different repository, the Swing site
	 * editor -- which is how the last of those came to disagree with the others.
	 * </p>
	 * <p>
	 * With no channel set, this is byte-for-byte the URL those call sites built,
	 * so behavior is unchanged for a site that has never heard of channels.
	 * </p>
	 */
	public String getIndexURL() {
		return getURL() + getIndexPath();
	}

	/**
	 * Gets the path of this site's index relative to the site root, which is both
	 * where a client reads it from and where a maintainer publishes it to.
	 *
	 * @param channel the channel to address; null for the base channel.
	 */
	public static String getIndexPath(final String channel) {
		return (channel == null ? "" : channel + "/") + UpdaterUtil.XML_COMPRESSED;
	}

	/** Gets the index path for the channel this site is resolved to. */
	public String getIndexPath() {
		return getIndexPath(channel);
	}

	/**
	 * Gets the base URL of this site's current channel: the site URL for the base
	 * channel, or the channel's subdirectory beneath it.
	 * <p>
	 * Note that file contents are <em>not</em> served from here. Datestamped
	 * blobs stay in the site root regardless of channel, because the datestamp
	 * already disambiguates them and a channel index may reference a blob first
	 * published for another channel; see {@code FilesCollection.getURL}.
	 * </p>
	 */
	public String getChannelURL() {
		return channel == null ? url : url + channel + "/";
	}

	public void setURL(String url) {
		url = format(url);
		this.url = url;
	}

	public String getHost() {
		return host;
	}

	public void setHost(final String host) {
		this.host = host;
	}

	public String getUploadDirectory() {
		return uploadDirectory;
	}

	public void setUploadDirectory(final String uploadDirectory) {
		if (uploadDirectory == null || uploadDirectory.equals("") || uploadDirectory.endsWith("/")) this.uploadDirectory = uploadDirectory;
		else this.uploadDirectory = uploadDirectory + "/";
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public String getMaintainer() {
		return maintainer;
	}

	public void setMaintainer(final String maintainer) {
		this.maintainer = maintainer;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public int getRank() {
		return rank;
	}

	@Override
	public Object clone() {
		final UpdateSite clone = new UpdateSite(name, url, host, uploadDirectory, description, maintainer, timestamp);
		clone.setActive(isActive());
		clone.setOfficial(isOfficial());
		clone.setChannel(getChannel());
		return clone;
	}

	public boolean isLastModified(final long lastModified) {
		return timestamp == Long.parseLong(UpdaterUtil.timestamp(lastModified));
	}

	public void setLastModified(final long lastModified) {
		timestamp = Long.parseLong(UpdaterUtil.timestamp(lastModified));
	}

	public boolean isUploadable() {
		return (uploadDirectory != null && !uploadDirectory.equals("")) ||
				(host != null && host.indexOf(':') > 0);
	}

	@Override
	public String toString() {
		return getURL() + (host != null ? ", " + host : "") +
			(uploadDirectory != null ? ", " + uploadDirectory : "");
	}

	@Override
	public int compareTo(UpdateSite other) {
		return rank - other.rank;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof UpdateSite)
			return rank == ((UpdateSite)other).rank;
		return false;
	}

	@Override
	public int hashCode() {
		return rank;
	}

	public String getUploadProtocol() {
		if (host == null)
			throw new RuntimeException("Missing upload information for site " + url);
		final int at = host.indexOf('@');
		final int colon = host.indexOf(':');
		if (colon > 0 && (at < 0 || colon < at)) return host.substring(0, colon);
		return "ssh";
	}

	public boolean shouldKeepURL() {
		return keepURLModification;
	}

	public void setKeepURL(boolean keepURL) {
		this.keepURLModification = keepURL;
	}

	public static String format(String url) {
		if(url == null || url.isEmpty()) return url;
		if (!url.endsWith("/")) {
			url += "/";
		}
		url = rewriteOldURLs(url);
		return url;
	}

	/** Rewrites known-obsolete URLs to the current ones. */
	private static String rewriteOldURLs(String url) {
		for (final String[] entry : UpdateSiteNetwork.OBSOLETE_URLS) {
			if (entry[0].equals(url)) {
				return HTTPSUtil.getProtocol() + entry[1];
			}
		}
		return url;
	}
}
