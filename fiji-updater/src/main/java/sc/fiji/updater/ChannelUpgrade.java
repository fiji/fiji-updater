package sc.fiji.updater;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import sc.fiji.updater.FileObject.Action;
import sc.fiji.updater.FileObject.Status;
import org.scijava.launcher.Java;

import sc.fiji.updater.util.AppLayout;
import sc.fiji.updater.util.ChannelManifest;
import sc.fiji.updater.util.ChannelState;
import sc.fiji.updater.util.Channels;
import sc.fiji.updater.util.JavaRequirement;
import sc.fiji.updater.util.Progress;

/**
 * Moves an installation from one update channel to another.
 * <p>
 * This is the only operation that changes which channel an installation
 * follows. Nothing else does, deliberately: reading against a different channel
 * would leave the installed files and the declared channel disagreeing, and the
 * next ordinary update would try to undo it.
 * </p>
 * <h2>What it has to get right</h2>
 * <p>
 * The dangerous part is not installing the new channel's files, which the
 * ordinary update machinery already does. It is the files the new channel does
 * <em>not</em> have. Those keep their local records and become
 * {@link Status#LOCAL_ONLY} with no update site: still on disk, still on the
 * classpath, managed by nobody, and invisible to every subsequent update. A
 * channel switch can strand a great many of them at once, so the file set is
 * reconciled explicitly rather than left to fall out.
 * </p>
 * <p>
 * Note that this cannot be done by comparing update site names, the way the
 * old Java-8 migration did. That migration swapped one set of sites for
 * another, so the names told it what had gone. A channel switch leaves every
 * site name unchanged and alters only what each one offers, so the comparison
 * has to be over the file set itself.
 * </p>
 *
 * @author Curtis Rueden
 * @see Channels
 */
public class ChannelUpgrade {

	private final FilesCollection files;
	private final String from;
	private final String to;

	private final Set<String> wasManaged = new LinkedHashSet<>();
	private final List<String> stranded = new ArrayList<>();
	private boolean reconciled;

	/**
	 * Prepares to move the given installation to the given channel.
	 *
	 * @param files the installation; must already have been loaded.
	 * @param to the target channel, or null for the base channel.
	 * @throws IllegalStateException if the installation does not declare a
	 *           channel, in which case there is nothing to move it from and
	 *           nowhere to record the move.
	 * @throws IllegalArgumentException if the installation already follows the
	 *           target channel.
	 */
	public ChannelUpgrade(final FilesCollection files, final String to) {
		final ChannelState state = files.getDeclaredChannelState();
		if (!state.isKnown()) {
			throw new IllegalStateException("Cannot determine which update " +
				"channel this installation follows, so it cannot be moved to " +
				"another one.");
		}
		this.files = files;
		this.from = state.channel();
		this.to = ChannelState.pinned(to).channel();
		if (java.util.Objects.equals(from, this.to)) {
			throw new IllegalArgumentException("This installation already " +
				"follows " + describe(this.to) + ".");
		}
	}

	/**
	 * The channel this installation could move up to, or null if none is on
	 * offer.
	 * <p>
	 * The core update site is the authority here, and only the core site: a
	 * third-party site publishing for a channel this installation does not
	 * follow says nothing about whether the application is ready for it. Its
	 * manifest is also the authoritative <em>ordering</em> of channels -- an
	 * updater can only have been built before the channels that come after it,
	 * so a list compiled into this one would dead-end every installation whose
	 * updater predates the next codename. Reading it here is what keeps that from
	 * happening, and it updates {@link Channels} for the rest of the session.
	 * </p>
	 *
	 * @param files the installation to check.
	 * @return the channel to offer, or null if the installation is on the newest
	 *         one the core site serves, or if its own channel is unknown.
	 */
	public static String availableUpgrade(final FilesCollection files) {
		final ChannelState state = files.getDeclaredChannelState();
		if (!state.isKnown()) return null;

		final UpdateSite core = files.getCoreSite();
		if (core == null) return null;

		final ChannelManifest manifest = ChannelManifest.read(core.getURL());
		if (!manifest.isPresent()) return null;
		files.setChannels(manifest.channels());

		// Newest first, so the first one above us is the one to offer.
		for (final String channel : manifest.channels()) {
			if (Channels.isNewerThan(manifest.channels(), channel, state.channel())) {
				return channel;
			}
		}
		return null;
	}

	/** The channel being moved away from; null is the base channel. */
	public String from() {
		return from;
	}

	/** The channel being moved to; null is the base channel. */
	public String to() {
		return to;
	}

	/** Whether this moves backwards, to an edition older than the current one. */
	public boolean isDowngrade() {
		return !Channels.isNewerThan(files.getChannels(), to, from);
	}

	/** A human-readable name for a channel, including the base one. */
	public static String describe(final String channel) {
		return channel == null ? ChannelState.BASE_CHANNEL_NAME : channel;
	}

