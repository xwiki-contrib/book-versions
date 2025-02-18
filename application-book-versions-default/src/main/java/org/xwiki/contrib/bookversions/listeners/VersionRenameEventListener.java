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

package org.xwiki.contrib.bookversions.listeners;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.bookversions.BookVersionsManager;
import org.xwiki.contrib.bookversions.internal.BookVersionsConstants;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.observation.event.AbstractLocalEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.query.QueryException;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Updating the name of versioned content pages after a version rename.
 * 
 * @version $Id$
 * @since 1.0
 */
@Component
@Named(VersionRenameEventListener.NAME)
@Singleton
public class VersionRenameEventListener extends AbstractLocalEventListener
{

    static final String NAME = "org.xwiki.contrib.bookversions.listeners.VersionRenameEventListener";

    private static final List<Event> EVENT_LIST = List.of(new DocumentUpdatedEvent());

    @Inject
    private Provider<BookVersionsManager> bookVersionsManagerProvider;

    @Inject
    private Logger logger;

    @Inject
    private DocumentReferenceResolver<String> referenceResolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private AuthorizationManager authorizationManager;

    /**
     * Constructor.
     */
    public VersionRenameEventListener()
    {
        super(NAME, EVENT_LIST);
    }

    @Override
    public void processLocalEvent(Event event, Object source, Object data)
    {
        XWikiDocument updatedXDoc = (XWikiDocument) source;
        BookVersionsManager bookVersionsManager = bookVersionsManagerProvider.get();
        XWikiContext context = contextProvider.get();
        XWiki xwiki = context.getWiki();

        try {
            String newName = updatedXDoc.getTitle();
            String previousName = updatedXDoc.getOriginalDocument().getTitle();
            if (bookVersionsManager.isVersion(updatedXDoc) && !previousName.equals(newName)) {
                logger.debug("[VersionRenameEventListener] Version page [{}] renamed to [{}].", previousName, newName);
                DocumentReference collectionReference =
                    bookVersionsManager.getVersionedCollectionReference(updatedXDoc.getDocumentReference());
                List<String> pageReferences = bookVersionsManager.queryPages(collectionReference,
                    BookVersionsConstants.BOOKVERSIONEDCONTENT_CLASS_REFERENCE);
                String transformedPreviousName = bookVersionsManager.transformUsingSlugValidation(previousName);

                for (String pageReferenceString : pageReferences) {
                    DocumentReference pageReference = referenceResolver.resolve(pageReferenceString,
                        collectionReference);
                    if (transformedPreviousName.equals(pageReference.getName())
                        || previousName.equals(pageReference.getName()))
                    {
                        logger.debug("[VersionRenameEventListener] Page [{}] will be renamed.", pageReference);
                        DocumentReference targetPageReference =
                            new DocumentReference(bookVersionsManager.transformUsingSlugValidation(newName),
                            pageReference.getLastSpaceReference());

                        // Check if the user performing the move has edit rights on the new document reference.
                        if (!authorizationManager.hasAccess(Right.EDIT, context.getUserReference(),
                            targetPageReference))
                        {
                            logger.error("Cannot rename [{}] to [{}] as the current user [{}] has no edit rights on the"
                                    + " destination document", pageReference, targetPageReference,
                                context.getUserReference());
                            continue;
                        }

                        xwiki.renameDocument(pageReference, targetPageReference, true, Collections.emptyList(),
                            Collections.emptyList(), context);
                    }
                }
            }
        } catch (XWikiException | QueryException e) {
            logger.error("Could not handle the event listener.", e);
        }
    }
}
