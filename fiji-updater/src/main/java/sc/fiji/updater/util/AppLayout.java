package sc.fiji.updater.util;

/**
 * What a Fiji installation looks like on disk.
 * <p>
 * This is the updater's most application-specific knowledge: which directories
 * it manages, what a launcher is called, how the macOS bundle must be treated,
 * and which system properties name the installation root. Unlike
 * {@link UpdateSiteNetwork}, whose contents are deployment configuration, these
 * facts are close to the updater's data model -- they decide what counts as a
 * file it may install, update or remove.
 * </p>
 * <p>
 * They are gathered here to be countable. Entries of this kind are cheap to add
 * and invisible to leave behind, so the set only ever grew: before this class
 * existed, the same knowledge sat in {@code Checksummer}, {@code Platforms},
 * {@code Installer}, {@code UpToDate} and {@code CommandLine}, and included a
 * third-party plugin suite's directory convention that nothing had shipped into
 * for years. Auditing one file is possible; auditing five is what does not
 * happen.
 * </p>
 * <p>
 * Two pieces of the layout surface deliberately stay where they are, because
 * splitting them from the logic that reads them would make both harder to
 * follow. They are listed here so that an audit has one complete inventory to
 * start from:
 * </p>
 * <ul>
 * <li>{@code Checksummer.directories} -- the managed directories and the file
 * extensions recognized in each.</li>
 * <li>{@code Platforms.LAUNCHERS} -- launcher filenames and the platform each
 * belongs to, together with the rule that everything inside a top-level
 * {@code .app} folder counts as a launcher.</li>
 * </ul>
 * <p>
 * There is one implementation because there is one application. Should that
 * ever change, this is where the interface goes.
 * </p>
 *
 * @author Curtis Rueden
 */
public final class AppLayout {

	private AppLayout() {
		// NB: prevent instantiation of constants class
	}

	/**
	 * System properties naming the installation root, in the order they are
	 * consulted. Several exist for historical reasons: {@code imagej.dir} is set
	 * by the launchers, {@code ij.dir} by the SciJava application layer, and
	 * {@code fiji.dir} predates both.
	 */
	public static final String[] APP_DIRECTORY_PROPERTIES = {
		"imagej.dir", "ij.dir", "fiji.dir"
	};

	/**
	 * Property set by the Debian/Ubuntu packaging, which manages updates through
	 * the system package manager instead.
	 */
	public static final String DEBIAN_PACKAGE_PROPERTY = "fiji.debian";

	/**
	 * The installation root as declared by the launcher, or {@code null} if no
	 * {@link #APP_DIRECTORY_PROPERTIES} is set -- which is the normal situation
	 * when the JVM was started by something other than a launcher.
	 */
	public static String appDirectory() {
		for (final String property : APP_DIRECTORY_PROPERTIES) {
			final String value = System.getProperty(property);
			if (value != null) return value;
		}
		return null;
	}

	/**
	 * Whether this is the Debian/Ubuntu packaged application, whose components
	 * are managed by the system package manager rather than by this updater.
	 */
	public static boolean isDebianPackage() {
		return "true".equals(System.getProperty(DEBIAN_PACKAGE_PROPERTY));
	}

	/**
	 * The macOS application bundle directory.
	 * <p>
	 * Everything inside it must be installed or backed up as a unit, because the
	 * bundle is code-signed as a unit: replacing one file inside a signed bundle
	 * invalidates the signature for the whole thing.
	 * </p>
	 */
	public static final String MAC_BUNDLE = "Fiji.app";

	/** Property list inside the pre-Jaunch macOS bundle, patched on update. */
	public static final String MAC_INFO_PLIST = "Contents/Info.plist";

	/** Launcher inside the pre-Jaunch macOS bundle that {@link #MAC_INFO_PLIST} names. */
	public static final String MAC_LEGACY_LAUNCHER = "Contents/MacOS/ImageJ-tiger";

	/**
	 * The updater's own JAR, which it updates before anything else so that a new
	 * index format is always read by the code that understands it.
	 */
	public static final String UPDATER_JAR = "jars/fiji-updater.jar";

	/** The updater's Swing user interface, updated alongside the updater. */
	public static final String UPDATER_GUI_JAR = "jars/fiji-updater-gui.jar";

	/**
	 * The original Fiji Updater JAR, whose checksum is computed differently for
	 * historical reasons; see {@code Checksummer}.
	 */
	public static final String LEGACY_UPDATER_JAR = "plugins/Fiji_Updater.jar";
}
