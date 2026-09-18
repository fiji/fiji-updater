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

package sc.fiji.updater.channel;

import java.util.Optional;

import sc.fiji.updater.UpdateSite;
import sc.fiji.updater.site.UpdateSiteNetwork;

/**
 * Class indicating automated changes to an update site.
 * These changes need to get approved for active updates.
 *
 * @author Deborah Schmidt
 */
public class URLChange {

	private final UpdateSite site;

	private final String newUrl;

	private final boolean recommended;

	private boolean approved = false;

	/**
	 * Proposes a new URL for this update site. The new URL will not be marked as recommended if:
	 * - the current update site URL is a mirror URL OR
	 * - the site is on the list of available update sites and the URL got manually modified
	 * @param newUrl the new URL
	 */
	public static Optional< URLChange > create(UpdateSite site, String newUrl) {
		newUrl = UpdateSite.format(newUrl);
		if(site.getURL().equals(newUrl)) return Optional.empty();
		return Optional.of( new URLChange(site, newUrl) );
	}

	private URLChange(UpdateSite site, String newUrl) {
		this.site = site;
		this.newUrl = newUrl;
		this.recommended = !site.shouldKeepURL() && ! isMirror(site);
		this.approved = recommended && ! site.isActive();
	}

	public UpdateSite updateSite()
	{
		return site;
	}

	public boolean isApproved() {
		return approved;
	}

	/**
	 * Applies this change, if it was approved.
	 *
	 * @return whether anything was changed. Callers persist the collection only
	 *         when something was, so that a passive check -- one that proposes
	 *         changes and approves none -- does not rewrite the local index.
	 */
	public boolean applyIfApproved() {
		if(newUrl == null) return false;
		if(!approved) return false;
		site.setURL(newUrl);
		site.setKeepURL(false);
		return true;
	}

	public String toString() {
		return "new URL: " + newUrl + ", " + " approved: " + approved;
	}

	public void setApproved(boolean approved) {
		this.approved = approved;
	}

	public String getNewURL() {
		return newUrl;
	}

	public boolean isRecommended() {
		return recommended;
	}

	/**
	 * Helper function checking if an update site URL is a known mirror URL.
	 */
	private static boolean isMirror(UpdateSite site) {
		return UpdateSiteNetwork.isMirror(site.getURL());
	}

}
