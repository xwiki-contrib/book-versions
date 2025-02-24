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

package org.xwiki.contrib.bookversions;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.xwiki.component.annotation.Role;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.job.JobException;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.query.QueryException;
import org.xwiki.rendering.parser.ParseException;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Book versions manager.
 *
 * @version $Id$
 * @since 0.1
 */
@Role
public interface BookVersionsManager
{
    /**
     * Check if the given reference is a book.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a book.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isBook(DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given document is a book.
     *
     * @param document The document.
     * @return True, if the given document is a book.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isBook(XWikiDocument document) throws XWikiException;

    /**
     * Check if the given reference is a book page.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a book page.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isPage(DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given document is a book page.
     *
     * @param document The XWiki document.
     * @return True, if the given reference is a book page.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isPage(XWikiDocument document) throws XWikiException;

    /**
     * Check if the given reference is a versioned book page.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a versioned book page.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isVersionedPage(DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given document is a versioned book page.
     *
     * @param document The XWiki document.
     * @return True, if the given document is a versioned book page.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isVersionedPage(XWikiDocument document) throws XWikiException;

    /**
     * Check if the given reference is a versioned content document.
     *
     * @param documentReference The document reference.
     * @return true if the given reference is a versioned content document.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isVersionedContent(DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given document is a versioned content one.
     *
     * @param document The XWiki document.
     * @return true if the given document is a versioned content one.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isVersionedContent(XWikiDocument document) throws XWikiException;

    /**
     * Check if the given reference is a possible versioned content reference in its collection (not checking if it
     * exists).
     *
     * @param collectionReference The collection reference.
     * @param documentReference The document reference.
     * @return true if the given reference is a possible versioned content reference in its collection.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isPossibleVersionedContentReference(DocumentReference collectionReference,
        DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given reference is a version.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a version.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isVersion(DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given document is a version.
     *
     * @param document The document.
     * @return True, if the given document is a version.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isVersion(XWikiDocument document) throws XWikiException;

    /**
     * Check if the given reference is a variant.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a variant.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isVariant(DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given reference is a library.
     *
     * @param documentReference The document reference.
     * @return True, if the given reference is a library.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isLibrary(DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given library version belongs to the given library.
     *
     * @param libraryReference The library reference
     * @param libraryVersionReference The library version reference
     * @return True if the library version belongs to the library
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isFromLibrary(DocumentReference libraryReference, DocumentReference libraryVersionReference)
        throws QueryException, XWikiException;

    /**
     * Check if the given library version belongs to the qiven library.
     *
     * @param library The library document
     * @param libraryVersion The library version document
     * @return True if the library version belongs to the library
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean isFromLibrary(XWikiDocument library, XWikiDocument libraryVersion) throws QueryException, XWikiException;

    /**
     * Check if the given document is marked as deleted.
     *
     * @param documentReference The document reference
     * @return True if the document is marked as deleted
     * @throws XWikiException could occur if getDocument has an issue
     */
    boolean isMarkedDeleted(DocumentReference documentReference) throws XWikiException;

    /**
     * Check if the given document is marked as deleted.
     *
     * @param document The document
     * @return True if the document is marked as deleted
     */
    boolean isMarkedDeleted(XWikiDocument document);

    /**
     * Transform the given name by using the slug name validation.
     *
     * @param name The name to be transformed.
     * @return the transformed name.
     */
    String transformUsingSlugValidation(String name);

    /**
     * Get the selected version that is stored in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @return the selected version.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    String getSelectedVersion(DocumentReference documentReference) throws XWikiException, QueryException;

    /**
     * Set the selected version in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @param version the version to be stored for the given collection.
     */
    void setSelectedVersion(DocumentReference documentReference, String version);

    /**
     * Get the selected variant that is stored in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @return the selected variant.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    String getSelectedVariant(DocumentReference documentReference) throws XWikiException, QueryException;

    /**
     * Get the variants the page belongs to.
     *
     * @param page the page document
     * @return the variants which the page belongs to
     */
    List<DocumentReference> getPageVariants(XWikiDocument page);

    /**
     * Set the selected variant in the session for the given collection (book / library).
     *
     * @param documentReference the document reference of the collection.
     * @param variant the variant to be stored for the given collection.
     */
    void setSelectedVariant(DocumentReference documentReference, String variant);

    /**
     * Get the selected language that is stored in the session for the given collection (book / library).
     *
     * @param documentReference the document reference.
     * @return the selected variant.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    String getSelectedLanguage(DocumentReference documentReference) throws XWikiException, QueryException;

    /**
     * Set the selected language in the session for the given collection (book / library).
     *
     * @param documentReference the document reference of the collection.
     * @param language the language to be stored for the given collection.
     */
    void setSelectedLanguage(DocumentReference documentReference, String language);

