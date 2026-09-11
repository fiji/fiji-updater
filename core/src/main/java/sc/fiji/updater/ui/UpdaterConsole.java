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


package sc.fiji.updater.ui;

import java.io.IOException;
import java.io.OutputStream;

import org.scijava.log.Logger;
import org.scijava.log.StderrLogService;

/**
 * How the updater talks to whoever is running it: messages, prompts,
 * preferences.
 * <p>
 * One implementation is installed at a time, reached through {@link #get()}.
 * The core asks its questions through this interface without knowing whether
 * the answer comes from a dialog, a terminal or a test.
 * </p>
 * <p>
 * Not to be confused with {@link sc.fiji.updater.UpdaterCommand}, which is the
 * SciJava command that launches a front end. This is the channel that front
 * end -- or its absence -- provides.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public interface UpdaterConsole {

	/** Prefix for the keys under which login details are remembered. */
	String PREFS_USER = "imagej.updater.login";

	/**
	 * A logger to fall back on when no other one is available.
	 * <p>
	 * The updater runs before and during the replacement of the very JARs a
	 * fuller logging implementation would come from, so it cannot assume one is
	 * present.
	 * </p>
	 */
	static Logger getLogger() {
		return new StderrLogService();
	}

	// -- Messages --

	void error(String message);

	void info(String message, String title);

	void log(String message);

	void debug(String message);

	OutputStream getOutputStream();

	void showStatus(String message);

	void handleException(Throwable exception);

	// -- Prompts --

	boolean isBatchMode();

	int optionDialog(String message, String title, Object[] options, int def);

	boolean promptYesNo(String message, String title);

	String getString(String title);

	String getPassword(String title);

	void openURL(String url) throws IOException;

	// -- Preferences --

	String getPref(String key);

	void setPref(String key, String value);

	void savePreferences();

	// -- The installed instance --

	/** Installs the console the updater is to use from now on. */
	static void set(final UpdaterConsole console) {
		InstalledConsole.current = console;
	}

	/**
	 * The installed console, which is a {@link StderrConsole} until something
	 * calls {@link #set}.
	 */
	static UpdaterConsole get() {
		return InstalledConsole.current;
	}

}

/**
 * Holder for the installed {@link UpdaterConsole}.
 * <p>
 * A separate class because an interface's fields are implicitly final, and
 * this one has to be replaceable.
 * </p>
 */
class InstalledConsole {

	private InstalledConsole() {
		// NB: prevent instantiation of holder class
	}

	static UpdaterConsole current = new StderrConsole();
}
