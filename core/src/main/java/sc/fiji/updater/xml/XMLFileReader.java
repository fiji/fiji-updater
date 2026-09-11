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

package sc.fiji.updater.xml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import sc.fiji.updater.FileObject.Status;
import sc.fiji.updater.FileObject.Version;
import sc.fiji.updater.FileObject;
import sc.fiji.updater.FilesCollection;
import sc.fiji.updater.Timestamps;
import sc.fiji.updater.UpdateSite;
import sc.fiji.updater.channel.Channels;
import sc.fiji.updater.site.Connections;
import sc.fiji.updater.site.UpdateSiteNetwork;

/**
 * The XML File Reader reads a locally-cached index of the available file
 * versions.
 * 
 * @author Johannes Schindelin
 */
public class XMLFileReader extends DefaultHandler {

	private final FilesCollection files;

	// this is the name of the update site (null means we read the local
	// db.xml.gz)
	private String updateSite;
	private Set<FileObject> filesFromThisSite = new HashSet<>();

	// every file newer than this was not seen by the user yet
	private long newTimestamp;

	// There might have been warnings
	private StringBuffer warnings = new StringBuffer();

	// currently parsed
	private FileObject current;
	private String currentTag, body;

	public XMLFileReader(final FilesCollection files) {
		this.files = files;
	}

	public String getWarnings() {
		return warnings.toString();
	}

	public void read(final String updateSite)
		throws ParserConfigurationException, IOException, SAXException
	{
		final UpdateSite site = files.getUpdateSite(updateSite, false);
		if (site == null) {
			throw new IOException("Unknown update site: " + updateSite);
		}

		if (!files.getChannelState().isKnown() &&
			Channels.anyExist(files.getChannels()))
		{
			// See XMLFileDownloader.start: resolving against a guessed channel is
			// how an installation gets silently rolled backwards.
			throw new IOException("Cannot determine which update channel this " +
				"installation follows, so update site '" + updateSite +
				"' cannot be read.");
		}

		// Try each candidate channel in turn, exactly as XMLFileDownloader does;
		// a site being activated has had no more chance to adopt this
		// installation's channel than any other. The core site, as ever, gets the
		// one candidate it is allowed.
		IOException failure = null;
		for (final String channel : files.candidates(site)) {
			site.setChannel(channel);
			try {
				final URLConnection connection =
					Connections.openConnection(new URL(site.getIndexURL()));
				final long lastModified = connection.getLastModified();
				read(updateSite, new GZIPInputStream(connection.getInputStream()),
					site.getTimestamp());

				// lastModified is a Unix epoch, we need a timestamp
				site.setTimestamp(Long.parseLong(Timestamps.timestamp(lastModified)));
				return;
			}
			catch (final IOException e) {
				if (failure == null) failure = e;
			}
		}
		site.setChannel(null);
		throw failure;
	}

	public void read(final InputStream in) throws ParserConfigurationException,
		IOException, SAXException
	{
		read(null, new GZIPInputStream(in), Long.MAX_VALUE);
	}

	// timestamp is the timestamp (not the Unix epoch) we last saw updates from
	// this site
	public void read(final String updateSite, final InputStream in,
		final long timestamp) throws ParserConfigurationException, IOException,
		SAXException
	{
		this.updateSite = updateSite;
		filesFromThisSite.clear();
		newTimestamp = timestamp;

		final InputSource inputSource = new InputSource(in);
		final SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);

		// commented-out as per Postel's law
		// factory.setValidating(true);

