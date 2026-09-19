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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.initialize;
import static sc.fiji.updater.UpdaterTestUtils.main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.xml.sax.SAXException;

import sc.fiji.updater.FileObject;
import sc.fiji.updater.FilesCollection;
import sc.fiji.updater.UpdateSite;
import sc.fiji.updater.channel.URLChange;
import sc.fiji.updater.channel.URLChangeReview;
import sc.fiji.updater.internal.UpdaterUtil;

/**
 * Tests functionalities related to the available update sites
 *
 * @author Deborah Schmidt
 */
public class AvailableSitesTest {

	@Test
	public void testAvailableUpdateSites() throws Exception {

		// load initial files collection
		FilesCollection files = initialize();

		// add two official update sites
		applyOfficialUpdateSitesList(files, "a", "http://a.de/", "b", "http://b.de/");

		// restart
		files = readFromDb(files);

		// test if update site URLS match
		assertEquals("http://a.de/", files.getUpdateSite("a", true).getURL());
		assertEquals("http://b.de/", files.getUpdateSite("b", true).getURL());

		cleanup(files);

	}

	@Test
	public void testAvailableUpdateSitesChange() throws Exception {

		// load initial files collection
		FilesCollection files = initialize();

		// add two official update sites
		applyOfficialUpdateSitesList(files, "a", "http://a.de/", "b", "http://b.de/");

		// apply changes to the list of available update sites
		applyOfficialUpdateSitesList(files, "a", "http://newa.de/", "b", "http://newb.de/");

		// restart
		files = readFromDb(files);

		// test if the update site URLs got updated
		assertEquals("http://newa.de/", files.getUpdateSite("a", true).getURL());
		assertEquals("http://newb.de/", files.getUpdateSite("b", true).getURL());

		cleanup(files);

	}

	@Test
	public void testLocalUpdateSiteURLModification() throws Exception {

		// load initial files collection
		FilesCollection files = initialize();

		// add two official update sites
		applyOfficialUpdateSitesList(files, "a", "http://a.de/", "b", "http://b.de/");

		// set the URL of one update site and remember choice
		UpdateSite b = files.getUpdateSite("b", true);
		b.setURL("http://manuallymodified.de/");
		b.setKeepURL(true);

		// write and read the changes (= restart)
		files.write();
		files = readFromDb(files);

		// test whether the manually modified URL is returned by default
		assertEquals("http://manuallymodified.de/", files.getUpdateSite("b", true).getURL());

		// restart
		files = readFromDb(files);

		// apply changes to the URLs on the list of available update sites
		applyOfficialUpdateSitesList(files, "a", "http://newa.de/", "b", "http://newb.de/");

		// test whether the update site URLs got updated
		assertEquals("http://newa.de/", files.getUpdateSite("a", true).getURL());
		// test whether the manually modified update site URL was kept
		assertEquals("http://manuallymodified.de/", files.getUpdateSite("b", true).getURL());

		cleanup(files);

	}

	@Test
	public void testMirrorMainUpdateSite() throws Exception {

		String mirrorURL = "https://downloads.micron.ox.ac.uk/fiji_update/mirrors/imagej/";

		// load initial files collection
		FilesCollection files = initialize();

		// apply mirror URL as source for main update site
		applyOfficialUpdateSitesList(files,
			FilesCollection.DEFAULT_UPDATE_SITE, mirrorURL);

		// test whether the mirror URL is in use
		assertEquals(mirrorURL, files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).getURL());

		// write and read the changes (= restart)
		files.write();
		files = readFromDb(files);

		// apply list of available update sites containing default ImageJ URL
		applyOfficialUpdateSitesList(files, "Image", "https://update.imagej.net/");

		// restart
		files = readFromDb(files);

