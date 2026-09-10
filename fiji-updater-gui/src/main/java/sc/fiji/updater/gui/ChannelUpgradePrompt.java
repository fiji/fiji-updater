/*
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2009 - 2026 ImageJ developers.
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

package sc.fiji.updater.gui;

import java.util.List;

import sc.fiji.updater.ChannelUpgrade;
import sc.fiji.updater.FileObject;
import sc.fiji.updater.FilesCollection;
import sc.fiji.updater.Installer;
import sc.fiji.updater.util.JavaRequirement;
import sc.fiji.updater.util.Progress;
import sc.fiji.updater.util.UpdaterUserInterface;

/**
 * Offers to move this installation to a newer update channel.
 * <p>
 * Deliberately general, where the Java-8 migration this replaces was not. That
 * one could name what was changing, because it knew: one specific move, from a
 * known set of update sites to another, at a known moment. A channel switch is
 * the same operation performed indefinitely into the future, between channels
 * that do not exist yet, so the prompt describes what the reconciliation
 * actually found rather than anything written in advance.
 * </p>
 *
 * @author Curtis Rueden
 */
public class ChannelUpgradePrompt {

	/** Remembers a user who asked not to be offered upgrades again. */
	private static final String SKIP_PREF = "skipChannelUpgradePrompt";

	private final UpdaterFrame frame;
	private final FilesCollection files;

	public ChannelUpgradePrompt(final UpdaterFrame frame,
		final FilesCollection files)
	{
		this.frame = frame;
		this.files = files;
	}

	/**
	 * Offers the upgrade if there is one to offer, and performs it if accepted.
	 *
	 * @return whether an upgrade was performed, in which case the application
	 *         must restart and the caller should not continue.
	 */
	public boolean offer() {
		if (UpdaterUserInterface.get().isBatchMode()) return false;
		if ("true".equals(UpdaterUserInterface.get().getPref(SKIP_PREF))) {
			return false;
		}

		final String target = ChannelUpgrade.availableUpgrade(files);
		if (target == null) return false;

		// An upgrade should not have to contend with a half-updated installation,
		// so it waits until everything in the current channel is in place. Saying
		// so matters: failing silently here is how one un-updateable file, for
		// any unrelated reason, quietly denies a user an upgrade their colleague
		// was offered.
		if (files.updateable(false).iterator().hasNext()) {
			frame.info("An upgrade to " + target + " is available, but this " +
				"installation is not fully up to date yet.\n" +
				"Apply the pending updates first, then reopen the updater to " +
				"upgrade.");
			return false;
		}

		final ChannelUpgrade upgrade;
		try {
			upgrade = new ChannelUpgrade(files, target);
		}
		catch (final IllegalStateException | IllegalArgumentException e) {
			frame.log.debug(e);
			return false;
		}

		final Progress progress = frame.getProgress("Examining " + target + "...");
		try {
			upgrade.reconcile(progress);
		}
		catch (final Exception e) {
			frame.log.error(e);
			frame.error("Could not examine " + target + ": " + e.getMessage());
			return false;
		}

		switch (ask(upgrade)) {
			case 0:
				return apply(upgrade, target);
			case 2:
				UpdaterUserInterface.get().setPref(SKIP_PREF, "true");
				UpdaterUserInterface.get().savePreferences();
				return false;
			default:
				return false;
		}
	}

