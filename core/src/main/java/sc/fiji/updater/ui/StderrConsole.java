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

import java.io.OutputStream;

import org.scijava.log.Logger;

/**
 * The console used until a front end installs its own: messages to stderr,
 * prompts from the terminal.
 * <p>
 * Public, and not final, so that a caller with only one thing to override --
 * a test that needs to answer one prompt, say -- need not restate all
 * seventeen methods.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public class StderrConsole implements UpdaterConsole {

	protected Logger log;

	public StderrConsole() {
		log = UpdaterConsole.getLogger();
	}

	@Override
	public void error(final String message) {
		log.error(message);
	}

	@Override
	public void info(final String message, final String title) {
		log.info(title + ": " + message);
	}

	@Override
	public void log(final String message) {
		log.info(message);
	}

	@Override
	public void debug(final String message) {
		log.debug(message);
	}

	@Override
	public OutputStream getOutputStream() {
		return System.err;
	}

	@Override
	public void showStatus(final String message) {
		log.info(message);
	}

	@Override
	public void handleException(final Throwable exception) {
		log.error(exception);
	}

	@Override
	public boolean isBatchMode() {
		return true;
	}

	@Override
	public int optionDialog(final String message, final String title,
		final Object[] options, final int def)
	{
		throw new RuntimeException("TODO");
	}

	@Override
	public String getPref(final String key) {
		return null;
	}

	@Override
	public void setPref(final String key, final String value) {
		/* ignore */
	}

	@Override
	public void savePreferences() {
		throw new RuntimeException("TODO");
	}

	@Override
	public void openURL(final String url) {
		log.info("Open URL " + url);
	}

	@Override
	public String getString(final String title) {
		System.err.print(title + " ");
		return new String(System.console().readLine());
	}

	@Override
	public String getPassword(final String title) {
		System.err.print(title + " ");
		return new String(System.console().readPassword());
	}

	@Override
	public boolean promptYesNo(final String message, final String title) {
		System.err.println(title + " " + message);
		final String answer = new String(System.console().readLine());
		return answer.toLowerCase().startsWith("y");
	}
}
