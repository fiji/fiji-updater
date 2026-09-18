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

package sc.fiji.updater;

import org.scijava.Priority;
import org.scijava.command.CommandService;
import org.scijava.event.EventHandler;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;
import org.scijava.ui.event.UIShownEvent;
import org.scijava.ui.headless.HeadlessUI;

import sc.fiji.updater.cli.CheckForUpdates;

/**
 * Checks for updates once the user interface is up.
 * <p>
 * This is a service because a service is what SciJava instantiates eagerly and
 * subscribes to events for; nothing calls it, and it exposes no interface of
 * its own. It is the only thing in the build that starts the up-to-date check,
 * so an installation that does not load it never checks for updates.
 * </p>
 * <p>
 * Note: this used to be {@code DefaultUpdateService}, implementing an
 * {@code UpdateService} interface whose two methods answered questions about
 * update sites from a {@link FilesCollection} it cached for the lifetime of the
 * context. The cache was never invalidated, so it went stale as soon as the
 * user changed their subscriptions -- which is precisely what its callers were
 * asking about. {@link FilesCollection#activeUpdateSites()} and
 * {@link FilesCollection#isUpdateSiteActive(String)} answer the same questions
 * from a fresh read.
 * </p>
 *
 * @author Curtis Rueden
 */
@Plugin(type = Service.class,
	priority = Priority.HIGH) // NOTE: Higher priority than the ImageJ Updater.
public class UpdateCheckService extends AbstractService {

	private static final String DISABLE_AUTOCHECK_PROPERTY = "imagej.updater.disableAutocheck";
	private static final String LAST_SOFT_CHECK_KEY = "lastSoftCheck";
	private static final long TWENTY_FOUR_HOURS = 24 * 60 * 60 * 1000;

	@Parameter
	private CommandService commandService;

	@Parameter
	private PrefService prefService;

	// -- Event handlers --

	/**
	 * Checks for updates when the user interface is first shown.
	 * 
	 * @param evt The event indicating the UI was shown.
	 */
	@EventHandler
	protected void onEvent(final UIShownEvent evt) {
		if (evt.getUI() instanceof HeadlessUI) return;
		if (Boolean.getBoolean(DISABLE_AUTOCHECK_PROPERTY)) return;

		// Auto-check only once every 24 hours.
		final long now = System.currentTimeMillis();
		final long lastSoftCheck = prefService.getLong(getClass(), LAST_SOFT_CHECK_KEY, 0);
		if (now < lastSoftCheck + TWENTY_FOUR_HOURS) return;
		prefService.put(getClass(), LAST_SOFT_CHECK_KEY, now);

		// NB: Check for updates, but on a separate thread (not the EDT!).
		commandService.run(CheckForUpdates.class, true);
	}
}