		final SAXParser parser = factory.newSAXParser();
		final XMLReader xr = parser.getXMLReader();
		xr.setContentHandler(this);
		xr.setErrorHandler(new XMLFileErrorHandler());
		xr.parse(inputSource);
	}

	@Override
	public void startDocument() {
		body = "";
	}

	@Override
	public void endDocument() {}

	@Override
	public void startElement(final String uri, final String name,
		final String qName, final Attributes atts)
	{
		if ("".equals(uri)) currentTag = qName;
		else currentTag = name;

		if (currentTag.equals("plugin")) {
			String updateSite = this.updateSite;
			if (updateSite == null) {
				updateSite = atts.getValue("update-site");
				// for backwards compatibility
				if (updateSite == null) {
					updateSite = UpdateSiteNetwork.LEGACY_DEFAULT_SITE_NAME;
				}
			}
			current =
				new FileObject(updateSite, atts.getValue("filename"), -1, null, 0,
					Status.NOT_INSTALLED);
			final String executable = atts.getValue("executable");
			if ("true".equalsIgnoreCase(executable)) current.executable = true;
		}
		else if (currentTag.equals("previous-version")) current.addPreviousVersion(
			atts.getValue("checksum"), getLong(atts, "timestamp"), atts.getValue("filename"), getLong(atts, "timestamp-obsolete"));
		else if (currentTag.equals("version")) {
			current.setVersion(atts.getValue("checksum"), getLong(atts, "timestamp"));
			current.filesize = getLong(atts, "filesize");
		}
		else if (currentTag.equals("dependency")) {
			// maybe sometime in the future final String timestamp =
			// atts.getValue("timestamp");
			final String overrides = atts.getValue("overrides");
			current.addDependency(atts.getValue("filename"), getLong(atts,
				"timestamp"), overrides != null && overrides.equals("true"));
		}
		else if (updateSite == null &&
				(currentTag.equals("update-site") || currentTag.equals("disabled-update-site"))) {
			final UpdateSite site = new UpdateSite(atts.getValue("name"),
					atts.getValue("url"),
					atts.getValue("ssh-host"),
					atts.getValue("upload-directory"),
					atts.getValue("description"),
					atts.getValue("maintainer"),
					Long.parseLong(atts.getValue("timestamp")));
			String official = atts.getValue("official");
			if(official != null) {
				site.setOfficial(Boolean.valueOf(official));
			}
			String keepURL = atts.getValue("keep-url");
			if(keepURL == null) site.setKeepURL(false);
			site.setKeepURL(Boolean.valueOf(keepURL));
			site.setActive(currentTag.equals("update-site"));
			files.addUpdateSite(site);
		}
	}

	@Override
	public void
		endElement(final String uri, final String name, final String qName)
	{
		String tagName;
		if ("".equals(uri)) tagName = qName;
		else tagName = name;

		if (tagName.equals("description")) current.description = body;
		else if (tagName.equals("author")) current.addAuthor(body);
		else if (tagName.equals("platform")) current.addPlatform(body);
		else if (tagName.equals("category")) current.addCategory(body);
		else if (tagName.equals("link")) current.addLink(body);
		else if (tagName.equals("plugin")) {
			fillPreviousFilenames(current);

			if (current.current == null) current
				.setStatus(Status.OBSOLETE_UNINSTALLED);
			else if (current.isNewerThan(newTimestamp)) {
				current.setStatus(Status.NEW);
				// NB: setStatus already leaves the file with no action.
				if (current.isActivePlatform(files)) {
					current.setAction(files, FileObject.Action.INSTALL);
				}
			}
			FileObject file = files.get(current.filename);
			if (updateSite == null && current.updateSite != null &&
				files.getUpdateSite(current.updateSite, false) == null) ; // ignore file with invalid update site
			else if (file == null) {
				files.add(current);
				filesFromThisSite.add(current);
			}
			else {
				// Be nice to old-style update sites where Jama-1.0.2.jar and Jama.jar were different file objects
				if (filesFromThisSite.contains(file)) {
					if (file.isObsolete()) {
						files.remove(file.filename);
						final FileObject swap = file;
						file = current;
						current = swap;
						files.add(file);
						filesFromThisSite.add(file);
					}
					addPreviousVersions(current, file);
				} else if (file.isObsolete()) {
					if (file.updateSite != null) {
						for (String site : file.overriddenUpdateSites().keySet())
							current.overriddenUpdateSites().put(site,  file.overriddenUpdateSites().get(site));
						current.overriddenUpdateSites().put(file.updateSite, file);
					}
					files.add(current);
					filesFromThisSite.add(current);
				} else if (current.isObsolete()) {
					if (current.updateSite != null)
						file.overriddenUpdateSites().put(current.updateSite, current);
				} else if (getRank(files, updateSite) >= getRank(files, file.updateSite)) {
					if ((updateSite != null && updateSite.equals(file.updateSite)) || (updateSite == null && file.updateSite == null)) {
						 // simply update the object
					} else {
						for (String site : file.overriddenUpdateSites().keySet())
							current.overriddenUpdateSites().put(site, file.overriddenUpdateSites().get(site));
						if (file.updateSite != null && !file.updateSite.equals(updateSite)) {
							current.overriddenUpdateSites().put(file.updateSite, file);
						}
					}
					if (file.localFilename != null) {
						current.localFilename = file.localFilename;
					}
					// do not forget metadata
					current.completeMetadataFrom(file);
					files.add(current);
					filesFromThisSite.add(current);
					if (this.updateSite != null && file.updateSite != null && getRank(files, this.updateSite) > getRank(files, file.updateSite))
						files.log.debug("'" + current.filename
								+ "' from update site '" + current.updateSite
								+ "' shadows the one from update site '"
								+ file.updateSite + "'");
				}
				else {
					file.overriddenUpdateSites().put(updateSite, current);
					if (this.updateSite != null && file.updateSite != null && getRank(files, file.updateSite) > getRank(files, this.updateSite))
						files.log.debug("'" + file.filename
								+ "' from update site '" + file.updateSite
								+ "' shadows the one from update site '"
								+ current.updateSite + "'");
				}
			}
			current = null;
		}
		body = "";
	}

	/**
	 * Make sure that all previous versions have their file name set.
	 * 
	 * @param file the component
	 */
	private static void fillPreviousFilenames(final FileObject file) {
		List<FileObject.Version> versions = new ArrayList<>();
		if (file.current != null)
			versions.add(file.current);
		for (final FileObject.Version version : file.previous)
			versions.add(version);
		Collections.sort(versions, new Comparator<FileObject.Version>() {
			@Override
			public int compare(Version v1, Version v2) {
				long diff = v1.timestamp - v2.timestamp;
				return diff > 0 ? -1 : (diff < 0 ? +1 : 0);
			}
		});
		String filename = file.filename;
		for (final FileObject.Version version : versions) {
			if (version.filename != null)
				filename = version.filename;
			else
				version.filename = filename;
		}
	}

	private static void addPreviousVersions(FileObject from, FileObject to) {
		if (from.current != null) {
			to.addPreviousVersion(from.current.checksum, from.current.timestamp, from.getLocalFilename(false), 0);
		}
		for (final FileObject.Version version : from.previous) {
			to.addPreviousVersion(version);
		}
	}

	@Override
	public void characters(final char ch[], final int start, final int length) {
		body += new String(ch, start, length);
	}

	private long getLong(final Attributes attributes, final String key) {
		final String value = attributes.getValue(key);
		return value == null ? 0 : Long.parseLong(value);
	}

	private int getRank(final FilesCollection files, final String updateSite) {
		if (updateSite == null || files == null) return -1;
		UpdateSite site = files.getUpdateSite(updateSite, false);
		return site == null ? -1 : site.getRank();
	}
}