    /**
     * Get the default translation for the given reference.
     *
     * @param documentReference The document reference.
     * @return The default translation.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getDefaultTranslation(DocumentReference documentReference) throws XWikiException, QueryException;

    /**
     * Get the translated title for a document based on the selected language.
     *
     * @param document The document to get the translated title for
     * @return The translated title if found, null otherwise
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getTranslatedTitle(XWikiDocument document) throws XWikiException, QueryException;

    /**
     * Get the translation title for the given document.
     *
     * @param document The document.
     * @return the translation title for the given reference, for the selected language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getTranslatedTitle(Document document) throws XWikiException, QueryException;

    /**
     * Get the translation title for the given reference, for the selected language.
     *
     * @param documentReference The document reference.
     * @return the translation title for the given reference, for the selected language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getTranslatedTitle(DocumentReference documentReference) throws XWikiException, QueryException;

    /**
     * Get the translation title for the given reference, for the given language.
     *
     * @param documentReference The document reference.
     * @param language The language to get the title of.
     * @return the translation title for the given reference, for the given language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getTranslatedTitle(DocumentReference documentReference, String language)
        throws XWikiException, QueryException;

    /**
     * Get the translation title for the given document, for the given language.
     *
     * @param document The XWiki document.
     * @param language The language to get the title of.
     * @return the translation title for the given document, for the given language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getTranslatedTitle(XWikiDocument document, String language) throws XWikiException, QueryException;

    /**
     * Get the translation status for the given document.
     *
     * @param document The translated document.
     * @return The translation status for the given document.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getTranslationStatus(XWikiDocument document) throws XWikiException, QueryException;

    /**
     * Get the translation status for the given reference, for the given language.
     *
     * @param documentReference The Document reference.
     * @param language The language to get the status of.
     * @return the translation status for the given reference, for the given language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getTranslationStatus(DocumentReference documentReference, String language)
        throws XWikiException, QueryException;

    /**
     * Get the translation status for the given document, for the given language.
     *
     * @param document The XWiki document.
     * @param language The language to get the status of.
     * @return the translation status for the given document, for the given language.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    String getTranslationStatus(XWikiDocument document, String language) throws XWikiException, QueryException;

    /**
     * Check if a page is a nested page of another one, recursively.
     *
     * @param documentReference the reference of the supposed space
     * @param nestedReference the reference of the supposed nested page
     * @return true if the space contains the nested space in one of its sub-spaces.
     */
    boolean isAParent(DocumentReference documentReference, DocumentReference nestedReference);

    /**
     * Get the versioned collection (book or library) reference of a given page.
     *
     * @param documentReference the page from which to take the collection reference
     * @return the versioned collection reference, or null if the page is not part of a collection
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    DocumentReference getVersionedCollectionReference(DocumentReference documentReference)
        throws XWikiException, QueryException;

    /**
     * Query pages under a given document, having a given class, ordered by descending creation date.
     *
     * @param documentReference the root document for the query
     * @param classReference the class to select pages from
     * @return the list of pages with the class, under the document, ordered by descending creation date
     * @throws QueryException happens when the query creation or execution has an issue.
     */
    List<String> queryPages(DocumentReference documentReference, EntityReference classReference)
        throws QueryException;

    /**
     * Get the versioned collection (book or library) reference of a given page.
     *
     * @param document the page from which to take the collection reference
     * @return the versioned collection reference, or null if the page is not part of a collection
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    DocumentReference getVersionedCollectionReference(Document document) throws XWikiException, QueryException;

    /**
     * Get the name of the referenced version.
     *
     * @param versionReference The version reference.
     * @return the name of the referenced version.
     */
    String getVersionName(DocumentReference versionReference);

    /**
     * Get the name of the referenced variant.
     *
     * @param variantReference The version reference.
     * @return the name of the referenced version.
     */
    String getVariantName(DocumentReference variantReference);

    /**
     * Get the reference of a given version id, in the given referenced collection.
     *
     * @param collectionReference The reference of the collection (book / library).
     * @param version The version id.
     * @return the reference of a given version id, in the given referenced collection.
     * @throws XWikiException In case the system can't provide an answer.
     */
    DocumentReference getVersionReference(DocumentReference collectionReference, String version) throws XWikiException;

    /**
     * Get the reference of a given variant id, in the given referenced collection.
     *
     * @param collectionReference The reference of the collection (book / library).
     * @param variant The variant id.
     * @return the reference of a given version id, in the given referenced collection.
     * @throws XWikiException In case the system can't provide an answer.
     */
    DocumentReference getVariantReference(DocumentReference collectionReference, String variant) throws XWikiException;