	/** Describes what the move would do, and asks whether to do it. */
	private int ask(final ChannelUpgrade upgrade) {
		final String target = ChannelUpgrade.describe(upgrade.to());
		final StringBuilder sb = new StringBuilder("<html><body>");
		sb.append("<h2>").append(target).append(" is available</h2>");
		sb.append("<p>This installation follows ")
			.append(ChannelUpgrade.describe(upgrade.from()))
			.append(". Moving to ").append(target).append(" would:</p><ul>");

		int updates = 0, installs = 0;
		for (final FileObject file : files.changes()) {
			switch (file.getAction()) {
				case INSTALL: installs++; break;
				case UPDATE: updates++; break;
				default: break;
			}
		}
		if (updates > 0) sb.append("<li>update ").append(count(updates, "file"))
			.append("</li>");
		if (installs > 0) sb.append("<li>install ")
			.append(count(installs, "new file")).append("</li>");

		final List<String> stranded = upgrade.stranded();
		if (!stranded.isEmpty()) {
			sb.append("<li>remove ").append(count(stranded.size(), "file"))
				.append(" that ").append(target).append(" does not include:<br><i>")
				.append(summarize(stranded)).append("</i></li>");
		}

		final JavaRequirement java = upgrade.javaRequirement();
		if (upgrade.needsNewerJava() && java.recommended() != null) {
			sb.append("<li>install Java ").append(java.recommended())
				.append(", which ").append(target)
				.append(" needs and this installation does not have</li>");
		}
		sb.append("</ul>");

		final List<String> lagging = laggingSites(upgrade.to());
		if (!lagging.isEmpty()) {
			sb.append("<p>These update sites have not published for ")
				.append(target).append(" yet:<br><i>").append(summarize(lagging))
				.append("</i><br>They will keep working from their previous ")
				.append("release, which may not suit this edition.</p>");
		}

		sb.append("<p>Fiji will need to restart afterwards.</p>");
		sb.append("</body></html>");

		return UpdaterUserInterface.get().optionDialog(sb.toString(),
			"Upgrade to " + target + "?",
			new Object[] { "Upgrade to " + target, "Not now", "Never ask again" },
			0);
	}

	/** Sites carrying channels but not the one being moved to. */
	private List<String> laggingSites(final String channel) {
		final List<String> lagging = new java.util.ArrayList<>();
		for (final String name : files.getUpdateSiteNames(false)) {
			final sc.fiji.updater.UpdateSite site = files.getUpdateSite(name, true);
			if (site == null) continue;
			// The core site is never one of the laggards. It either serves the
			// target channel -- which is where the offer of the upgrade came from
			// in the first place -- or the upgrade fails outright and says so.
			if (files.isCoreSite(site)) continue;
			if (channel != null && channel.equals(site.getChannel())) continue;
			if (sc.fiji.updater.util.ChannelManifest.read(site.getURL())
				.isPresent())
			{
				lagging.add(name);
			}
		}
		return lagging;
	}

	private boolean apply(final ChannelUpgrade upgrade, final String target) {
		final Progress progress = frame.getProgress("Upgrading to " + target);
		try {
			upgrade.stage();
			final Installer installer = new Installer(files, progress);
			installer.start();
			installer.done();

			// Before the restart, not after: the launcher applies pending updates
			// from the configuration it has already read, so the next launch would
			// otherwise run this channel's code on the old channel's Java.
			if (upgrade.needsNewerJava()) upgrade.upgradeJava(false);

			upgrade.commit();
		}
		catch (final Exception e) {
			frame.log.error(e);
			frame.error("The upgrade to " + target + " did not complete: " +
				e.getMessage() + "\nThis installation still follows " +
				ChannelUpgrade.describe(upgrade.from()) + ".");
			return false;
		}
		frame.info("This installation now follows " + target + ".\n" +
			"Please restart Fiji for the changes to take effect.");
		return true;
	}

	private static String count(final int n, final String noun) {
		return n + " " + noun + (n == 1 ? "" : "s");
	}

	/** Names a few things, then says how many more there were. */
	private static String summarize(final List<String> names) {
		final int shown = Math.min(names.size(), 8);
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < shown; i++) {
			if (i > 0) sb.append(", ");
			sb.append(names.get(i));
		}
		if (names.size() > shown) {
			sb.append(" and ").append(names.size() - shown).append(" more");
		}
		return sb.toString();
	}
}
