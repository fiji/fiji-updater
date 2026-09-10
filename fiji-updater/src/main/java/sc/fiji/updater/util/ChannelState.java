package sc.fiji.updater.util;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.scijava.launcher.Config;

/**
 * The update channel an installation is currently following.
 * <p>
 * The channel decides which of a site's indexes the updater reads, so getting
 * it wrong is not a cosmetic mistake: silently treating an upgraded
 * installation as though it were on the base channel would resolve every site
 * to its oldest index and mass-downgrade the installation. This class therefore
 * distinguishes three situations rather than two.
 * </p>
 * <ul>
 * <li><b>Base channel.</b> A configuration file was found and declares no
 * channel. The installation predates channels, or has simply never been
 * upgraded past the base. Resolution proceeds against the site root.</li>
 * <li><b>A named channel.</b> A configuration file was found and declares one.
 * Resolution proceeds against that channel, falling back for sites that have
 * not published for it.</li>
 * <li><b>Unknown.</b> No configuration file could be located at all, which
 * happens whenever something other than the launcher started the JVM -- an IDE,
 * a test harness, PyImageJ, a wrapper script. The correct response is to refuse
 * to resolve and say so, never to assume the base channel.</li>
 * </ul>
 * <p>
 * The channel lives in the launcher's CFG rather than in {@code db.xml.gz}
 * because the index is rewritten by whichever updater last touched it: an older
 * one, which knows nothing of channels, would silently drop the field and leave
 * the next channel-aware run looking at an installation that appears to be on
 * the base channel. The CFG is never rewritten by the updater's index code, and
 * -- as the Checksummer's allow list ensures -- can never be shipped by an
 * update site either.
 * </p>
 *
 * @author Curtis Rueden
 */
public final class ChannelState {

	/** Key naming the channel inside the launcher's configuration file. */
	public static final String CHANNEL_KEY = "updater-channel";

	/**
	 * Property the launcher interpolates {@link #CHANNEL_KEY} into, so that the
	 * running application can read the channel without touching the file.
	 */
	public static final String CHANNEL_PROPERTY = "scijava.updater.channel";

	/** Display name for the base channel, which has no name of its own. */
	public static final String BASE_CHANNEL_NAME = "Origin";

	private final String channel;
	private final boolean known;
	private final File configFile;

	private ChannelState(final String channel, final boolean known,
		final File configFile)
	{
		this.channel = channel;
		this.known = known;
		this.configFile = configFile;
	}

	/**
	 * Reads the channel of the installation rooted at the given directory.
	 *
	 * @param appRoot the installation root, used to locate the launcher
	 *          configuration when the launcher did not name it.
	 * @return the channel state; never null, but possibly {@link #isKnown()
	 *         unknown}.
	 */
	public static ChannelState read(final File appRoot) {
		final File config = findConfigFile(appRoot);
		if (config == null) {
			// No configuration file: we cannot tell the base channel apart from an
			// upgraded installation whose launcher simply is not involved.
			return new ChannelState(null, false, null);
		}
		String value = null;
		try {
			final Map<String, String> entries = Config.load(config);
			if (entries != null) value = entries.get(CHANNEL_KEY);
		}
		catch (final IOException e) {
			// An unreadable configuration file is not a missing one, but we cannot
			// tell what it says, so the channel is still unknown.
			return new ChannelState(null, false, config);
		}
		if (value != null) value = value.trim();
		if (value != null && value.isEmpty()) value = null;
		return new ChannelState(value, true, config);
	}

	/**
	 * A channel named explicitly rather than read from a launcher configuration.
	 * <p>
	 * This is how a headless or scripted run says which edition it is operating
	 * on, in the situations where the launcher is not involved and the channel
	 * would otherwise be {@link #isKnown() unknown}. It is an override for the
	 * run, not a change to the installation: there is no configuration file
	 * behind it, so {@link #write} refuses, and the next launch reads whatever
	 * the installation actually says. Changing an installation's channel is an
	 * upgrade, and an upgrade is not a command-line flag.
	 * </p>
	 *
	 * @param channel the channel, or null for the base channel. The base
	 *          channel's display name is also accepted, case-insensitively.
	 */
	public static ChannelState pinned(final String channel) {
		String name = channel == null ? null : channel.trim();
		if (name != null &&
			(name.isEmpty() || name.equalsIgnoreCase(BASE_CHANNEL_NAME)))
		{
			name = null;
		}
		return new ChannelState(name, true, null);
	}

	/**
	 * Whether the channel could be determined. When false, the updater must not
	 * resolve any update site: assuming the base channel here is the
	 * mass-downgrade bug.
	 */
	public boolean isKnown() {
		return known;
	}

	/**
	 * The channel name, or null for the base channel. Only meaningful when
	 * {@link #isKnown()}.
	 */
	public String channel() {
		return channel;
	}

	/** The configuration file the channel was read from, or null if none. */
	public File configFile() {
		return configFile;
	}

	/** A name suitable for showing to a person. */
	public String displayName() {
		if (!known) return "unknown";
		return channel == null ? BASE_CHANNEL_NAME : channel;
	}

	/**
	 * Records a new channel for this installation, so that the next launch reads
	 * it back. Preserves every other entry in the configuration file, which also
	 * holds the launcher's chosen JVM and other local machine state.
	 *
	 * @param channel the new channel, or null for the base channel.
	 * @return the updated state.
	 * @throws IOException if the configuration file cannot be written, or if
	 *           there is no configuration file to write to.
	 */
	public ChannelState write(final String channel) throws IOException {
		if (configFile == null) {
			throw new IOException("No launcher configuration file to record the " +
				"channel in; cannot change channel for this installation.");
		}
		Config.update(configFile, CHANNEL_KEY, channel == null ? "" : channel);
		return new ChannelState(channel, true, configFile);
	}

	/**
	 * Locates the launcher's configuration file: the one the launcher named, or
	 * failing that the sole CFG in the installation's configuration directory.
	 * <p>
	 * The fallback matters more than it looks. Without it, any JVM not started by
	 * the launcher -- PyImageJ, CI, an IDE -- would see no channel at all, and a
	 * headless update would refuse to run on an installation that is perfectly
	 * well configured.
	 * </p>
	 */
	private static File findConfigFile(final File appRoot) {
		final String declared = System.getProperty(AppLayout.CONFIG_FILE_PROPERTY);
		if (declared != null && !declared.isEmpty()) {
			final File file = new File(declared);
			if (file.exists()) return file;
		}
		if (appRoot == null) return null;
		final File dir = new File(appRoot, AppLayout.CONFIG_DIRECTORY);
		final File[] candidates = dir.listFiles((d, name) -> //
			name.endsWith(AppLayout.CONFIG_EXTENSION));
		if (candidates == null || candidates.length == 0) return null;
		if (candidates.length == 1) return candidates[0];

		// More than one: prefer the one named after the application, so that a
		// stray CFG next to the real one does not make the channel ambiguous.
		final String appName = System.getProperty("scijava.app.name");
		if (appName != null) {
			final String expected =
				appName.toLowerCase() + AppLayout.CONFIG_EXTENSION;
			for (final File candidate : candidates) {
				if (candidate.getName().equalsIgnoreCase(expected)) return candidate;
			}
		}
		return null;
	}
}
