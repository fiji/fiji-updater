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

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * How the updater talks to an update site over HTTP.
 * <p>
 * Every connection the updater opens goes through {@link #openConnection}, so
 * that redirects are followed and the User-Agent identifying this updater is
 * sent exactly once, from one place.
 * </p>
 *
 * @author Johannes Schindelin
 */
public final class Connections {

	private Connections() {
		// NB: prevent instantiation of utility class
	}

	/**
	 * Routes outgoing connections through the proxy the operating system is
	 * configured to use.
	 */
	public static void useSystemProxies() {
		/*
		 * Avoid those pesky
		 * "GConf-WARNING **: Client failed to connect to the D-BUS daemon"
		 * messages in headless mode (e.g. Jenkins).
		 */
		final String osName = System.getProperty("os.name", "<unknown>");
		if (osName.equals("Linux") && GraphicsEnvironment.isHeadless()) return;

		System.setProperty("java.net.useSystemProxies", "true");
	}

	/**
	 * The URL's last-modified time as a Unix epoch in milliseconds, or a
	 * negative value if the server would not say -- including the case of no
	 * network at all, since a site which cannot be reached must never be
	 * mistaken for a site whose content has changed.
	 */
	public static long getLastModified(final URL url) {
		try {
			final URLConnection connection = openConnection(url);
			if (connection instanceof HttpURLConnection) ((HttpURLConnection) connection)
				.setRequestMethod("HEAD");
			connection.setUseCaches(false);
			final long lastModified = connection.getLastModified();
			connection.getInputStream().close();
			return lastModified;
		}
		catch (final IOException e) {
			if (e.getMessage().startsWith("Server returned HTTP response code: 407")) return -111381;
			if (e.getMessage().startsWith("Server returned HTTP response code: 405")) try {
				final URLConnection connection = openConnection(url);
				connection.setUseCaches(false);
				final long lastModified = connection.getLastModified();
				connection.getInputStream().close();
				return lastModified;
			} catch (IOException e2) {
				e.printStackTrace();
				e2.printStackTrace();
			}
			// assume no network; so let's pretend everything's ok.
			return -1;
		}
	}

	/**
	 * Open a stream to a {@link URL}.
	 *
	 * @param url the URL to open
	 */
	public static InputStream openStream(final URL url) throws IOException {
		return openConnection(url).getInputStream();
	}

	/**
	 * Open a connection to a {@link URL}.
	 *
	 * @param url the URL to open
	 */
	public static URLConnection openConnection(final URL url) throws IOException {
		final URLConnection connection = url.openConnection();
		if (connection instanceof HttpURLConnection) {
			HttpURLConnection http = (HttpURLConnection)connection;
			http.setInstanceFollowRedirects(true); // Follow HTTP 3xx redirects.
			final String javaVmVersion = System.getProperty("java.runtime.version");
			final String javaVersion = javaVmVersion != null ?
					javaVmVersion : System.getProperty("java.version");
			final String osVersion = System.getProperty("os.version");
			final String os = "" + System.getProperty("os.name") + "-"
					+ (osVersion != null ? osVersion + "-" : "")
					+ System.getProperty("os.arch");
			http.setRequestProperty("User-Agent",
					"curl/7.22.0 compatible ImageJ updater/2.0.0-SNAPSHOT (Java "
					+ javaVersion + "/" + os + ")");
		}
		return connection;
	}

}
