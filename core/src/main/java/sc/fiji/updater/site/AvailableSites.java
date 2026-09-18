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

package sc.fiji.updater.site;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;

import org.scijava.log.Logger;
import org.scijava.util.XML;
import org.xml.sax.SAXException;

import sc.fiji.updater.FilesCollection;
import sc.fiji.updater.UpdateSite;
import sc.fiji.updater.channel.URLChange;
import sc.fiji.updater.internal.UpdaterUtil;

/**
 * Utility class for parsing the list of available update sites.
 * 
 * @author Johannes Schindelin
 * @author Curtis Rueden
 */
public final class AvailableSites {

	private AvailableSites() {
		// NB: prevent instantiation of utility class
	}

	private static final String SITE_LIST_PAGE_TITLE =
		UpdateSiteNetwork.SITE_LIST_PAGE_TITLE;

	public static Map<String, UpdateSite> getAvailableSites() throws IOException {
		return getAvailableSites(null);
	}

	public static Map<String, UpdateSite> getAvailableSites(final Logger log) throws IOException {
		final String text = downloadWikiPage(log);
		final Map<String, UpdateSite> result = parseWikiPage(text);
		runSanityChecks(result);
		return result;
	}

	private static String downloadWikiPage(final Logger log) throws IOException {
		final String wikiURL = "https://" + UpdateSiteNetwork.SITE_LIST_HOST + "/";

		if(log != null) log.info("Reading available sites from " + wikiURL);
		else System.out.println("[INFO] Reading available sites from " + wikiURL);

		return getPageSource(wikiURL, SITE_LIST_PAGE_TITLE);
	}

	/**
	 * Fetches the wiki source of a single page via the MediaWiki API.
	 * <p>
	 * This is deliberately minimal: an anonymous GET of one page, which is all
	 * the updater has ever needed from the wiki. It replaces a general-purpose
	 * MediaWiki client -- account creation, login, cookie handling and all --
	 * whose only other consequence was a dependency on imagej-common.
	 * </p>
	 * <p>
	 * The whole arrangement is on its way out: the update site list is generated
	 * from structured data in the imagej/list-of-update-sites repository, and is
	 * scraped back out of the wiki page that data renders into. Once that
	 * repository publishes its list directly, {@link #parseWikiPage} and this
	 * method go away together.
	 * </p>
	 *
	 * @param wikiURL base URL of the wiki, ending in a slash.
	 * @param title title of the page to fetch.
	 * @return the page's wiki source.
	 * @throws IOException if the page cannot be fetched or parsed.
	 */
	private static String getPageSource(final String wikiURL, final String title)
		throws IOException
	{
		final String url = wikiURL + "api.php?action=query&format=xml" +
			"&export=true&exportnowrap=true&titles=" +
			URLEncoder.encode(title, "UTF-8");
		try (final InputStream in =
			Connections.openConnection(new URL(url)).getInputStream())
		{
			final XML xml = new XML(in);
			final String source = xml.cdata("/mediawiki/page/revision/text");
			if (source == null) {
				throw new IOException("No such wiki page: " + title);
			}
			return source;
		}
		catch (final ParserConfigurationException | SAXException e) {
			throw new IOException("Could not parse response from " + wikiURL, e);
		}
	}

	private static Map<String, UpdateSite> parseWikiPage(final String text) throws IOException {
		final int start = text.indexOf("\n{| class=\"wikitable\"\n");
		int end = text.indexOf("\n|}\n", start);
		if (end < 0) end = text.length();
		if (start < 0) {
			throw new IOException("Could not find table");
		}
		final String[] table = text.substring(start + 1, end).split("\n\\|-");

		final Map<String, UpdateSite> result = new LinkedHashMap<>();
		int nameColumn = -1;
		int urlColumn = -1;
		int descriptionColumn = -1;
		int maintainerColumn = -1;
		for (final String row : table) {
			if (row.matches("(?s)(\\{\\||[\\|!](style=\"vertical-align|colspan=\"4\")).*")) continue;
			final String[] columns = row.split("\n[\\|!]");
			if (columns.length > 1 && columns[1].endsWith("|'''Name'''")) {
				nameColumn = urlColumn = descriptionColumn = maintainerColumn = -1;
				int i = 0;
				for (final String column : columns) {
					if (column.endsWith("|'''Name'''")) nameColumn = i;
					else if (column.endsWith("|'''Site'''")) urlColumn = i;
					else if (column.endsWith("|'''URL'''")) urlColumn = i;
					else if (column.endsWith("|'''Description'''")) descriptionColumn = i;
					else if (column.endsWith("|'''Maintainer'''")) maintainerColumn = i;
					i++;
				}
			} else if (nameColumn >= 0 && urlColumn >= 0 && columns.length > nameColumn && columns.length > urlColumn) {
				final String name = stripWikiMarkup(columns, nameColumn);
				final String url = stripWikiMarkup(columns, urlColumn);
				final String description = stripWikiMarkup(columns, descriptionColumn);
				final String maintainer = stripWikiMarkup(columns, maintainerColumn);
				final UpdateSite info = new UpdateSite(name, url, null, null, description, maintainer, 0l);
				info.setOfficial(true);
				result.put(info.getURL(), info);
			}
		}
		return result;
	}

