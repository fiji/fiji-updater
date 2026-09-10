/*-
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

/**
 * The facts that tie this updater to the ImageJ/Fiji update site network.
 * <p>
 * These are deployment configuration, not application identity: the host
 * serving the main update site, where user sites live, where the list of
 * available sites comes from, and which historical URLs need rewriting. They
 * are gathered here so that the updater's coupling to one particular network is
 * visible and countable in one file, rather than scattered across
 * {@code UpdaterUtil}, {@code HTTPSUtil}, {@code FilesCollection},
 * {@code AvailableSites}, {@code XMLFileReader} and {@code UpdateSite}, as it
 * was until this class existed.
 * </p>
 * <p>
 * Nothing here is injected yet, and there is exactly one network, so the values
 * are constants. Should a second one ever appear, this is the file to make
 * pluggable -- and the point of collecting them is that the change would then
 * be a package move plus a lookup, rather than an archaeological dig.
 * </p>
 * <p>
 * Note the deliberate split from {@link AppLayout}: this class is about
 * <em>where content comes from</em>, that one is about <em>what an installation
 * looks like on disk</em>. The former is plausibly configurable; the latter is
 * closer to the updater's data model.
 * </p>
 *
 * @author Curtis Rueden
 */
public final class UpdateSiteNetwork {

	private UpdateSiteNetwork() {
		// NB: prevent instantiation of constants class
	}

	/**
	 * Name of the update site every installation starts out following.
	 * <p>
	 * Not "ImageJ": that site, along with "Fiji" and "Java-8", belongs to
	 * Fiji-Stable, whose installations run Java 8 and so never run this updater.
	 * This is the site a modern installation actually follows.
	 * </p>
	 * <p>
	 * Nor, yet, plain "Fiji". That name is taken -- every modern installation
	 * still carries a disabled entry for the legacy {@code update.fiji.sc} site
	 * under it, and {@code AvailableSites} matches local sites against the
	 * official list <em>by name</em>, including disabled ones. Claiming the name
	 * here would make the merge replace the main site with that disabled legacy
	 * entry. Retiring the name is a change to the published site list and to the
	 * server, and this constant follows it rather than leading it.
	 * </p>
	 */
	public static final String MAIN_SITE_NAME = "Fiji-Latest";

	/** Host and path serving the main update site, without a protocol. */
	public static final String MAIN_SITE_PATH = "sites.imagej.net/Fiji/";

	/** Host maintainers upload the main update site's contents to. */
	public static final String MAIN_SITE_SSH_HOST = "update.imagej.net";

	/** Upload destination for the main update site. */
	public static final String MAIN_SITE_UPLOAD_DIRECTORY =
		"/home/imagej/update-site";

	/**
	 * Update site name assumed for {@code <plugin>} entries in a local index that
	 * predates the {@code update-site} attribute.
	 */
	public static final String LEGACY_DEFAULT_SITE_NAME = "Fiji";

	/** Host serving personal and third-party update sites. */
	public static final String USER_SITE_HOST = "sites.imagej.net";

	/** Wiki host from which the list of available update sites is read. */
	public static final String SITE_LIST_HOST = "imagej.net";

	/** Title of the wiki page listing the available update sites. */
	public static final String SITE_LIST_PAGE_TITLE = "List of update sites";

	/**
	 * URL used to probe whether the running JVM can negotiate HTTPS with the
	 * hosts this updater talks to.
	 */
	public static final String HTTPS_PROBE_URL = "https://imagej.net/api.php";

	/**
	 * Update site URLs that have moved, paired with their replacements.
	 * <p>
	 * Entries are permanent: an installation left alone for a decade still has
	 * the old URL in its local index, and rewriting it is what lets that
	 * installation find its update site again.
	 * </p>
	 */
	public static final String[][] OBSOLETE_URLS = {
		{ "http://pacific.mpi-cbg.de/update/", "update.fiji.sc/" },
		{ "http://fiji.sc/update/", "update.fiji.sc/" },
	};
}
