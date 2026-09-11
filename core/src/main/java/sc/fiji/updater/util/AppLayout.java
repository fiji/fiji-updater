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

import java.io.File;

import org.scijava.util.AppUtils;

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
	 * consulted -- most current first.
	 * <p>
	 * Jaunch sets the first two from {@code ${app-dir}} on every launch, so on
	 * any installation new enough to be running this updater, one of them is
	 * present. {@code scijava.app.directory} leads because it is the
	 * app-agnostic one, shared with app-launcher; {@code fiji.dir} follows
	 * because it names this application specifically.
	 * </p>
	 * <p>
	 * The last two are legacy and kept only for reach: they are what the old
	 * ImageJ launcher set, and costing one array entry each is cheaper than
	 * discovering some path that still depends on them.
	 * </p>
	 */
	public static final String[] APP_DIRECTORY_PROPERTIES = {
		"scijava.app.directory", "fiji.dir", "imagej.dir", "ij.dir"
	};

	/**
	 * Property set by the Debian/Ubuntu packaging, which manages updates through
	 * the system package manager instead.
	 */
	public static final String DEBIAN_PACKAGE_PROPERTY = "fiji.debian";

	/**
	 * Property naming the launcher's key=value configuration file. Set by the
	 * launcher for any Jaunch application, and also read by app-launcher, which
	 * writes the chosen JVM into the same file.
	 */
	public static final String CONFIG_FILE_PROPERTY = "scijava.app.config-file";

	/**
	 * Directory holding the launcher configuration, relative to the installation
	 * root. Used only to locate the CFG when the launcher did not set
	 * {@link #CONFIG_FILE_PROPERTY} -- which is the case whenever the JVM was
	 * started by something other than the launcher.
	 */
	public static final String CONFIG_DIRECTORY = "config/jaunch";

	/** Extension of the launcher's configuration file. */
	public static final String CONFIG_EXTENSION = ".cfg";

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
	 * The installation root to operate on.
	 * <p>
	 * This is the one answer to "which installation is being updated": every
	 * entry point -- the GUI, the command line, the {@code UpdateService} and
	 * the startup check -- goes through here, so that they cannot disagree.
	 * </p>
	 * <p>
	 * It is {@link #appDirectory()} whenever the launcher declared one, which is
	 * every real installation. Only when no property is set -- a JVM started by
	 * something other than a launcher, i.e. a developer setting -- does it fall
	 * back to guessing from the location of this class's JAR.
	 * </p>
	 */
	public static File appRoot() {
		final String dir = appDirectory();
		if (dir != null) return new File(dir);
		return AppUtils.getBaseDirectory(AppLayout.class, "updater");
	}

	/**
	 * Whether we are running outside a launcher, i.e. in a developer setting.
	 * <p>
	 * A launcher always declares the installation root; nothing else does. So
	 * the absence of every {@link #APP_DIRECTORY_PROPERTIES} is the signal, and
	 * {@link #appRoot()} is a guess rather than a fact.
	 * </p>
	 */
	public static boolean isDeveloperSetup() {
		return appDirectory() == null;
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