	private static void runSanityChecks(final Map<String, UpdateSite> result) throws IOException {
		final Iterator<UpdateSite> iter = result.values().iterator();
		if (!iter.hasNext()) throw new IOException("Invalid page: " + SITE_LIST_PAGE_TITLE);
	}

	/**
	 * As {@link #initializeAndAddSites(FilesCollection)} with an optional
	 * {@link Logger} for reporting errors.
	 */
	public static List< URLChange > initializeAndAddSites(final FilesCollection files, final Logger log) {
		return initializeAndAddSites(files, tryGetAvailableSites(log));
	}

	static List< URLChange > initializeAndAddSites(
			final FilesCollection files, final Collection< UpdateSite > availableSites)
	{
		// The names the installation's sites had before any of this, so that the
		// files naming them can be pointed at wherever those sites end up.
		final Map< UpdateSite, String > namesBefore = new IdentityHashMap<>();
		for (final UpdateSite site : files.getUpdateSites(true)) {
			namesBefore.put(site, site.getName());
		}

		final List< UpdateSite > sites = prepareAvailableUpdateSites(availableSites);
		// method is package private to allow testing
		ArrayList< URLChange > urlChanges = mergeLocalAndAvailableUpdateSites(
				files, sites);
		makeSureNamesAreUnique(sites);
		files.renameUpdateSiteReferences(renames(namesBefore));

		files.replaceUpdateSites(sites);

		return urlChanges;
	}

	private static List< UpdateSite > prepareAvailableUpdateSites(
			Collection< UpdateSite > availableSites)
	{
		// method is package private to allow testing
		final List<UpdateSite> sites = new ArrayList<>();
		// make sure that the main update site is the first one.
		sites.add(initializeMainUpdateSite());
		addAvailableUpdateSites(sites, availableSites);
		return sites;
	}

	private static Collection< UpdateSite > tryGetAvailableSites(Logger log)
	{
		try {
			return getAvailableSites(log).values();
		} catch (Exception e) {
			if (log != null) log.error("Error processing available update sites from ImageJ wiki", e);
			else e.printStackTrace();
			return Collections.emptyList();
		}
	}

