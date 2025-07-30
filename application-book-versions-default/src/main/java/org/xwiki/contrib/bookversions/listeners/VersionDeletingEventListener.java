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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentDeletingEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.bookversions.BookVersionsManager;
import org.xwiki.contrib.bookversions.internal.BookVersionsConstants;
import org.xwiki.job.JobException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.observation.ObservationContext;
import org.xwiki.observation.event.AbstractLocalEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.query.QueryException;
import org.xwiki.refactoring.event.DocumentRenamingEvent;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Updating the name of versioned content pages after a version rename, and removing the related content.
 * 
 * @version $Id$
 * @since 1.2
 */
@Component
@Named(VersionDeletingEventListener.NAME)
@Singleton
public class VersionDeletingEventListener extends AbstractLocalEventListener
{

    static final String NAME = "org.xwiki.contrib.bookversions.listeners.VersionDeletingEventListener";

    private static final List<Event> EVENT_LIST = List.of(new DocumentDeletingEvent());

    private static final DocumentRenamingEvent DOCUMENT_RENAMING_EVENT = new DocumentRenamingEvent();

    @Inject
    private Provider<BookVersionsManager> bookVersionsManagerProvider;

    @Inject
    private Logger logger;

    @Inject
    private DocumentReferenceResolver<String> referenceResolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private ObservationContext observationContext;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    /**
     * Constructor.
     */
    public VersionDeletingEventListener()
    {
        super(NAME, EVENT_LIST);
    }

    @Override
    public void processLocalEvent(Event event, Object source, Object data)
    {
        XWikiDocument deletedXDoc = (XWikiDocument) source;
        BookVersionsManager bookVersionsManager = bookVersionsManagerProvider.get();
        XWikiContext context = contextProvider.get();
        XWiki xwiki = context.getWiki();

        try {
            DocumentReference deletedVersionReference = deletedXDoc.getDocumentReference();
            if (bookVersionsManager.isVersion(deletedXDoc.getOriginalDocument())
                && !this.observationContext.isIn(DOCUMENT_RENAMING_EVENT))
            {
                DocumentReference collectionReference =
                    bookVersionsManager.getVersionedCollectionReference(deletedVersionReference);
                DocumentReference newPrecedingVersionRef =
                    bookVersionsManager.getPreviousVersion(deletedVersionReference);
                UserReference userReference = userReferenceResolver.resolve(context.getUserReference());
                // Get all the versions using the deleted version as preceding version
                List<String> versions = bookVersionsManager.getCollectionVersions(collectionReference);
                for (String updateVersionRefString : versions) {
                    DocumentReference updateVersionReference = referenceResolver.resolve(updateVersionRefString,
                        collectionReference);
                    DocumentReference updatePrecedingVersionRef = bookVersionsManager.getPreviousVersion(
                        updateVersionReference);
                    if (updatePrecedingVersionRef != null && updatePrecedingVersionRef.equals(deletedVersionReference))
                    {
                        XWikiDocument updateVersion = xwiki.getDocument(updateVersionReference, context);
                        BaseObject xObject = updateVersion.getXObject(BookVersionsConstants.VERSION_CLASS_REFERENCE);
                        xObject.set(BookVersionsConstants.VERSION_PROP_PRECEDINGVERSION, newPrecedingVersionRef,
                            context);
                        updateVersion.getAuthors().setEffectiveMetadataAuthor(userReference);
                        updateVersion.getAuthors().setOriginalMetadataAuthor(userReference);
                        xwiki.saveDocument(updateVersion,
                            "Update preceding version after [" + deletedVersionReference + "] removal.", context);
                    }
                }
                // Remove the version's content
                bookVersionsManager.removeVersionContent(deletedVersionReference);
            }
        } catch (XWikiException | QueryException e) {
            logger.error("Could not handle the event listener.", e);
        } catch (JobException e) {
            logger.error("Could not handle the removal of version's content.", e);
        }
    }
}
