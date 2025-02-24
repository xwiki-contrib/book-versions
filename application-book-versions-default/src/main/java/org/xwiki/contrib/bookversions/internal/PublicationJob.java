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

import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.bookversions.BookVersionsManager;
import org.xwiki.contrib.bookversions.PublishBookRight;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.DefaultJobStatus;
import org.xwiki.job.DefaultRequest;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.model.reference.EntityReference;

/**
 * The job dedicated to publication of books and library.
 * @version $Id$
 * @since 0.1
 */
@Component
@Named(BookVersionsConstants.PUBLICATIONJOB_TYPE)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class PublicationJob extends AbstractJob<DefaultRequest, DefaultJobStatus<DefaultRequest>>
{
    @Inject
    private Provider<BookVersionsManager> bookVersionsManagerProvider;

    @Inject
    private AuthorizationManager authorizationManager;

    @Override
    public String getType()
    {
        return BookVersionsConstants.PUBLICATIONJOB_TYPE;
    }

    @Override
    protected void runInternal() throws Exception
    {
        DocumentReference configurationReference = this.request.getProperty("configurationReference");
        DocumentReference userReference = this.request.getProperty("userReference");
        Locale userLocale = this.request.getProperty("userLocale");

        // Verify that the user has the rights needed to execute the publication before actually publishing
        BookVersionsManager bookVersionsManager = bookVersionsManagerProvider.get();
        Map<String, Object> publicationConfiguration =
            bookVersionsManager.loadPublicationConfiguration(configurationReference);

        if (publicationConfiguration.containsKey(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE)
            && publicationConfiguration.containsKey(
                BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_DESTINATIONSPACE))
        {
            DocumentReference sourceReference = (DocumentReference) publicationConfiguration.get(
                BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE);
            EntityReference destinationReference = (EntityReference) publicationConfiguration.get(
                BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_DESTINATIONSPACE);

            if (authorizationManager.hasAccess(PublishBookRight.getRight(), userReference,
                sourceReference.getLastSpaceReference())
                && authorizationManager.hasAccess(PublishBookRight.getRight(), userReference, destinationReference))
            {
                bookVersionsManager.publishInternal(configurationReference, userReference, userLocale);
            } else if (!authorizationManager.hasAccess(PublishBookRight.getRight(), userReference, sourceReference)) {
                logger.error("User [{}] is missing book publication right on source book [{}]", userReference,
                    sourceReference);
            } else {
                logger.error("User [{}] is missing book publication right on destination space [{}]", userReference,
                    destinationReference);
            }
        } else {
            logger.error("Incomplete publication configuration found for [{}]", configurationReference);
        }
    }
}
