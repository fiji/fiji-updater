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

	/**
	 * URL prefixes under which update sites are served by a mirror rather than by
	 * their canonical host.
	 * <p>
	 * A mirror is a different source for the same content, not a different site,
	 * so a user who has chosen one should be left on it: the updater must not
	 * offer to "correct" their URL back to the canonical one. That is what
	 * {@code URLChange} consults this for.
	 * </p>
	 * <p>
	 * These are path prefixes, not host prefixes, and deliberately so. Mirror
	 * operators also host update sites of their own -- SIMcheck lives at
	 * {@code downloads.micron.ox.ac.uk/fiji_update/SIMcheck/}, alongside but not
	 * inside that host's {@code /mirrors/} tree -- and matching on the host alone
	 * would classify those as mirrors of something they are not.
	 * </p>
	 * <p>
	 * This list is a stopgap. Mirrors are a property of a site, and belong in the
	 * published site list next to the site they mirror, at which point no URL
	 * needs to appear in this file at all. Until the updater reads that list
	 * directly, adding a mirror means adding a line here.
	 * </p>
	 */
	public static final String[] MIRROR_URL_PREFIXES = {
		"https://downloads.micron.ox.ac.uk/fiji_update/mirrors/",
		"https://mirrors.pasteur.fr/fiji/",
	};

	/**
	 * Mirrors of the main update site, in no particular order.
	 * <p>
	 * Used to recognize that an installation is already following the main site,
	 * whichever source it reads it from.
	 * </p>
	 */
	public static final String[] MAIN_SITE_MIRRORS = {
		"https://downloads.micron.ox.ac.uk/fiji_update/mirrors/sites-fiji/",
		"https://mirrors.pasteur.fr/fiji/sites/Fiji/",
	};

	/** Whether the given URL is served by a known mirror. */
	public static boolean isMirror(final String url) {
		if (url == null) return false;
		for (final String prefix : MIRROR_URL_PREFIXES) {
			if (url.startsWith(prefix)) return true;
		}
		return false;
	}

	/**
	 * Whether the given URL is the main update site, by any of its sources.
	 *
	 * @param url the URL to test; a trailing slash is not required.
	 */
	public static boolean isMainSite(final String url) {
		if (url == null) return false;
		final String normalized = url.endsWith("/") ? url : url + "/";
		if (normalized.endsWith(MAIN_SITE_PATH)) return true;
		for (final String mirror : MAIN_SITE_MIRRORS) {
			if (normalized.equals(mirror)) return true;
		}
		return false;
	}

	/**
	 * Whether the given site is the <em>core</em> update site: the one that ships
	 * the application itself, and therefore the authority on which channels exist
	 * and which one this installation is entitled to read.
	 * <p>
	 * The URL is asked first and the name second, because the URL is the half
	 * that survives {@link #MAIN_SITE_NAME} being retired. During that rename the
	 * constant changes while every installation in the world still carries the
	 * old name in its local index, so a name-only test would stop recognizing the
	 * core site on precisely the installations that have one -- and a site that
	 * is not recognized as core is treated as third-party, which is the permissive
	 * case. Matching on the URL keeps that from being a silent downgrade.
	 * </p>
	 * <p>
	 * The name remains as a fallback for installations whose main site is not
	 * served from the canonical host or a known mirror: a local mirror, a test
	 * fixture, an air-gapped deployment. There the name is all there is to go on.
	 * </p>
	 *
	 * @param name the site's name, as it appears in the local index.
	 * @param url the site's URL.
	 */
	public static boolean isCoreSite(final String name, final String url) {
		return isMainSite(url) || MAIN_SITE_NAME.equals(name);
	}

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