	private static UpdateSite initializeMainUpdateSite() {
		final UpdateSite mainSite = new UpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, UpdaterUtil.MAIN_URL, "", "", null, null, 0l);
		mainSite.setOfficial(true);
		return mainSite;
	}

	private static void addAvailableUpdateSites(List< UpdateSite > sites,
			Collection< UpdateSite > availableSites)
	{
		for (final UpdateSite site : availableSites ) {
			Integer index = findPublishedIndex(sites, site);
			if (index == null) {
				sites.add(site);
			} else {
				sites.set(index, site);
			}
		}
	}

	/**
	 * Matches an installation's own update sites against the published list.
	 * <p>
	 * Each published entry can be claimed by at most one local site, and a
	 * claim by identity -- the URL, or being the core site -- is made before
	 * any claim by name, so it wins. Without that, an installation carrying a
	 * stale entry under the name the published list has since given to a site
	 * it already follows would have the stale one displace the real one: both
	 * would claim the same published entry, and the loser would vanish along
	 * with its files.
	 * </p>
	 */
	private static ArrayList< URLChange > mergeLocalAndAvailableUpdateSites(FilesCollection files,
	                                                                        List< UpdateSite > sites)
	{
		// Matching is against the published list as it stands, not against the
		// list being built, so that one local site cannot match another.
		final List< UpdateSite > published = new ArrayList<>(sites);
		final List< UpdateSite > locals = new ArrayList<>(files.getUpdateSites(true));
		final Integer[] matches = new Integer[locals.size()];
		final Set<Integer> claimed = new HashSet<>();

		for (int i = 0; i < locals.size(); i++) {
			matches[i] = findIndexByIdentity(published, locals.get(i), claimed);
			if (matches[i] != null) claimed.add(matches[i]);
		}
		for (int i = 0; i < locals.size(); i++) {
			if (matches[i] != null) continue;
			matches[i] = findIndexByName(published, locals.get(i), claimed);
			if (matches[i] != null) claimed.add(matches[i]);
		}

		ArrayList< URLChange > urlChanges = new ArrayList<>();
		for (int i = 0; i < locals.size(); i++) {
			final UpdateSite local = locals.get(i);
			if (matches[i] == null) {
				sites.add(local);
			} else {
				final UpdateSite available = published.get(matches[i]);
				local.setOfficial(available.isOfficial());
				local.setDescription(available.getDescription());
				local.setMaintainer(available.getMaintainer());
				adoptName(local, available.getName());
				Optional< URLChange > change =
						URLChange.create(local, available.getURL());
				change.ifPresent( urlChanges::add );
				sites.set(matches[i], local);
			}
		}
		return urlChanges;
	}

	/** Which sites ended up named something other than what they started as. */
	private static Map< String, String > renames(
		final Map< UpdateSite, String > namesBefore)
	{
		final Map< String, String > renames = new HashMap<>();
		namesBefore.forEach((site, before) -> {
			if (!before.equals(site.getName())) renames.put(before, site.getName());
		});
		return renames;
	}

	/**
	 * Renames a local site to what the published list calls it.
	 * <p>
	 * The published name is authoritative for a published site, so a rename
	 * there propagates by itself rather than appearing as a second site. This
	 * is what the {@code Fiji-Latest} to {@code Fiji} rename rides on: the
	 * local site is recognized by its URL, and follows the name.
	 * </p>
	 * <p>
	 * Note: unlike a URL change, this is not offered for review. A name is a
	 * label, and changing it moves nothing and downloads nothing.
	 * </p>
	 */
	private static void adoptName(final UpdateSite local, final String name) {
		if (name == null || name.equals(local.getName())) return;
		local.setName(name);
	}

	/**
	 * Finds the unclaimed entry that is the same site as the given one.
	 * <p>
	 * This is identity rather than naming: a site renamed in the published list
	 * is still the site an installation is following, and matching by name
	 * alone would take it for a new one and leave the old entry behind as a
	 * duplicate. That is what makes a rename in the published list propagate by
	 * itself.
	 * </p>
	 */
	private static Integer findIndexByIdentity(List<UpdateSite> sites,
		UpdateSite site, Set<Integer> claimed)
	{
		// Most specific first: the same URL as written.
		for (int i = 0; i < sites.size(); i++) {
			if (claimed.contains(i)) continue;
			if (UpdateSite.normalizedURL(sites.get(i).getURL())
					.equals(UpdateSite.normalizedURL(site.getURL()))) return i;
		}
		// Then a different source for the same site: a user reading the main
		// site from a mirror is following the main site. Note this pass comes
		// second so that a user on a mirror matches the mirror's own entry in
		// the published list, where there is one, rather than the canonical one.
		for (int i = 0; i < sites.size(); i++) {
			if (claimed.contains(i)) continue;
			if (UpdateSite.sameURL(sites.get(i).getURL(), site.getURL())) return i;
		}
		// The core site is the one site whose identity is known independently of
		// the published list, so it is recognized even when the URL cannot speak
		// for it: an installation whose main site URL points somewhere
		// unrecognizable still has a main site, and it is this one.
		for (int i = 0; i < sites.size(); i++) {
			if (claimed.contains(i)) continue;
			if (isCoreSite(sites.get(i)) && isCoreSite(site)) return i;
		}
		return null;
	}

	/**
	 * Finds an unclaimed entry with the given site's name.
	 * <p>
	 * The last resort, for the sites the URL cannot speak for: one whose URL
	 * the user has edited, and -- until sites carry an id -- one that has
	 * genuinely moved.
	 * </p>
	 */
	private static Integer findIndexByName(List<UpdateSite> sites,
		UpdateSite site, Set<Integer> claimed)
	{
		for (int i = 0; i < sites.size(); i++) {
			if (claimed.contains(i)) continue;
			if( sites.get(i).getName().equals(site.getName()) )
				return i;
		}
		return null;
	}

	private static boolean isCoreSite(final UpdateSite site) {
		return UpdateSiteNetwork.isCoreSite(site.getName(), site.getURL());
	}

	/**
	 * Finds the entry a published one coincides with.
	 * <p>
	 * This folds the published list onto itself and onto the seeded main site,
	 * which is a narrower question than {@link #findIndex} answers. The exact
	 * URL counts -- the main site listed under a new name must land on the seed
	 * rather than beside it, or an installation would see two of it -- but
	 * equivalent URLs deliberately do not: the published list carries a mirror
	 * as its own entry, and folding it into the site it mirrors would delete a
	 * listing users pick from.
	 * </p>
	 */
	private static Integer findPublishedIndex(List<UpdateSite> sites, UpdateSite site) {
		for (int i = 0; i < sites.size(); i++) {
			if (UpdateSite.normalizedURL(sites.get(i).getURL())
					.equals(UpdateSite.normalizedURL(site.getURL()))) return i;
		}
		for (int i = 0; i < sites.size(); i++) {
			if( sites.get(i).getName().equals(site.getName()) )
				return i;
		}
		return null;
	}

	/**
	 * Disambiguates sites that ended up sharing a name.
	 * <p>
	 * The list is keyed by name once it reaches the collection, so a duplicate
	 * is not a cosmetic problem: one entry would silently displace the other.
	 * The first holder of a name keeps it and later ones are suffixed, which
	 * puts the published list ahead of local leftovers, since the published
	 * sites are merged first.
	 * </p>
	 * <p>
	 * Note: every site is considered, active or not. This used to skip active
	 * sites before recording their names, so the set only ever held inactive
	 * ones and an inactive duplicate of an <em>active</em> name was never
	 * disambiguated -- which is precisely the case it exists for, an
	 * installation carrying a disabled legacy entry under a name the published
	 * list has since reused.
	 * </p>
	 */
	private static void makeSureNamesAreUnique(final List< UpdateSite > sites)
	{
		final Set<String> names = new HashSet<>();
		for (final UpdateSite site : sites) {
			if (names.contains(site.getName())) {
				int i = 2;
				while (names.contains(site.getName() + "-" + i))
					i++;
				site.setName(site.getName() + ("-" + i));
			}
			names.add(site.getName());
		}
	}

	/**
	 * Initializes the list of update sites,
	 * <em>and</em> adds them to the given {@link FilesCollection}.
	 */
	public static void initializeAndAddSites(final FilesCollection files) {
		initializeAndAddSites(files, (Logger) null);
	}

	private static String stripWikiMarkup(final String[] columns, int index) {
		if (index < 0 || index >= columns.length) return null;
		final String string = columns[index];
		return string.replaceAll("'''", "").replaceAll("\\[\\[([^\\|\\]]*\\|)?([^\\]]*)\\]\\]", "$2").replaceAll("\\[[^\\[][^ ]*([^\\]]*)\\]", "$1");
	}

	/**
	 * Checks whether for all update sites of a given {@link FilesCollection} there is
	 * an updated URL on the remote list of available update sites.
	 */
	public static boolean hasUpdateSiteURLUpdates(FilesCollection plugins) throws IOException {
		return hasUpdateSiteURLUpdates(plugins, getAvailableSites());
	}

	/**
	 * Checks whether for all update sites of a given {@link FilesCollection} there is
	 * an updated URL on the given list of available sites.
	 */
	public static boolean hasUpdateSiteURLUpdates(FilesCollection plugins, Map<String, UpdateSite> availableSites) {
		for(UpdateSite site : availableSites.values()) {
			// TODO use site id
			UpdateSite local = plugins.getUpdateSite(site.getName(), false);
			if(local == null) continue;
			if(!local.shouldKeepURL() && !local.getURL().equals(site.getURL())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Applies the approved changes among the given proposed update site URL
	 * changes, and persists the collection if any were applied.
	 * <p>
	 * Note: the local index is written only when something changed. Proposing
	 * changes and approving none is a read-only operation, and the up-to-date
	 * check does exactly that on every launch -- it reports that updates are
	 * available and leaves applying them to the user, who has not seen the
	 * proposal yet.
	 * </p>
	 *
	 * @return whether anything was changed.
	 */
	public static boolean applySitesURLUpdates(FilesCollection plugins, List< URLChange > urlChanges ) {
		boolean changed = false;
		for(URLChange site : urlChanges) {
			if (site.applyIfApproved()) changed = true;
		}
		if (!changed) return false;
		plugins.setUpdateSitesChanged(true);
		try {
			plugins.write();
		} catch (IOException | SAXException | TransformerConfigurationException e) {
			e.printStackTrace();
		}
		return true;
	}
}