    /**
     * Get the version references from a versioned collection (book or library.
     *
     * @param documentReference the reference of the collection to get versions from
     * @return a list of versions references declared in the versioned collection, ordered by descending date. Returns
     *     an empty list if none are found.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    List<String> getCollectionVersions(DocumentReference documentReference) throws QueryException, XWikiException;

    /**
     * Get the variant references from a versioned collection (book or library.
     *
     * @param documentReference the reference of the collection to get variants from
     * @return a list of variants references declared in the versioned collection, ordered by descending date. Returns
     *     an empty list if none are found.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    List<String> getCollectionVariants(DocumentReference documentReference) throws QueryException, XWikiException;

    /**
     * Get the reference of the page content corresponding to the given version. No inheritance is used for this
     * computation.
     *
     * @param documentReference The page reference.
     * @param version The version id.
     * @return the reference of the page content corresponding to the given version.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    DocumentReference getVersionedContentReference(DocumentReference documentReference, String version)
        throws QueryException, XWikiException;

    /**
     * Check if the page has content corresponding to the given version.
     *
     * @param documentReference The page reference.
     * @param version The version id.
     * @return true if the page has content corresponding to the given version.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    boolean hasContentForVersion(DocumentReference documentReference, String version)
        throws QueryException, XWikiException;

    /**
     * Get versioned content reference. The method is used at page content creation.
     *
     * @param documentReference the reference of the book page
     * @return the versioned content reference
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    DocumentReference getVersionedContentReference(DocumentReference documentReference)
        throws XWikiException, QueryException;

    /**
     * Get versioned content reference for the given document.
     *
     * @param document The XWiki document
     * @return the versioned content reference
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    DocumentReference getVersionedContentReference(XWikiDocument document) throws XWikiException, QueryException;

    /**
     * Get the reference of a versioned page content. This does not check existence or page class.
     *
     * @param documentReference the reference of the (versioned content) page
     * @return the reference of the versioned page content
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    DocumentReference getInheritedVersionedContentReference(DocumentReference documentReference)
        throws XWikiException, QueryException;

    /**
     * Get the reference of a versioned page content. This does not check existence or page class.
     *
     * @param documentReference the reference of the (versioned content) page
     * @param versionReference the reference of the version page
     * @return the reference of the versioned page content
     */
    DocumentReference getVersionedContentReference(DocumentReference documentReference,
        DocumentReference versionReference);

    /**
     * Get the reference of the version of the content to be displayed, be it the required version, or inherited one.
     *
     * @param documentReference the reference of the page to get the content from
     * @param versionReference the reference of the version from which to get the content from, or inherit
     * @return the reference of the version of the content to be displayed. Null if there's no versioned content for the
     *     page, neither to inherit.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    DocumentReference getInheritedContentVersionReference(DocumentReference documentReference,
        DocumentReference versionReference) throws QueryException, XWikiException;

    /**
     * Get the reference of the content to be displayed, be it corresponding to the required version, or inherited from
     * another version.
     *
     * @param documentReference the reference of the page to get the content from
     * @param versionReference the reference of the version from which to get the content from, or inherit
     * @return the reference of the content to be displayed. Null if there's no versioned content for the page, neither
     *     to inherit.
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    DocumentReference getInheritedContentReference(DocumentReference documentReference,
        DocumentReference versionReference) throws QueryException, XWikiException;

    /**
     * Set a library configuration to a book if it doesn't exit yet. The last version of the library is set as default.
     *
     * @param bookReference the reference of the book
     * @param libraryReference the reference of the library to add
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    void setLibrary(DocumentReference bookReference, DocumentReference libraryReference)
        throws QueryException, XWikiException;

    /**
     * Set a library configuration to a book if it doesn't exit yet.
     *
     * @param bookReference the reference of the book
     * @param libraryReference the reference of the library to add
     * @param libraryVersionReference the reference of the version of the library to add
     * @throws QueryException If any exception occurs while querying the database.
     * @throws XWikiException In case the system can't provide an answer.
     */
    void setLibrary(DocumentReference bookReference, DocumentReference libraryReference,
        DocumentReference libraryVersionReference) throws QueryException, XWikiException;

    /**
     * Get the library version reference which is configured in the given book, for the given library, with the current
     * selected book version.
     *
     * @param bookReference the reference of the book
     * @param libraryReference the reference of the library
     * @return the reference of the library version configured in the book for the library
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    DocumentReference getConfiguredLibraryVersion(DocumentReference bookReference, DocumentReference libraryReference)
        throws XWikiException, QueryException;

    /**
     * Get the reference of the library content for the given key, depending on the configured library version in the
     * given book and the current selected book version.
     *
     * @param documentReference the reference of the book or the page
     * @param keyReference the reference of the key (library page)
     * @return the reference of the content of the library
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    DocumentReference getLinkedLibraryContentReference(DocumentReference documentReference,
        DocumentReference keyReference) throws XWikiException, QueryException;

    /**
     * Get a list of used libraries in the given book.
     *
     * @param bookReference the reference of the book
     * @return the list of used library references
     * @throws XWikiException In case the isBook method has an issue.
     * @throws QueryException If any exception occurs while querying the database.
     */
    List<DocumentReference> getUsedLibraries(DocumentReference bookReference) throws XWikiException, QueryException;

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
    Map<DocumentReference, DocumentReference> getUsedPublishedLibraries(DocumentReference bookReference,
        DocumentReference versionReference)
        throws XWikiException, QueryException;