	/**
	 * Re-resolves every update site against the target channel and works out what
	 * the move would do, without changing anything on disk.
	 *
	 * @param progress where to report progress.
	 * @return the warnings gathered while re-reading the update sites.
	 */
	public String reconcile(final Progress progress) throws Exception {
		if (files.isEmpty()) {
			// The snapshot below would be empty, and every dropped file would go
			// unnoticed -- the exact defect this class exists to prevent, arriving
			// silently. Better to say so than to report that nothing was dropped.
			throw new IllegalStateException("The file collection has not been " +
				"read yet, so there is nothing to compare the target channel " +
				"against.");
		}

		// Remember what is currently accounted for by some update site. Anything
		// installed and managed now, but managed by nobody afterwards, has been
		// dropped by the channel we are moving to.
		for (final FileObject file : files.managedFiles()) {
			if (file.isInstallable() || file.isUpdateable(true) ||
				file.getStatus() != Status.NOT_INSTALLED)
			{
				wasManaged.add(file.getFilename());
			}
		}

		// The stored per-site timestamps describe the indexes of the channel we
		// are leaving. Against a different index they mean nothing -- they may be
		// older or newer than anything in it -- and they drive the "seen this
		// before" logic, so they are cleared rather than reinterpreted.
		for (final UpdateSite site : files.getUpdateSites(true)) {
			site.setTimestamp(0);
		}

		files.pinChannel(to);
		final String warnings = files.downloadIndexAndChecksum(progress);

		stranded.clear();
		for (final FileObject file : files.values()) {
			if (!wasManaged.contains(file.getFilename())) continue;
			if (file.updateSite != null) continue; // still offered by someone
			if (!file.isLocalOnly()) continue;
			stranded.add(file.getFilename());
		}
		Collections.sort(stranded);
		reconciled = true;
		return warnings;
	}

	/**
	 * The installed files that the target channel does not offer, and which
	 * would therefore be removed. Only meaningful after {@link #reconcile}.
	 */
	public List<String> stranded() {
		return Collections.unmodifiableList(stranded);
	}

	/**
	 * Stages the move: removals for what the target channel drops, installs and
	 * updates for what it offers.
	 * <p>
	 * Staging only. The changes are written into the update directory and take
	 * effect on the next launch, and the declared channel is not recorded until
	 * {@link #commit} is called, so an abandoned upgrade leaves an installation
	 * that still knows what it is.
	 * </p>
	 */
	public void stage() throws IOException {
		requireReconciled();
		for (final String filename : stranded) {
			final FileObject file = files.get(filename);
			if (file != null) file.stageForUninstall(files);
		}
		for (final FileObject file : files.values()) {
			if (file.updateSite == null) continue;
			switch (file.getStatus()) {
				case OBSOLETE:
				case OBSOLETE_MODIFIED:
					file.stageForUninstall(files);
					break;
				case INSTALLED:
					break;
				default:
					// Force, because a file that differs from the target channel's
					// version reads as "locally modified" whether the user modified it
					// or the previous channel simply shipped something else.
					file.setFirstValidAction(files, Action.UPDATE, Action.INSTALL);
					break;
			}
		}
	}

	/**
	 * The Java the target channel expects, read from the launcher configuration
	 * it just staged.
	 * <p>
	 * Only meaningful after {@link #stage}, since it reads the staged file.
	 * </p>
	 */
	public JavaRequirement javaRequirement() {
		final File config = files.getDeclaredChannelState().configFile();
		if (config == null) return JavaRequirement.unknown();
		// The application's TOML is the CFG's sibling of the same name: fiji.cfg
		// alongside fiji.toml. Deriving it avoids naming the application here,
		// and picks the right one out of the several TOMLs Jaunch ships.
		final String name = config.getName();
		final int dot = name.lastIndexOf('.');
		final String toml = (dot < 0 ? name : name.substring(0, dot)) + ".toml";
		return JavaRequirement.read(files.prefixUpdate(
			AppLayout.CONFIG_DIRECTORY + "/" + toml));
	}

	/**
	 * Whether the running Java can run what the target channel is about to
	 * install.
	 */
	public boolean needsNewerJava() {
		return !javaRequirement().isSatisfiedBy(Java.currentVersion());
	}

	/**
	 * Installs the Java the target channel expects, if the running one will not
	 * do.
	 * <p>
	 * Deliberately done before the restart rather than left to the app-launcher
	 * afterwards. Jaunch applies pending updates from within the configuration it
	 * has already read, so the first launch after a switch puts the new files in
	 * place while running on the JVM the <em>previous</em> channel asked for. If
	 * the new channel needs a newer Java, that launch does not reach a prompt; it
	 * fails on the first class compiled for a version it cannot read.
	 * </p>
	 * <p>
	 * Installing first avoids that: app-launcher records the Java it installs in
	 * the launcher's CFG, which the launcher honours over its own search, so the
	 * next launch gets both the new files and a Java able to run them -- in one
	 * restart rather than two.
	 * </p>
	 *
	 * @param headless whether to install without prompting.
	 * @return whether an installation was attempted.
	 */
	public boolean upgradeJava(final boolean headless) {
		final JavaRequirement requirement = javaRequirement();
		if (requirement.isSatisfiedBy(Java.currentVersion())) return false;

		// Point app-launcher at what this channel asks for, rather than at the
		// values the outgoing channel's configuration put into our properties.
		if (requirement.recommended() != null) {
			System.setProperty("scijava.app.java-version-recommended",
				requirement.recommended());
		}
		if (requirement.links() != null) {
			System.setProperty("scijava.app.java-links", requirement.links());
		}
		Java.upgrade(headless, false);
		return true;
	}

	/**
	 * Records the new channel, so the next launch reads it back.
	 * <p>
	 * Last, and separate from {@link #stage}, so that a failure while staging
	 * does not leave an installation claiming to follow a channel whose files it
	 * never received.
	 * </p>
	 */
	public void commit() throws Exception {
		requireReconciled();
		files.getDeclaredChannelState().write(to);
		files.write();
	}

	private void requireReconciled() {
		if (!reconciled) {
			throw new IllegalStateException("reconcile() must run first");
		}
	}
}
