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

import java.util.List;

/**
 * What an entry point does about the update site URL changes the published list
 * proposes.
 * <p>
 * Reconciling an installation with the published list is the same three steps
 * everywhere -- load the local index, merge the published list into it, apply
 * the URL changes that were approved -- and the entry points differ only in who
 * approves. The graphical updater asks the user, the command line reads its
 * flags, and the up-to-date check approves nothing at all, because it runs
 * unattended on every launch and its job is to report that there is something
 * to do rather than to do it.
 * </p>
 * <p>
 * Note a review that approves nothing is not a review that does nothing: the
 * proposals are still computed and returned, which is how the up-to-date check
 * knows there is something to report. What it does not do is write them to
 * disk behind the user, who has not seen them yet.
 * </p>
 *
 * @author Curtis Rueden
 */
@FunctionalInterface
public interface URLChangeReview {

	/**
	 * Decides which of the proposed changes to apply, by setting each one's
	 * approved flag. Whatever is approved when this returns is what gets
	 * applied.
	 *
	 * @param proposed the changes the published list proposes, which may be
	 *          empty.
	 */
	void review(List<URLChange> proposed);

	/** Approves nothing: compute the proposals and leave the installation be. */
	static URLChangeReview approveNone() {
		return proposed -> proposed.forEach(change -> change.setApproved(false));
	}

	/**
	 * Approves the changes the updater recommends: those to a site whose URL the
	 * user has not pinned.
	 */
	static URLChangeReview approveRecommended() {
		return proposed -> proposed.forEach( //
			change -> change.setApproved(change.isRecommended()));
	}

	/** Approves everything proposed, pinned URLs included. */
	static URLChangeReview approveAll() {
		return proposed -> proposed.forEach(change -> change.setApproved(true));
	}

	/** Leaves each proposal at the approval it was created with. */
	static URLChangeReview asProposed() {
		return proposed -> {};
	}
}