    /**
     * Switch the document between "Marked as Deleted" or not.
     *
     * @param documentReference The document reference
     * @throws XWikiException could occur if getDocument, newXObject or save have an issue
     */
    void switchDeletedMark(DocumentReference documentReference) throws XWikiException;

    /**
     * Execute the publication process with the provided configuration.
     *
     * @param configurationReference the configuration reference
     * @return the publication job's ID
     * @throws JobException if an error occurs while manipulating the publication job
     */
    String publish(DocumentReference configurationReference) throws JobException;

    /**
     * Load the configuration for the publication saved in a document.
     *
     * @param configurationReference The document containing the configuration for the publication.
     * @return a map containing the configuration for the publication.
     * @throws XWikiException if an error occurs while getting the document.
     */
    Map<String, Object> loadPublicationConfiguration(DocumentReference configurationReference)
        throws XWikiException;

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
    List<Map<String, Object>> previewPublication(DocumentReference configurationReference,
                                           DocumentReference userDocumentReference)
            throws XWikiException, QueryException, ComponentLookupException, ParseException;

    /**
     * Execute the publication process with the provided configuration.
     *
     * @param configurationReference The configuration reference
     * @param userReference the user executing the job
     * @param userLocale the user's locale
     * @throws XWikiException could occur if loadPublicationConfiguration has an issue
     * @throws QueryException If any exception occurs while querying the database.
     */
    void publishInternal(DocumentReference configurationReference, DocumentReference userReference, Locale userLocale)
        throws XWikiException, QueryException, ComponentLookupException, ParseException;

    /**
     * Get the page status.
     *
     * @param page the page document
     * @return the status of the page
     */
    String getPageStatus(XWikiDocument page);

    /**
     * Get the data about page translations.
     *
     * @param document The XWiki document.
     * @return the data about page translations.
     */
    Map<String, Map<String, Object>> getLanguageData(XWikiDocument document);

    /**
     * Update the language data in the document.
     *
     * @param document The XWiki document.
     * @param languageData The language data.
     */
    void setLanguageData(XWikiDocument document, Map<String, Map<String, Object>> languageData);

    /**
     * Check if the given language is the default one for the given reference.
     *
     * @param documentReference The document reference.
     * @param language The laguage.
     * @return true if the given language is the default one for the given reference.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    boolean isDefaultLanguage(DocumentReference documentReference, String language)
        throws XWikiException, QueryException;

    /**
     * Check if the given language is set to be the default one.
     *
     * @param document The XWiki document.
     * @param language The lanugage.
     * @return true if the given language is set to be the default one.
     * @throws XWikiException In case the system can't provide an answer.
     * @throws QueryException If any exception occurs while querying the database.
     */
    boolean isDefaultLanguage(XWikiDocument document, String language) throws XWikiException, QueryException;

    /**
     * Remove all translation objects from the given document.
     *
     * @param document The XWiki document.
     */
    void resetTranslations(XWikiDocument document);

    /**
     * Get the list of languages configured for the given book.
     *
     * @param bookReference The document reference.
     * @return the list of languages.
     * @throws XWikiException In case the system can't provide an answer.
     */
    List<String> getConfiguredLanguages(DocumentReference bookReference) throws XWikiException;

    /**
     * Add a new Library reference class object into the specified version document.
     *
     * @param versionReference the document reference to add the object.
     * @throws XWikiException could occur if we can't load the referenced document.
     */
    void addLibraryReferenceClassObject(DocumentReference versionReference) throws XWikiException;

    /**
     * Remove the specific library reference class object into the specified version document.
     *
     * @param versionReference the document reference to remove the object.
     * @param objectNumber the object number to remove.
     * @throws XWikiException could occur if we can't load the referenced document.
     */
    void removeLibraryReferenceClassObject(DocumentReference versionReference, int objectNumber) throws XWikiException;

    /**
     * Start a job to change page status on a list of page specified by reference.
     *
     * @param pageReferences pages references to set the news status.
     * @param namespaces namespace of the livedata
     * @param liveDataConfiguration the livedata configuration
     * @param newStatus new status to set to the pages.
     * @return the job ID
     * @throws JobException the error in case of the job fail to start.
     */
    String setPagesStatus(List<String> pageReferences, String namespaces,
        LiveDataConfiguration liveDataConfiguration, String newStatus) throws JobException;
}
