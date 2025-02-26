/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.bookversions.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.bookversions.BookVersionsManager;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.DefaultJobStatus;
import org.xwiki.job.DefaultRequest;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;

/**
 * The job dedicated to removal of version's content of books and library.
 * @version $Id$
 * @since 1.2
 */
@Component
@Named(BookVersionsConstants.VERSIONCONTENTREMOVEJOB_TYPE)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class VersionContentRemoveJob extends AbstractJob<DefaultRequest, DefaultJobStatus<DefaultRequest>>
{
    @Inject
    private Provider<BookVersionsManager> bookVersionsManagerProvider;

    @Inject
    private AuthorizationManager authorizationManager;

    @Override
    public String getType()
    {
        return BookVersionsConstants.VERSIONCONTENTREMOVEJOB_TYPE;
    }

    @Override
    protected void runInternal() throws Exception
    {
        BookVersionsManager bookVersionsManager = bookVersionsManagerProvider.get();

        DocumentReference versionReference = this.request.getProperty("versionReference");
        DocumentReference userReference = this.request.getProperty("userReference");
        DocumentReference collectionReference = bookVersionsManager.getVersionedCollectionReference(versionReference);

        if (authorizationManager.hasAccess(Right.DELETE, userReference, collectionReference.getLastSpaceReference())) {
            bookVersionsManager.removeVersionContentInternal(versionReference, userReference);
        } else {
            logger.error("User [{}] is missing DELETE right on book/library [{}]", userReference, collectionReference);
        }
    }
}
