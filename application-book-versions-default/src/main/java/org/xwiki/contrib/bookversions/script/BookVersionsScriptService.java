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

package org.xwiki.contrib.bookversions.script;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.bookversions.BookVersionsManager;
import org.xwiki.job.JobException;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.QueryException;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.script.service.ScriptService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Book versions script service.
 *
 * @version $Id$
 * @since 0.1
 */
@Component
@Named(BookVersionsScriptService.ID)
@Singleton
public class BookVersionsScriptService implements ScriptService
{
    /**
     * The id of this script service.
     */
    public static final String ID = "bookversions";

    @Inject
    private Provider<BookVersionsManager> bookVersionsManagerProvider;

    /**
     * Check if the given reference is a book.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a book.
     * @throws XWikiException In case the system can't provide an answer.
     */
    public boolean isBook(DocumentReference documentReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isBook(documentReference);
    }

    /**
     * Check if the given document is a book.
     *
     * @param document The document.
     * @return True, if the given document is a book.
     * @throws XWikiException In case the system can't provide an answer.
     */
    public boolean isBook(XWikiDocument document) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isBook(document);
    }

    /**
     * Check if the given reference is a book page.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a book page.
     * @throws XWikiException In case the system can't provide an answer.
     */
    public boolean isPage(DocumentReference documentReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isPage(documentReference);
    }

    /**
     * Check if the given reference is a versioned book page.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a versioned book page.
     * @throws XWikiException In case the system can't provide an answer.
     */
    public boolean isVersionedPage(DocumentReference documentReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isVersionedPage(documentReference);
    }

    /**
     * Check if the given reference is a versioned content document.
     *
     * @param documentReference The document reference.
     * @return true if the given reference is a versioned content document.
     * @throws XWikiException
     */
    public boolean isVersionedContent(DocumentReference documentReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isVersionedContent(documentReference);
    }

    /**
     * Check if the given document is marked as deleted.
     *
     * @param documentReference The document reference
     * @return True if the document is marked as deleted
     * @throws XWikiException could occur if getDocument has an issue
     */
    public boolean isMarkedDeleted(DocumentReference documentReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isMarkedDeleted(documentReference);
    }

    /**
     * Check if the given document is marked as deleted.
     *
     * @param document The document
     * @return True if the document is marked as deleted
     */
    public boolean isMarkedDeleted(XWikiDocument document)
    {
        return bookVersionsManagerProvider.get().isMarkedDeleted(document);
    }

    /**
     * Check if the given document is a versioned content one.
     *
     * @param document The XWiki document.
     * @return true if the given document is a versioned content one.
     * @throws XWikiException
     */
    public boolean isVersionedContent(XWikiDocument document) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isVersionedContent(document);
    }

    /**
     * Check if the given reference is a possible versioned content reference in its collection (not checking if it
     * exists).
     *
     * @param collectionReference The collection reference.
     * @param documentReference The document reference.
     * @return true if the given reference is a possible versioned content reference in its collection.
     * @throws XWikiException
     */
    public boolean isPossibleVersionedContentReference(DocumentReference collectionReference,
        DocumentReference documentReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isPossibleVersionedContentReference(collectionReference,
            documentReference);
    }

    /**
     * Check if the given reference is a version.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a version.
     * @throws XWikiException In case the system can't provide an answer.
     */
    public boolean isVersion(DocumentReference documentReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isVersion(documentReference);
    }

    /**
     * Check if the given document is a version.
     *
     * @param document The document.
     * @return True, if the given document is a version.
     * @throws XWikiException In case the system can't provide an answer.
     */
    public boolean isVersion(XWikiDocument document) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isVersion(document);
    }

    /**
     * Transform the given name by using the slug name validation.
     *
     * @param name The name to be transformed.
     * @return the transformed.
     */
    public String transformUsingSlugValidation(String name)
    {
        return bookVersionsManagerProvider.get().transformUsingSlugValidation(name);
    }

    /**
     * Get the selected version that is stored in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @return the selected version.
     * @throws QueryException
     * @throws XWikiException
     */
    public String getSelectedVersion(DocumentReference documentReference) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getSelectedVersion(documentReference);
    }

    /**
     * Set the selected version in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @param version the version to be stored for the given collection.
     */
    public void setSelectedVersion(DocumentReference documentReference, String version)
    {
        bookVersionsManagerProvider.get().setSelectedVersion(documentReference, version);
    }

    /**
     * Get the selected version that is stored in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @return the selected version.
     * @throws QueryException
     * @throws XWikiException
     */
    public String getSelectedVariant(DocumentReference documentReference) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getSelectedVariant(documentReference);
    }

    /**
     * Get the variants the page belongs to.
     *
     * @param page the page document
     * @return the variants which the page belongs to
     */
    public List<DocumentReference> getPageVariants(XWikiDocument page)
    {
        return bookVersionsManagerProvider.get().getPageVariants(page);
    }

    /**
     * Set the selected variant in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @param variant the variant to be stored for the given collection.
     */
    public void setSelectedVariant(DocumentReference documentReference, String variant)
    {
        bookVersionsManagerProvider.get().setSelectedVariant(documentReference, variant);
    }

    /**
     * Get the selected language that is stored in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @return the selected version.
     * @throws QueryException
     * @throws XWikiException
     */
    public String getSelectedLanguage(DocumentReference documentReference) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getSelectedLanguage(documentReference);
    }

    /**
     * Set the selected language in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @param language the language to be stored for the given collection.
     */
    public void setSelectedLanguage(DocumentReference documentReference, String language)
    {
        bookVersionsManagerProvider.get().setSelectedLanguage(documentReference, language);
    }

    /**
     * Get the default translation for the given reference.
     *
     * @param documentReference The document reference.
     * @return The default translation.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    public String getDefaultTranslation(DocumentReference documentReference) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getDefaultTranslation(documentReference);
    }

    /**
     * Get the translation title for the given document, in the selected language.
     *
     * @param document The document.
     * @return the translation title for the given reference, for the selected language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    public String getTranslatedTitle(Document document) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getTranslatedTitle(document);
    }

    /**
     * Get the translation title for the given reference, in the selected language.
     *
     * @param documentReference The document reference.
     * @return the translation title for the given reference, for the selected language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    public String getTranslatedTitle(DocumentReference documentReference) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getTranslatedTitle(documentReference);
    }

    /**
     * Get the translated title for a document based on the selected language.
     *
     * @param document The document to get the translated title for
     * @return The translated title if found, null otherwise
     * @throws XWikiException
     * @throws QueryException
     */
    public String getTranslatedTitle(XWikiDocument document) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getTranslatedTitle(document);
    }

    /**
     * Get the translation title for the given reference, for the given language.
     *
     * @param documentReference The Document reference.
     * @param language The language to get the title of.
     * @return the translation title for the given reference, for the given language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    public String getTranslatedTitle(DocumentReference documentReference, String language)
        throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getTranslatedTitle(documentReference, language);
    }

    /**
     * Get the translation status for the given reference, for the given language.
     *
     * @param documentReference The Document reference.
     * @param language The language to get the status of.
     * @return the translation status for the given reference, for the given language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    public String getTranslationStatus(DocumentReference documentReference, String language)
        throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getTranslationStatus(documentReference, language);
    }

    /**
     * Check if a page is a nested page of another one, recursively.
     *
     * @param spaceReference the reference of the supposed space
     * @param nestedReference the reference of the supposed nested page
     * @return true if the space contains the nested space in one of its sub-spaces.
     */
    public boolean isAParent(DocumentReference spaceReference, DocumentReference nestedReference)
    {
        return bookVersionsManagerProvider.get().isAParent(spaceReference, nestedReference);
    }

    /**
     * Get the versioned collection (book or library) reference of a given page.
     *
     * @param pageReference the page from which to take the collection reference
     * @return the versioned collection reference, or null if the page is not part of a collection
     * @throws QueryException
     */
    public DocumentReference getVersionedCollectionReference(DocumentReference pageReference)
        throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getVersionedCollectionReference(pageReference);
    }

    /**
     * Get the name of the referenced version.
     *
     * @param versionReference The version reference.
     * @return the name of the referenced version.
     */
    public String getVersionName(DocumentReference versionReference)
    {
        return this.bookVersionsManagerProvider.get().getVersionName(versionReference);
    }

    /**
     * Get the name of the referenced variant.
     *
     * @param variantReference The version reference.
     * @return the name of the referenced version.
     */
    public String getVariantName(DocumentReference variantReference)
    {
        return this.bookVersionsManagerProvider.get().getVariantName(variantReference);
    }

    /**
     * Get the reference of a given version id, in the given referenced collection.
     *
     * @param collectionReference The reference of the collection (book / library).
     * @param version The version id.
     * @return the reference of a given version id, in the given referenced collection.
     * @throws XWikiException
     */
    public DocumentReference getVersionReference(DocumentReference collectionReference, String version)
        throws XWikiException
    {
        return this.bookVersionsManagerProvider.get().getVersionReference(collectionReference, version);
    }

    /**
     * Get the reference of a given varoamt id, in the given referenced collection.
     *
     * @param collectionReference The reference of the collection (book / library).
     * @param variant The variant id.
     * @return the reference of a given variant id, in the given referenced collection.
     * @throws XWikiException
     */
    public DocumentReference getVariantReference(DocumentReference collectionReference, String variant)
        throws XWikiException
    {
        return this.bookVersionsManagerProvider.get().getVariantReference(collectionReference, variant);
    }

    /**
     * Get the version references from a versioned collection (book or library).
     *
     * @param collectionReference the reference of the collection to get versions from
     * @return a list of versions references declared in the versioned collection, ordered by descending date. Returns
     *     an empty list if none are found.
     * @throws QueryException
     * @throws XWikiException
     */
    public List<String> getCollectionVersions(DocumentReference collectionReference)
        throws QueryException, XWikiException
    {
        return bookVersionsManagerProvider.get().getCollectionVersions(collectionReference);
    }

    /**
     * Get the variants references from a versioned collection (book library or variant).
     *
     * @param collectionReference the reference of the collection to get versions from
     * @return a list of variants references declared in the versioned collection, ordered by descending date. Returns
     *     an empty list if none are found.
     * @throws QueryException
     * @throws XWikiException
     */
    public List<String> getCollectionVariants(DocumentReference collectionReference)
        throws QueryException, XWikiException
    {
        return bookVersionsManagerProvider.get().getCollectionVariants(collectionReference);
    }

    /**
     * Get the reference of the page content corresponding to the given version. No inheritance is used for this
     * computation.
     *
     * @param documentReference The page reference.
     * @param version The version id.
     * @return the reference of the page content corresponding to the given version.
     * @throws QueryException
     * @throws XWikiException
     */
    public DocumentReference getVersionedContentReference(DocumentReference documentReference, String version)
        throws QueryException, XWikiException
    {
        return bookVersionsManagerProvider.get().getVersionedContentReference(documentReference, version);
    }

    /**
     * Check if the page has content corresponding to the given version.
     *
     * @param documentReference The page reference.
     * @param version The version id.
     * @return true if the page has content corresponding to the given version.
     * @throws QueryException
     * @throws XWikiException
     */
    public boolean hasContentForVersion(DocumentReference documentReference, String version)
        throws QueryException, XWikiException
    {
        return bookVersionsManagerProvider.get().hasContentForVersion(documentReference, version);
    }

    /**
     * Get the reference of a versioned page content. This does not check existence or page class.
     *
     * @param documentReference the reference of the (versioned content) page
     * @return the reference of the versioned page content
     * @throws XWikiException
     * @throws QueryException
     */
    public DocumentReference getInheritedVersionedContentReference(DocumentReference documentReference)
        throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getInheritedVersionedContentReference(documentReference);
    }

    /**
     * Get the reference of a versioned page content. This does not check existence or page class.
     *
     * @param pageReference the reference of the (versioned content) page
     * @param versionReference the reference of the version page
     * @return the reference of the versioned page content
     */
    public DocumentReference getVersionedContentReference(DocumentReference pageReference,
        DocumentReference versionReference)
    {
        return bookVersionsManagerProvider.get().getVersionedContentReference(pageReference, versionReference);
    }

    /**
     * Get the reference of the version of the content to be displayed, be it the required version, or inherited one.
     *
     * @param pageReference the reference of the page to get the content from
     * @param versionReference the reference of the version from which to get the content from, or inherit
     * @return the reference of the version of the content to be displayed. Null if there's no versioned content for the
     *     page, neither to inherit.
     * @throws QueryException
     * @throws XWikiException
     */
    public DocumentReference getInheritedContentVersionReference(DocumentReference pageReference,
        DocumentReference versionReference) throws QueryException, XWikiException
    {
        return bookVersionsManagerProvider.get().getInheritedContentVersionReference(pageReference, versionReference);
    }

    /**
     * Get the reference of the content to be displayed, be it corresponding to the required version, or inherited from
     * another version.
     *
     * @param pageReference the reference of the page to get the content from
     * @param versionReference the reference of the version from which to get the content from, or inherit
     * @return the reference of the content to be displayed. Null if there's no versioned content for the page, neither
     *     to inherit.
     * @throws QueryException
     * @throws XWikiException
     */
    public DocumentReference getInheritedContentReference(DocumentReference pageReference,
        DocumentReference versionReference) throws QueryException, XWikiException
    {
        return bookVersionsManagerProvider.get().getInheritedContentReference(pageReference, versionReference);
    }

    /**
     * Check if the given reference is a library.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a library.
     * @throws XWikiException In case the system can't provide an answer.
     */
    public boolean isLibrary(DocumentReference documentReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().isLibrary(documentReference);
    }

    /**
     * Set a library configuration to a book if it doesn't exit yet. The last version of the library is set as default.
     *
     * @param bookReference the reference of the book
     * @param libraryReference the reference of the library to add
     * @throws QueryException
     * @throws XWikiException
     */
    public void setLibrary(DocumentReference bookReference, DocumentReference libraryReference)
        throws QueryException, XWikiException
    {
        bookVersionsManagerProvider.get().setLibrary(bookReference, libraryReference);
    }

    /**
     * Set a library configuration to a book if it doesn't exit yet.
     *
     * @param bookReference the reference of the book
     * @param libraryReference the reference of the library to add
     * @param libraryVersionReference the reference of the version of the library to add
     * @throws QueryException
     * @throws XWikiException
     */
    public void setLibrary(DocumentReference bookReference, DocumentReference libraryReference,
        DocumentReference libraryVersionReference) throws QueryException, XWikiException
    {
        bookVersionsManagerProvider.get().setLibrary(bookReference, libraryReference, libraryVersionReference);
    }

    /**
     * Get the library version reference which is configured in the given book, for the given library, with the current
     * selected book version.
     *
     * @param bookReference the reference of the book
     * @param libraryReference the reference of the library
     * @return the reference of the library version configured in the book for the library
     * @throws XWikiException
     * @throws QueryException
     */
    public DocumentReference getConfiguredLibraryVersion(DocumentReference bookReference,
        DocumentReference libraryReference) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getConfiguredLibraryVersion(bookReference, libraryReference);
    }

    /**
     * Get the reference of the library content for the given key, depending on the configured library version in the
     * given book and the current selected book version.
     *
     * @param documentReference the reference of the book or the page
     * @param keyReference the reference of the key (library page)
     * @return the reference of the content of the library
     * @throws XWikiException
     * @throws QueryException
     */
    public DocumentReference getLinkedLibraryContentReference(DocumentReference documentReference,
        DocumentReference keyReference) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getLinkedLibraryContentReference(documentReference, keyReference);
    }

    /**
     * Get a list of used libraries in the given book.
     *
     * @param bookReference the reference of the book
     * @return the list of used library references
     * @throws XWikiException In case the isBook method has an issue.
     * @throws QueryException If any exception occurs while querying the database.
     */
    public List<DocumentReference> getUsedLibraries(DocumentReference bookReference)
        throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getUsedLibraries(bookReference);
    }

    /**
     * Get the published space for each of the libraries used in the given book.
     *
     * @param bookReference the reference of the book
     * @param versionReference the version of the book, which corresponds to a library's version in the book
     *     configuration
     * @return the published space reference of each library used in the book
     * @throws XWikiException In case a getDocument method or a check of type (isBook, ...) has an issue
     * @throws QueryException If any exception occurs while querying the database for the used libraries in the
     *     book.
     */
    public Map<DocumentReference, DocumentReference> getUsedPublishedLibraries(DocumentReference bookReference,
        DocumentReference versionReference) throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().getUsedPublishedLibraries(bookReference, versionReference);
    }

    /**
     * Switch the document between "Marked as Deleted" or not.
     *
     * @param documentReference The document reference
     * @throws XWikiException could occur if getDocument, newXObject or save have an issue
     */
    public void switchDeletedMark(DocumentReference documentReference) throws XWikiException
    {
        bookVersionsManagerProvider.get().switchDeletedMark(documentReference);
    }

    /**
     * Get the information for the publication preview.
     * @param configurationReference
     * @param userDocumentReference
     * @return the information for the publication preview
     * @throws XWikiException
     * @throws QueryException
     * @throws ComponentLookupException
     * @throws ParseException
     */
    public List<Map<String, Object>> previewPublication(DocumentReference configurationReference,
                                           DocumentReference userDocumentReference)
            throws XWikiException, QueryException, ComponentLookupException, ParseException
    {
        return bookVersionsManagerProvider.get().previewPublication(configurationReference,
            userDocumentReference);
    }

    /**
     * Execute the publication process with the provided configuration.
     *
     * @param configurationReference the configuration reference
     * @return the publication job's ID
     */
    public String publish(DocumentReference configurationReference) throws JobException
    {
        return bookVersionsManagerProvider.get().publish(configurationReference);
    }

    /**
     * Get the page status.
     *
     * @param page the page document
     * @return the status of the page
     */
    public String getPageStatus(XWikiDocument page)
    {
        return bookVersionsManagerProvider.get().getPageStatus(page);
    }

    /**
     * Check if the given language is the default one for the given reference.
     *
     * @param documentReference The document reference.
     * @param language The laguage.
     * @return true if the given language is the default one for the given reference.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    public boolean isDefaultLanguage(DocumentReference documentReference, String language)
        throws XWikiException, QueryException
    {
        return bookVersionsManagerProvider.get().isDefaultLanguage(documentReference, language);
    }

    /**
     * Get the list of languages configured for the given book.
     *
     * @param bookReference The document reference.
     * @return the list of languages.
     * @throws XWikiException In case the system can't provide an answer.
     */
    public List<String> getConfiguredLanguages(DocumentReference bookReference) throws XWikiException
    {
        return bookVersionsManagerProvider.get().getConfiguredLanguages(bookReference);
    }

    /**
     * Add a new Library reference class object into the specified version document.
     *
     * @param versionReference the document reference to add the object.
     * @throws XWikiException could occur if we can't load the referenced document.
     */
    public void addLibraryReferenceClassObject(DocumentReference versionReference) throws XWikiException
    {
        bookVersionsManagerProvider.get().addLibraryReferenceClassObject(versionReference);
    }

    /**
     * Remove the specific library reference class object into the specified version document.
     *
     * @param versionReference the document reference to remove the object.
     * @param objectNumber the object number to remove.
     * @throws XWikiException could occur if we can't load the referenced document.
     */
    public void removeLibraryReferenceClassObject(DocumentReference versionReference, int objectNumber)
        throws XWikiException
    {
        bookVersionsManagerProvider.get().removeLibraryReferenceClassObject(versionReference, objectNumber);
    }

    /**
     * Start a job to change page status on a list of page specified by reference.
     *
     * @param pageReferences pages references to set the news status.
     * @param namespaces namespace of the livedata
     * @param liveDataConfiguration the livedata configuration serialized in JSOn.
     * @param newStatus new status to set to the pages.
     * @return the job ID
     * @throws JobException the error in case of the job fail to start.
     * @throws JsonProcessingException in case of the livedataconfiguration is invalid
     */
    public String setPagesStatus(List<String> pageReferences, String namespaces,
        String liveDataConfiguration, String newStatus) throws JobException, JsonProcessingException
    {
        LiveDataConfiguration ldConfig =
            new ObjectMapper().readValue(liveDataConfiguration, LiveDataConfiguration.class);
        return bookVersionsManagerProvider.get()
            .setPagesStatus(pageReferences, namespaces, ldConfig, newStatus);
    }
}