		// test whether the mirror URL is still in use
		assertEquals(mirrorURL, files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).getURL());

		cleanup(files);

	}

	@Test
	public void testUpdateSiteOrder() throws Exception {

		// load initial files collection
		FilesCollection files = initialize();

		// add a personal update site
		files.addUpdateSite(createPersonalSite("test", "https://sites.imagej.net/test"));

		// write and read the changes (= restart)
		files.write();
		files = readFromDb(files);

		// add an official update site
		applyOfficialUpdateSitesList(files, "a", "https://sites.imagej.net/a");

		// add another personal update site
		files.addUpdateSite(createPersonalSite("test2", "https://sites.imagej.net/test2"));

		// check if personal update site is the last one in the list
		// check if rank equals list index

		Collection<UpdateSite> sites = files.getUpdateSites(true);
		Iterator<UpdateSite> iterator = sites.iterator();
		assertTrue(iterator.hasNext());
		UpdateSite site = iterator.next();
		assertEquals(FilesCollection.DEFAULT_UPDATE_SITE, site.getName());
		assertEquals(0, site.getRank());
		assertTrue(iterator.hasNext());
		site = iterator.next();
		assertEquals("a", site.getName());
		assertEquals(1, site.getRank());
		site = iterator.next();
		assertTrue(iterator.hasNext());
		assertEquals("test", site.getName());
		assertEquals(2, site.getRank());
		assertTrue(iterator.hasNext());
		site = iterator.next();
		assertEquals("test2", site.getName());
		assertEquals(3, site.getRank());
		assertFalse(iterator.hasNext());

		cleanup(files);

	}

	@Test
	public void testOfficialFlag() throws Exception {

		// load initial files collection
		FilesCollection files = initialize();

		// merge in a published list
		AvailableSites.refresh(files,
			asListOfUpdateSites(new String[] { "a", "https://a.de/", "b", "https://b.de/" }),
			URLChangeReview.asProposed());

		// test whether the update sites are marked as official
		files.getUpdateSites(true).forEach(updateSite -> assertTrue(updateSite.isOfficial()));

		// write and read the changes (= restart)
		files.write();
		files = readFromDb(files);

		// test whether the update sites are still marked as official
		files.getUpdateSites(true).forEach(updateSite -> assertTrue(updateSite.isOfficial()));

		cleanup(files);
	}

	@Test
	public void testActiveUpdateSitesChange() throws Exception {

		// load initial files collection
		FilesCollection files = initialize();

		// add two official update sites
		applyOfficialUpdateSitesList(files, "a", "http://a.de/");

		files.getUpdateSite("a", true).setActive(true);

		// apply changes to the list of available update sites
		applyOfficialUpdateSitesList(files, "a", "http://newa.de/");

		// restart
		files = readFromDb(files);

		// test if the update site URLs got updated
		assertEquals("http://newa.de/", files.getUpdateSite("a", true).getURL());

		cleanup(files);

	}

	@Test
	public void testCommandLineRefreshUpdateSites() throws Exception {

		// load initial files collection
		FilesCollection files = initialize();
		// there should be a main update site
		UpdateSite updateSite = files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true);
		updateSite.setActive(true);
		// the main update site should point to a local folder
		String oldLocalUrl = files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).getURL();

		// when simulating refreshing the update sites, we can observe the changes
		System.out.println("ImageJ --update refresh-update-sites --simulate");
		files = main(files, "refresh-update-sites", "--simulate");
		// the main update site URL should still be the same
		assertEquals(oldLocalUrl, files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).getURL());

		// refresh the URLs again, now forcing to update all URLs
		System.out.println("ImageJ --update refresh-update-sites --updateall");
		files = main(files, "refresh-update-sites", "--updateall");
		// the main update site should now point to the imagej.net server
		assertNotEquals(oldLocalUrl, files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).getURL());
		assertFalse(files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).shouldKeepURL());

		cleanup(files);
	}

	@Test
	public void testCommandLineRefreshInactiveUpdateSites() throws Exception {

		// load initial files collection
		FilesCollection files = initialize();
		// there should be a main update site
		UpdateSite updateSite = files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true);
		updateSite.setActive(false);
		// the main update site should point to a local folder
		String oldLocalUrl = files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).getURL();

		// when simulating refreshing the update sites, we can observe the changes
		System.out.println("ImageJ --update refresh-update-sites --simulate");
		files = main(files, "refresh-update-sites", "--simulate");
		// the main update site URL should still be the same
		assertEquals(oldLocalUrl, files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).getURL());

		// refresh the URLs again, now forcing to update all URLs
		System.out.println("ImageJ --update refresh-update-sites");
		files = main(files, "refresh-update-sites");
		// the main update site should now point to the imagej.net server
		assertNotEquals(oldLocalUrl, files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).getURL());
		assertFalse(files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true).shouldKeepURL());

		cleanup(files);
	}

	@Test
	public void testURLFormattingChange() {
		UpdateSite updateSite = createOfficialSite("ImageJ", "https://sites.imagej.net/ImageJ/");
		Optional< URLChange > change =
				URLChange.create(updateSite, "https://sites.imagej.net/ImageJ");
		assertFalse(change.isPresent());
	}

	/**
	 * A site renamed in the published list is recognized by its URL, and
	 * follows the name rather than appearing as a second site.
	 * <p>
	 * This is what the {@code Fiji-Latest} to {@code Fiji} rename rides on.
	 * </p>
	 */
	@Test
	public void testRenameInPublishedListPropagates() throws Exception {
		final FilesCollection files = initialize();
		files.addUpdateSite("Old", "https://sites.example.org/thing/", null, null, 0);
		files.add(fileOn("Old", "jars/thing.jar"));

		applyOfficialUpdateSitesList(files, "New", "https://sites.example.org/thing/");

		assertNull(files.getUpdateSite("Old", true));
		assertNotNull(files.getUpdateSite("New", true));
		assertEquals("New", files.get("jars/thing.jar").updateSite);

		cleanup(files);
	}

	/**
	 * The collision the rename has to survive: an installation carrying both the
	 * real main site under its old name and a disabled legacy entry under the
	 * name the published list is about to reuse.
	 */
	@Test
	public void testRenameSurvivesACollisionWithALegacyEntry() throws Exception {
		final FilesCollection files = initialize();
		final UpdateSite main = files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true);
		main.setURL("https://sites.imagej.net/Fiji/");
		main.setActive(true);
		files.add(fileOn(FilesCollection.DEFAULT_UPDATE_SITE, "jars/ij.jar"));
		// The leftover an installation that once followed update.fiji.sc has.
		files.addUpdateSite("Fiji", "https://update.fiji.sc/", null, null, 0);
		files.getUpdateSite("Fiji", true).setActive(false);
		files.add(fileOn("Fiji", "jars/legacy.jar"));

		// The published list, after the server-side rename.
		applyOfficialUpdateSitesList(files, "Fiji", "https://sites.imagej.net/Fiji/");

		// The real main site took the name, and its files came with it.
		final UpdateSite renamed = files.getUpdateSite("Fiji", true);
		assertEquals("https://sites.imagej.net/Fiji/", renamed.getURL());
		assertTrue(renamed.isActive());
		assertEquals("Fiji", files.get("jars/ij.jar").updateSite);

		// The legacy entry was disambiguated rather than silently displacing it,
		// and kept both its URL and its files.
		final UpdateSite legacy = files.getUpdateSite("Fiji-2", true);
		assertNotNull(legacy);
		assertEquals("https://update.fiji.sc/", legacy.getURL());
		assertFalse(legacy.isActive());
		assertEquals("Fiji-2", files.get("jars/legacy.jar").updateSite);

		cleanup(files);
	}

	/** A user reading the main site from a mirror is following the main site. */
	@Test
	public void testMirrorIsNotASecondSite() throws Exception {
		final String micron =
				"https://downloads.micron.ox.ac.uk/fiji_update/mirrors/sites-fiji/";
		final FilesCollection files = initialize();
		final UpdateSite main = files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true);
		main.setURL(micron);
		main.setActive(true);

		applyOfficialUpdateSitesList(files, "Fiji", "https://sites.fiji.sc/Fiji/");

		// One main site, not two, and it took the published name.
		assertNull(files.getUpdateSite(FilesCollection.DEFAULT_UPDATE_SITE, true));
		final UpdateSite renamed = files.getUpdateSite("Fiji", true);
		assertNotNull(renamed);
		// The site is recorded where it canonically lives, and the user is still
		// reading it from the mirror they chose.
		assertEquals("https://sites.fiji.sc/Fiji/", renamed.getURL());
		assertEquals(micron, files.getMirror());
		assertEquals(micron, files.sourceURL(renamed));

		cleanup(files);
	}

	/** An installation predating HTTPS is on the same site, not a different one. */
	@Test
	public void testSchemeIsNotIdentity() throws Exception {
		final FilesCollection files = initialize();
		files.addUpdateSite("Thing", "http://sites.example.org/thing/", null, null, 0);

		applyOfficialUpdateSitesList(files, "Thing", "https://sites.example.org/thing/");

		assertEquals(1, countSitesNamed(files, "Thing"));
		// Matching ignores the scheme; the published URL still supersedes it,
		// which is how an installation gets moved off plain HTTP.
		assertEquals("https://sites.example.org/thing/",
				files.getUpdateSite("Thing", true).getURL());

		cleanup(files);
	}

	/** Two unrelated sites stay two sites. */
	@Test
	public void testUnrelatedSitesAreNotMerged() throws Exception {
		final FilesCollection files = initialize();
		files.addUpdateSite("Mine", "https://sites.example.org/mine/", null, null, 0);

		applyOfficialUpdateSitesList(files, "Yours", "https://sites.example.org/yours/");

		assertNotNull(files.getUpdateSite("Mine", true));
		assertNotNull(files.getUpdateSite("Yours", true));

		cleanup(files);
	}

	/**
	 * An installation that predates the mirror preference has the mirror in each
	 * site's URL. It ends up recorded as what it is -- the canonical site, read
	 * from a chosen mirror -- and reads from the same server either way.
	 */
	@Test
	public void testAnInstallationAlreadyOnAMirrorIsMigrated() throws Exception {
		final String pasteur = "https://mirrors.pasteur.fr/fiji/sites/";
		final FilesCollection files = initialize();
		files.addUpdateSite("MoBIE", pasteur + "MoBIE/", null, null, 0);

		applyOfficialUpdateSitesList(files, "MoBIE", "https://sites.fiji.sc/MoBIE/");

		assertEquals(pasteur, files.getMirror());
		final UpdateSite migrated = files.getUpdateSite("MoBIE", true);
		assertEquals("https://sites.fiji.sc/MoBIE/", migrated.getURL());
		assertEquals(pasteur + "MoBIE/", files.sourceURL(migrated));

		cleanup(files);
	}

	/**
	 * The bootstrap loads the installation's index itself, so an entry point
	 * hands it a collection rather than a loaded one.
	 */
	@Test
	public void testBootstrapLoadsTheIndex() throws Exception {
		final FilesCollection files = initialize();
		files.addUpdateSite("Thing", "https://sites.fiji.sc/Thing/", null, null, 0);
		files.write();

		final FilesCollection fresh = new FilesCollection(files.getAppRoot());
		fresh.tryLoadingCollection();
		AvailableSites.refresh(fresh, Collections.emptyList(),
			URLChangeReview.approveNone());

		assertNotNull(fresh.getUpdateSite("Thing", true));

		cleanup(files);
	}

	/**
	 * Approving nothing still reports: this is what lets the up-to-date check
	 * say there is something to do without doing it.
	 */
	@Test
	public void testApproveNoneStillReports() throws Exception {
		final FilesCollection files = initialize();
		files.addUpdateSite("Thing", "https://sites.fiji.sc/Thing/", null, null, 0);
		files.write();
		final byte[] before = index(files);

		final List< URLChange > changes = AvailableSites.refresh(files, moved(),
			proposed -> {
				// The proposal reaches the reviewer either way.
				assertNotNull(changeFor(proposed, "Thing"));
				URLChangeReview.approveNone().review(proposed);
			});

		final URLChange thing = changeFor(changes, "Thing");
		assertNotNull(thing);
		assertTrue(thing.isRecommended());
		assertFalse(thing.isApproved());
		assertEquals("https://sites.fiji.sc/Thing/",
			files.getUpdateSite("Thing", true).getURL());
		assertArrayEquals(before, index(files));

		cleanup(files);
	}

	/**
	 * A URL the user pinned is left alone by the recommended policy and taken by
	 * the one the command line's --updateall selects.
	 */
	@Test
	public void testApproveAllOverridesAPinnedURL() throws Exception {
		final FilesCollection pinned = initialize();
		pinned.addUpdateSite("Thing", "https://sites.fiji.sc/Thing/", null, null, 0);
		pinned.getUpdateSite("Thing", true).setKeepURL(true);
		pinned.write();

		AvailableSites.refresh(pinned, moved(), URLChangeReview.approveRecommended());
		assertEquals("a pinned URL is not recommended for change",
			"https://sites.fiji.sc/Thing/",
			pinned.getUpdateSite("Thing", true).getURL());

		AvailableSites.refresh(pinned, moved(), URLChangeReview.approveAll());
		assertEquals("https://sites.fiji.sc/Moved/",
			pinned.getUpdateSite("Thing", true).getURL());

		cleanup(pinned);
	}

	private static URLChange changeFor(final List< URLChange > changes,
		final String name)
	{
		return changes.stream()
			.filter(change -> name.equals(change.updateSite().getName()))
			.findFirst().orElse(null);
	}

	/** A published list that has moved the "Thing" site. */
	private List< UpdateSite > moved() {
		return asListOfUpdateSites(
			new String[] { "Thing", "https://sites.fiji.sc/Moved/" });
	}

	private byte[] index(final FilesCollection files) throws IOException {
		return Files.readAllBytes(files.prefix(UpdaterUtil.XML_COMPRESSED).toPath());
	}

	private static long countSitesNamed(final FilesCollection files, final String name) {
		return files.getUpdateSites(true).stream()
				.filter(site -> name.equals(site.getName())).count();
	}

	private static FileObject fileOn(final String updateSite, final String filename) {
		return new FileObject(updateSite, filename, 0, "0000000000000000000000000000000000000000",
				0, FileObject.Status.INSTALLED);
	}

	/**
	 * A proposal nobody approved changes nothing, and leaves the local index
	 * untouched.
	 * <p>
	 * The up-to-date check runs on every launch and approves nothing: it
	 * reports that updates are available and leaves applying them to the user.
	 * Writing there would rewrite an installation's site URLs behind the user's
	 * back, on a code path they never saw.
	 * </p>
	 */
	@Test
	public void testUnapprovedChangesAreNotWritten() throws Exception {
		final FilesCollection files = initialize();
		files.addUpdateSite("a", "http://a.de/", null, null, 0);
		files.write();

		final File db = files.prefix(UpdaterUtil.XML_COMPRESSED);
		final byte[] before = Files.readAllBytes(db.toPath());

		final List< URLChange > changes = AvailableSites.initializeAndAddSites(
				files, asListOfUpdateSites(new String[] { "a", "http://moved.de/" }));
		changes.forEach(change -> change.setApproved(false));

		assertFalse(AvailableSites.applySitesURLUpdates(files, changes));
		assertArrayEquals(before, Files.readAllBytes(db.toPath()));
		assertEquals("http://a.de/", readFromDb(files).getUpdateSite("a", true).getURL());

		cleanup(files);
	}

	/** An approved change is applied, and does reach the local index. */
	@Test
	public void testApprovedChangesAreWritten() throws Exception {
		final FilesCollection files = initialize();
		files.addUpdateSite("a", "http://a.de/", null, null, 0);
		files.write();

		final List< URLChange > changes = AvailableSites.initializeAndAddSites(
				files, asListOfUpdateSites(new String[] { "a", "http://moved.de/" }));
		changes.forEach(change -> change.setApproved(true));

		assertTrue(AvailableSites.applySitesURLUpdates(files, changes));
		assertEquals("http://moved.de/",
				readFromDb(files).getUpdateSite("a", true).getURL());

		cleanup(files);
	}

	protected static FilesCollection readFromDb(final FilesCollection ijRoot) {
		FilesCollection files = new FilesCollection(ijRoot.prefix(""));
		try {
			files.read();
		} catch (IOException | ParserConfigurationException | SAXException e) {
			e.printStackTrace();
		}
		return files;
	}

	private void applyOfficialUpdateSitesList(FilesCollection files, String... args) {
		List< UpdateSite > availableSites = asListOfUpdateSites(args);
		List< URLChange > urlChanges = AvailableSites
				.initializeAndAddSites(files, availableSites);
		urlChanges.forEach(change -> change.setApproved(change.isRecommended()));
		AvailableSites.applySitesURLUpdates(files, urlChanges);
	}

	private List< UpdateSite > asListOfUpdateSites(String[] args) {
		assert(args.length % 2 == 0);
		List<UpdateSite> availableUpdateSites = new ArrayList<>();
		for (int i = 0; i < args.length-1; i+=2) {
			availableUpdateSites.add(createOfficialSite(args[i], args[i+1]));
		}
		return availableUpdateSites;
	}

	private UpdateSite createOfficialSite(String name, String url) {
		UpdateSite site = createPersonalSite(name, url);
		site.setOfficial(true);
		return site;
	}

	private UpdateSite createPersonalSite(String name, String url) {
		UpdateSite site = new UpdateSite(name, url, "", "", "", "", 0);
		site.setHost("file:localhost");
		return site;
	}
}
