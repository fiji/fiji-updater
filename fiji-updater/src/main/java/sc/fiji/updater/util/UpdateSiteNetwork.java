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

	/** Name of the update site every installation starts out following. */
	public static final String MAIN_SITE_NAME = "ImageJ";

	/** Host serving the main update site. */
	public static final String MAIN_SITE_HOST = "update.imagej.net";

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
