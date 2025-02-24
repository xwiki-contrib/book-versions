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

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.bookversions.BookVersionsManager;
import org.xwiki.contrib.bookversions.PageTranslationStatus;
import org.xwiki.contrib.bookversions.internal.batch.LiveDataBatchRequest;
import org.xwiki.job.DefaultRequest;
import org.xwiki.job.JobException;
import org.xwiki.job.JobExecutor;
import org.xwiki.job.event.status.JobProgressManager;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.localization.LocalizationManager;
import org.xwiki.logging.LogLevel;
import org.xwiki.logging.event.LogEvent;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.SpaceReferenceResolver;
import org.xwiki.model.validation.EntityNameValidation;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.doc.merge.MergeConfiguration;
import com.xpn.xwiki.doc.merge.MergeResult;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Default implementation of {@link BookVersionsManager}.
 *
 * @version $Id$
 * @since 0.1
 */
@Component
@Singleton
public class DefaultBookVersionsManager implements BookVersionsManager
{
    private static final ClassBlockMatcher MACRO_MATCHER = new ClassBlockMatcher(MacroBlock.class);

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Provider<QueryManager> queryManagerProvider;

    @Inject
    private DocumentReferenceResolver<String> referenceResolver;

    @Inject
    private SpaceReferenceResolver<String> spaceReferenceResolver;

    @Inject
    @Named("currentmixed")
    private DocumentReferenceResolver<String> currentMixedReferenceResolver;

    @Inject
    @Named("explicit")
    private DocumentReferenceResolver<String> currentExplicitReferenceResolver;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
    @Named("SlugEntityNameValidation")
    private Provider<EntityNameValidation> slugEntityNameValidationProvider;

    @Inject
    private Logger logger;

    @Inject
    private JobExecutor jobExecutor;

    @Inject
    private JobProgressManager progressManager;

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private BookPublicationReferencesTransformationHelper publicationReferencesTransformationHelper;

    @Inject
    private LocalizationManager localization;

    @Override
    public boolean isBook(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isBook(this.getXWikiContext().getWiki().getDocument(documentReference, xcontext));
    }

    @Override
    public boolean isBook(XWikiDocument document) throws XWikiException
    {
        return new DefaultBook(document).isDefined();
    }

    @Override
    public boolean isPage(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isPage(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    @Override
    public boolean isPage(XWikiDocument document) throws XWikiException
    {
        return new DefaultPage(document).isDefined();
    }

    @Override
    public boolean isVersionedPage(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isVersionedPage(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    @Override
    public boolean isVersionedPage(XWikiDocument document) throws XWikiException
    {
        return new DefaultPage(document).isVersioned();
    }

    @Override
    public boolean isVersionedContent(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isVersionedContent(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    @Override
    public boolean isVersionedContent(XWikiDocument document) throws XWikiException
    {
        return new DefaultVersionedContent(document).isDefined();
    }

    @Override
    public boolean isPossibleVersionedContentReference(DocumentReference collectionReference,
        DocumentReference documentReference) throws XWikiException
    {
        return getVersionReference(collectionReference, documentReference.getName()) != null;
    }

    @Override
    public boolean isVersion(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isVersion(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    @Override
    public boolean isVersion(XWikiDocument document) throws XWikiException
    {
        return new DefaultVersion(document).isDefined();
    }

    @Override
    public boolean isVariant(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isVariant(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    private boolean isVariant(XWikiDocument document) throws XWikiException
    {
        return new DefaultVariant(document).isDefined();
    }

    @Override
    public boolean isLibrary(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isLibrary(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    private boolean isLibrary(XWikiDocument document) throws XWikiException
    {
        return new DefaultLibrary(document).isDefined();
    }

    @Override
    public boolean isFromLibrary(DocumentReference libraryReference, DocumentReference libraryVersionReference)
        throws QueryException, XWikiException
    {
        for (String versionStringRef : getCollectionVersions(libraryReference)) {
            if (versionStringRef != null && libraryVersionReference != null && libraryVersionReference
                .equals(referenceResolver.resolve(versionStringRef, libraryVersionReference)))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isFromLibrary(XWikiDocument library, XWikiDocument libraryVersion)
        throws QueryException, XWikiException
    {
        return library != null && libraryVersion != null
            ? isFromLibrary(library.getDocumentReference(), libraryVersion.getDocumentReference()) : false;
    }

    @Override
    public boolean isMarkedDeleted(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isMarkedDeleted(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    @Override
    public boolean isMarkedDeleted(XWikiDocument document)
    {
        return document != null && document.getXObject(BookVersionsConstants.MARKEDDELETED_CLASS_REFERENCE) != null;
    }

    @Override
    public String transformUsingSlugValidation(String name)
    {
        EntityNameValidation modelValidationScriptService = this.slugEntityNameValidationProvider.get();

        return name != null ? modelValidationScriptService.transform(name) : null;
    }

    @Override
    public String getSelectedVersion(DocumentReference documentReference) throws XWikiException, QueryException
    {
        if (documentReference == null) {
            return null;
        }

        DocumentReference versionedCollectionReference = getVersionedCollectionReference(documentReference);
        Map<String, String> versionsMap = new HashMap<String, String>();
        XWikiRequest request = getXWikiContext().getRequest();
        HttpSession session = request.getSession();
        if (session != null) {
            versionsMap = (Map<String, String>) session.getAttribute(BookVersionsConstants.SESSION_SELECTEDVERSION);

            if (versionsMap != null) {
                Iterator<?> it = versionsMap.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<String, String> collectionVersion = (Map.Entry<String, String>) it.next();
                    String collectionReference = collectionVersion.getKey();
                    if (collectionReference != null && !collectionReference.isBlank()
                        && collectionReference.equals(localSerializer.serialize(versionedCollectionReference)))
                    {
                        return collectionVersion.getValue();
                    }
                }
            }
        }

        List<String> collectionVersions = getCollectionVersions(documentReference);
        if (collectionVersions != null && collectionVersions.size() > 0) {
            return collectionVersions.get(0);
        }

        return null;
    }

    @Override
    public void setSelectedVersion(DocumentReference documentReference, String version)
    {
        if (documentReference == null) {
            return;
        }

        Map<String, String> versionsMap = new HashMap<String, String>();
        XWikiRequest request = getXWikiContext().getRequest();
        HttpSession session = request.getSession();
        if (session != null) {
            Object sessionAttribute = session.getAttribute(BookVersionsConstants.SESSION_SELECTEDVERSION);
            versionsMap = sessionAttribute != null ? (Map<String, String>) sessionAttribute : versionsMap;
            String collectionReferenceSerialized = localSerializer.serialize(documentReference);
            versionsMap.put(collectionReferenceSerialized, version);
            session.setAttribute(BookVersionsConstants.SESSION_SELECTEDVERSION, versionsMap);
        }
    }

    @Override
    public String getSelectedVariant(DocumentReference documentReference) throws XWikiException, QueryException
    {
        if (documentReference == null) {
            return null;
        }

        Map<String, String> variantsMap = new HashMap<String, String>();
        XWikiRequest request = getXWikiContext().getRequest();
        HttpSession session = request.getSession();
        if (session != null) {
            variantsMap = (Map<String, String>) session.getAttribute(BookVersionsConstants.SESSION_SELECTEDVARIANT);

            if (variantsMap != null) {
                Iterator<?> it = variantsMap.entrySet().iterator();
                DocumentReference versionedCollectionReference = getVersionedCollectionReference(documentReference);

                while (it.hasNext()) {
                    Map.Entry<String, String> collectionVariant = (Map.Entry<String, String>) it.next();
                    String collectionReference = collectionVariant.getKey();
                    if (collectionReference != null && !collectionReference.isBlank()
                        && collectionReference.equals(localSerializer.serialize(versionedCollectionReference)))
                    {
                        return collectionVariant.getValue();
                    }
                }
            }
        }

        return null;
    }

    @Override
    public List<DocumentReference> getPageVariants(XWikiDocument page)
    {
        List<DocumentReference> result = new ArrayList<DocumentReference>();
        if (page == null) {
            return result;
        }
        BaseObject variantListObj = page.getXObject(BookVersionsConstants.VARIANTLIST_CLASS_REFERENCE);
        if (variantListObj == null) {
            return result;
        }
        for (String referenceString : (List<String>) variantListObj
            .getListValue(BookVersionsConstants.VARIANTLIST_PROP_VARIANTSLIST)) {
            result.add(referenceResolver.resolve(referenceString, page.getDocumentReference()));
        }
        return result;
    }

    @Override
    public void setSelectedVariant(DocumentReference documentReference, String variant)
    {
        if (documentReference == null) {
            return;
        }

        Map<String, String> variantsMap = new HashMap<String, String>();
        XWikiRequest request = getXWikiContext().getRequest();
        HttpSession session = request.getSession();
        if (session != null) {
            Object sessionAttribute = session.getAttribute(BookVersionsConstants.SESSION_SELECTEDVARIANT);
            variantsMap = sessionAttribute != null ? (Map<String, String>) sessionAttribute : variantsMap;
            String collectionReferenceSerialized = localSerializer.serialize(documentReference);
            variantsMap.put(collectionReferenceSerialized, variant);
            session.setAttribute(BookVersionsConstants.SESSION_SELECTEDVARIANT, variantsMap);
        }
    }

    @Override
    public String getSelectedLanguage(DocumentReference documentReference) throws XWikiException, QueryException
    {
        if (documentReference == null) {
            return null;
        }

        Map<String, String> languagesMap = new HashMap<String, String>();
        XWikiRequest request = getXWikiContext().getRequest();
        HttpSession session = request.getSession();
        if (session != null) {
            languagesMap = (Map<String, String>) session.getAttribute(BookVersionsConstants.SESSION_SELECTEDLANGUAGE);

            if (languagesMap != null) {
                Iterator<?> it = languagesMap.entrySet().iterator();
                DocumentReference versionedCollectionReference = getVersionedCollectionReference(documentReference);

                while (it.hasNext()) {
                    Map.Entry<String, String> collectionLanguage = (Map.Entry<String, String>) it.next();
                    String collectionReference = collectionLanguage.getKey();
                    if (collectionReference != null && !collectionReference.isBlank()
                        && collectionReference.equals(localSerializer.serialize(versionedCollectionReference)))
                    {
                        return collectionLanguage.getValue();
                    }
                }
            }
        }

        return null;
    }

    private String getSelectedLanguage(Document document) throws XWikiException, QueryException
    {
        if (document == null) {
            return null;
        }

        DocumentReference documentReference = document.getDocumentReference();

        Map<String, String> languagesMap = new HashMap<String, String>();
        XWikiRequest request = getXWikiContext().getRequest();
        HttpSession session = request.getSession();
        if (session != null) {
            languagesMap = (Map<String, String>) session.getAttribute(BookVersionsConstants.SESSION_SELECTEDLANGUAGE);

            if (languagesMap != null) {
                Iterator<?> it = languagesMap.entrySet().iterator();
                DocumentReference versionedCollectionReference = getVersionedCollectionReference(document);

                while (it.hasNext()) {
                    Map.Entry<String, String> collectionLanguage = (Map.Entry<String, String>) it.next();
                    String collectionReference = collectionLanguage.getKey();
                    if (collectionReference != null && !collectionReference.isBlank()
                        && collectionReference.equals(localSerializer.serialize(versionedCollectionReference)))
                    {
                        return collectionLanguage.getValue();
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void setSelectedLanguage(DocumentReference documentReference, String language)
    {
        if (documentReference == null) {
            return;
        }

        Map<String, String> languagesMap = new HashMap<String, String>();
        XWikiRequest request = getXWikiContext().getRequest();
        HttpSession session = request.getSession();
        if (session != null) {
            Object sessionAttribute = session.getAttribute(BookVersionsConstants.SESSION_SELECTEDLANGUAGE);
            languagesMap = sessionAttribute != null ? (Map<String, String>) sessionAttribute : languagesMap;
            String collectionReferenceSerialized = localSerializer.serialize(documentReference);
            languagesMap.put(collectionReferenceSerialized, language);
            session.setAttribute(BookVersionsConstants.SESSION_SELECTEDLANGUAGE, languagesMap);
        }
    }

    @Override
    public String getDefaultTranslation(DocumentReference documentReference) throws XWikiException, QueryException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return getDefaultTranslation(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    private String getDefaultTranslation(Document document) throws XWikiException, QueryException
    {
        Map<String, Map<String, Object>> languageData = getLanguageData(document);

        for (Entry<String, Map<String, Object>> languageDataEntry : languageData.entrySet()) {
            if (languageDataEntry != null && languageDataEntry.getValue() != null
                && (boolean) languageDataEntry.getValue().get(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT))
            {
                return (String) languageDataEntry.getKey();
            }
        }

        return null;
    }

    private String getDefaultTranslation(XWikiDocument document) throws XWikiException, QueryException
    {
        Map<String, Map<String, Object>> languageData = getLanguageData(document);

        for (Entry<String, Map<String, Object>> languageDataEntry : languageData.entrySet()) {
            if (languageDataEntry != null && languageDataEntry.getValue() != null
                && (boolean) languageDataEntry.getValue().get(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT))
            {
                return (String) languageDataEntry.getKey();
            }
        }

        return null;
    }

    @Override
    public String getTranslatedTitle(Document document) throws XWikiException, QueryException
    {
        String selectedLanguage = getSelectedLanguage(document);

        if (selectedLanguage == null) {
            selectedLanguage = this.getDefaultTranslation(document);
        }

        return selectedLanguage != null ? getTranslatedTitle(document, selectedLanguage)
            : document.getDocumentReference().getName();
    }

    @Override
    public String getTranslatedTitle(DocumentReference documentReference) throws XWikiException, QueryException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return getTranslatedTitle(xcontext.getWiki().getDocument(documentReference, xcontext));
    }

    @Override
    public String getTranslatedTitle(XWikiDocument document) throws XWikiException, QueryException
    {
        DocumentReference collectionRef = getVersionedCollectionReference(document.getDocumentReference());
        String selectedLanguage = getSelectedLanguage(collectionRef);

        if (selectedLanguage == null) {
            selectedLanguage = this.getDefaultTranslation(document);
        }

        return selectedLanguage != null ? getTranslatedTitle(document, selectedLanguage)
            : document.getDocumentReference().getName();
    }

    @Override
    public String getTranslatedTitle(DocumentReference documentReference, String language)
        throws XWikiException, QueryException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return getTranslatedTitle(xcontext.getWiki().getDocument(documentReference, xcontext), language);
    }

    @Override
    public String getTranslatedTitle(XWikiDocument document, String language) throws XWikiException, QueryException
    {
        if (document == null) {
            return null;
        }

        String title = null;
        for (BaseObject tObj : document.getXObjects(BookVersionsConstants.PAGETRANSLATION_CLASS_REFERENCE)) {
            if (tObj != null) {
                String languageEntry = tObj.getStringValue(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);
                if (languageEntry != null && !languageEntry.isEmpty() && languageEntry.equals(language)) {
                    title = tObj.getStringValue(BookVersionsConstants.PAGETRANSLATION_TITLE);
                }
            }
        }

        return title != null && !title.isEmpty() ? title : getInheritedTitle(document);
    }

    private String getTranslatedTitle(Document document, String language) throws XWikiException, QueryException
    {
        if (document == null || language == null || language.isEmpty()) {
            return null;
        }

        String title = null;
        com.xpn.xwiki.api.Object tObj = document.getObject(BookVersionsConstants.PAGE_TRANSLATION_CLASS_SERIALIZED,
            BookVersionsConstants.PAGETRANSLATION_LANGUAGE, language);
        if (tObj != null) {
            title = (String) tObj.getValue(BookVersionsConstants.PAGETRANSLATION_TITLE);
        }

        return title != null && !title.isEmpty() ? title : getInheritedTitle(document);
    }

    private String getInheritedTitle(Document document) throws XWikiException
    {
        if (document.getObject(BookVersionsConstants.BOOKVERSIONEDCONTENT_CLASS_SERIALIZED) != null) {

            DocumentReference documentReference = document.getDocumentReference();
            SpaceReference parentSpaceReference = getSpaceReference(documentReference);
            if (parentSpaceReference != null) {
                DocumentReference parentPageReference = new DocumentReference("WebHome", parentSpaceReference);
                // The parent is a book page, so use its title.
                Document parentDocument = new Document(new XWikiDocument(parentPageReference), getXWikiContext());
                if (parentDocument.getObject(BookVersionsConstants.BOOKPAGE_CLASS_SERIALIZED) != null) {
                    return parentDocument.getTitle();
                }
            }
        }

        return document != null ? document.getDocumentReference().getName() : null;
    }

    private String getInheritedTitle(XWikiDocument document) throws XWikiException
    {
        if (isVersionedContent(document)) {

            DocumentReference documentReference = document.getDocumentReference();
            SpaceReference parentSpaceReference = getSpaceReference(documentReference);
            if (parentSpaceReference != null) {
                XWikiContext xcontext = this.getXWikiContext();

                DocumentReference parentPageReference =
                    new DocumentReference(xcontext.getWiki().DEFAULT_SPACE_HOMEPAGE, parentSpaceReference);
                // The parent is a book page, so use its title.
                if (isPage(parentPageReference)) {
                    return xcontext.getWiki().getDocument(parentPageReference, xcontext).getTitle();
                }
            }
        }

        return document != null ? document.getDocumentReference().getName() : null;
    }

    @Override
    public String getTranslationStatus(DocumentReference documentReference, String language)
        throws XWikiException, QueryException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return getTranslationStatus(xcontext.getWiki().getDocument(documentReference, xcontext), language);
    }

    @Override
    public String getTranslationStatus(XWikiDocument document) throws XWikiException, QueryException
    {
        if (document == null) {
            return null;
        }

        DocumentReference collectionRef = getVersionedCollectionReference(document.getDocumentReference());
        String selectedLanguage = getSelectedLanguage(collectionRef);

        BaseObject translationObject = null;
        for (BaseObject tObj : document.getXObjects(BookVersionsConstants.PAGETRANSLATION_CLASS_REFERENCE)) {
            String languageEntry = tObj.getStringValue(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);
            if (languageEntry != null && !languageEntry.isEmpty() && selectedLanguage != null
                && languageEntry.equals(selectedLanguage))
            {
                translationObject = tObj;
                break;
            }
        }

        return translationObject != null
            ? translationObject.getStringValue(BookVersionsConstants.PAGETRANSLATION_STATUS) : null;
    }

    @Override
    public String getTranslationStatus(XWikiDocument document, String language) throws XWikiException, QueryException
    {
        if (document == null) {
            return null;
        }

        for (BaseObject tObj : document.getXObjects(BookVersionsConstants.PAGETRANSLATION_CLASS_REFERENCE)) {
            String languageEntry = tObj.getStringValue(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);
            if (languageEntry != null && !languageEntry.isEmpty() && language != null
                && languageEntry.equals(language))
            {
                return tObj.getStringValue(BookVersionsConstants.PAGETRANSLATION_STATUS);
            }
        }

        return null;
    }

    @Override
    public boolean isDefaultLanguage(DocumentReference documentReference, String language)
        throws XWikiException, QueryException
    {
        XWikiContext xcontext = this.getXWikiContext();

        return isDefaultLanguage(xcontext.getWiki().getDocument(documentReference, xcontext), language);
    }

    @Override
    public boolean isDefaultLanguage(XWikiDocument document, String language) throws XWikiException, QueryException
    {
        for (BaseObject tObj : document.getXObjects(BookVersionsConstants.PAGETRANSLATION_CLASS_REFERENCE)) {
            String languageEntry = tObj.getStringValue(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);
            if (languageEntry != null && !languageEntry.isEmpty() && language != null
                && languageEntry.equals(language))
            {
                int isDefault = tObj.getIntValue(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT);
                if (isDefault == 1) {
                    return true;
                }
                break;
            }
        }

        return false;
    }

    @Override
    public boolean isAParent(DocumentReference spaceReference, DocumentReference nestedReference)
    {
        if (spaceReference != null && nestedReference != null) {
            List<SpaceReference> childSpaces = nestedReference.getSpaceReferences();

            for (SpaceReference parentSpace : spaceReference.getSpaceReferences()) {

                if (childSpaces.contains(parentSpace)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get the name of the page, but escaped for dot character.
     *
     * @param documentReference the document reference to get the name from
     * @return the escaped name of the page
     */
    private String getEscapedName(DocumentReference documentReference)
    {
        return getEscapedName(documentReference.getName());
    }

    /**
     * Get the name of the page, but escaped for dot character.
     *
     * @param name the name to escape
     * @return the escaped name of the page
     */
    private String getEscapedName(String name)
    {
        if (name == null) {
            return null;
        }

        String sequence = "\\.";
        String escapedName = name.replaceAll(sequence, sequence);

        logger.debug("[getEscapedName] escapedName : [{}]", escapedName);

        return escapedName;
    }

    @Override
    public DocumentReference getVersionReference(DocumentReference collectionReference, String version)
        throws XWikiException
    {
        if (collectionReference == null || version == null) {
            return null;
        }
        // Search first for the terminal document : Book.Versions.MyVersion
        SpaceReference versionParentSpaceReference =
            new SpaceReference(new EntityReference(BookVersionsConstants.VERSIONS_LOCATION, EntityType.SPACE,
                collectionReference.getParent()));
        DocumentReference versionDocumentReference = versionParentSpaceReference != null
            ? new DocumentReference(new EntityReference(version, EntityType.DOCUMENT, versionParentSpaceReference))
            : null;

        // Search for the non-terminal document : Book.Versions.MyVersion.WebHome
        if (!this.isVersion(versionDocumentReference)) {
            SpaceReference versionNonTerminalParentSpaceReference =
                new SpaceReference(new EntityReference(version, EntityType.SPACE, versionParentSpaceReference));
            versionDocumentReference =
                new DocumentReference(new EntityReference(this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE,
                    EntityType.DOCUMENT, versionNonTerminalParentSpaceReference));
        }

        return this.isVersion(versionDocumentReference) ? versionDocumentReference : null;
    }

    @Override
    public DocumentReference getVariantReference(DocumentReference collectionReference, String variant)
        throws XWikiException
    {
        if (collectionReference == null || variant == null) {
            return null;
        }

        // Search first for the terminal document : Book.Versions.MyVersion
        SpaceReference variantParentSpaceReference =
            new SpaceReference(new EntityReference(BookVersionsConstants.VARIANTS_LOCATION, EntityType.SPACE,
                collectionReference.getParent()));
        DocumentReference variantDocumentReference =
            new DocumentReference(new EntityReference(variant, EntityType.DOCUMENT, variantParentSpaceReference));

        // Search for the non-terminal document : Book.Versions.MyVersion.WebHome
        if (variantParentSpaceReference != null && !this.isVariant(variantDocumentReference)) {
            SpaceReference versionNonTerminalParentSpaceReference =
                new SpaceReference(new EntityReference(variant, EntityType.SPACE, variantParentSpaceReference));
            variantDocumentReference =
                new DocumentReference(new EntityReference(this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE,
                    EntityType.DOCUMENT, versionNonTerminalParentSpaceReference));
        }

        return isVariant(variantDocumentReference) ? variantDocumentReference : null;
    }

    @Override
    public List<String> getCollectionVersions(DocumentReference collectionReference)
        throws QueryException, XWikiException
    {
        if (collectionReference == null) {
            return Collections.emptyList();
        }

        DocumentReference versionedCollectionReference = getVersionedCollectionReference(collectionReference);

        return queryPages(versionedCollectionReference, BookVersionsConstants.VERSION_CLASS_REFERENCE);
    }

    @Override
    public List<String> getCollectionVariants(DocumentReference collectionReference)
        throws QueryException, XWikiException
    {
        if (collectionReference == null) {
            return Collections.emptyList();
        }

        DocumentReference versionedCollectionReference = getVersionedCollectionReference(collectionReference);

        return queryPages(versionedCollectionReference, BookVersionsConstants.VARIANT_CLASS_REFERENCE);
    }

    /**
     * Query pages under a given document, ordered by descending creation date
     *
     * @param documentReference the root document for the query
     * @return the list of pages under the document, ordered by descending creation date
     * @throws QueryException happens when the query creation or execution has an issue.
     */
    private List<String> queryPages(DocumentReference documentReference)
        throws QueryException
    {
        return queryPages(documentReference, null);
    }

    @Override
    public List<String> queryPages(DocumentReference documentReference, EntityReference classReference)
        throws QueryException
    {
        if (documentReference == null) {
            return Collections.emptyList();
        }

        if (classReference != null) {
            logger.debug("[queryPages] Query pages with class [{}] under [{}]", classReference, documentReference);
        } else {
            logger.debug("[queryPages] Query pages under [{}]", documentReference);
        }

        SpaceReference spaceReference = documentReference.getLastSpaceReference();
        String spaceSerialized = localSerializer.serialize(spaceReference);
        String spacePrefix = spaceSerialized.replaceAll("([%_/])", "/$1").concat(".%");

        logger.debug("[queryPages] spaceSerialized : [{}]", spaceSerialized);
        logger.debug("[queryPages] spacePrefix : [{}]", spacePrefix);

        // Query inspired from getDocumentReferences of DefaultModelBridge.java in xwiki-platform-refactoring
        String statement = "where doc.space like :space escape '/' order by doc.creationDate desc";
        if (classReference != null) {
            statement = ", BaseObject as obj where doc.fullName = obj.name and obj.className = :class "
                + "and doc.space like :space escape '/' order by doc.creationDate desc";
        }
        Query query = this.queryManagerProvider.get()
            .createQuery(statement, Query.HQL)
            .bindValue("space", spacePrefix)
            .setWiki(documentReference.getWikiReference().getName());
        if (classReference != null) {
            query = query.bindValue("class", localSerializer.serialize(classReference));
        }
        List<String> result = query.execute();

        logger.debug("[queryPages] result : [{}]", result);

        return result;
    }

    /**
     * Search for the parent storing the collection type (book or library).
     */
    @Override
    public DocumentReference getVersionedCollectionReference(Document document) throws XWikiException, QueryException
    {
        if (document == null) {
            return null;
        }

        DocumentReference documentReference = document.getDocumentReference();

        if (document.getObject(BookVersionsConstants.BOOK_CLASS_SERIALIZED) != null
            || document.getObject(BookVersionsConstants.LIBRARY_CLASS_SERIALIZED) != null
            || document.getObject(BookVersionsConstants.PUBLISHED_BOOK_CLASS_SERIALIZED) != null)
        {
            return documentReference;
        }

        EntityReference entityReference = documentReference.getParent();

        if (entityReference != null) {

            // Check if the parent is a document.
            EntityReference documentEntityReference = entityReference.extractReference(EntityType.DOCUMENT);
            if (documentEntityReference != null) {
                DocumentReference parentDocumentReference = documentEntityReference instanceof DocumentReference
                    ? (DocumentReference) documentEntityReference : new DocumentReference(documentEntityReference);

                // Verify recursively if the parent is storing the collection definition.
                return getVersionedCollectionReference(parentDocumentReference);
            } else {
                // Check if the parent is a space.
                SpaceReference parentSpaceReference = getSpaceReference(entityReference);

                // Check if the parent itself is a collection
                DocumentReference parentDocumentReference = null;
                if (parentSpaceReference != null) {
                    parentDocumentReference = new DocumentReference(
                        this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE, parentSpaceReference);
                    if (isBook(parentDocumentReference) || isLibrary(parentDocumentReference)) {
                        return parentDocumentReference;
                    }
                }

                // If the parent is a space, but it's the last space of the given reference,
                // then go upper with one level, to the parent of the parent to avoid Stack Overflow.
                if (parentSpaceReference != null
                    && parentSpaceReference.equals(documentReference.getLastSpaceReference()))
                {
                    parentSpaceReference = getSpaceReference(parentSpaceReference.getParent());
                }

                // Get the document reference of the root for the parent space.
                parentDocumentReference = parentSpaceReference != null ? new DocumentReference(
                    this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE, parentSpaceReference) : null;

                // Verify recursively if this document is storing the collection definition.
                return getVersionedCollectionReference(parentDocumentReference);
            }
        }

        return null;
    }

    /**
     * Search for the parent storing the collection type (book or library).
     */
    @Override
    public DocumentReference getVersionedCollectionReference(DocumentReference documentReference)
        throws XWikiException, QueryException
    {
        if (documentReference == null) {
            return null;
        }

        XWikiContext xcontext = getXWikiContext();

        if (isBook(documentReference) || isLibrary(documentReference)
            || xcontext.getWiki().getDocument(documentReference, xcontext)
            .getXObject(BookVersionsConstants.PUBLISHEDCOLLECTION_CLASS_REFERENCE) != null)
        {
            return documentReference;
        }

        EntityReference entityReference = documentReference.getParent();

        if (entityReference != null) {

            // Check if the parent is a document.
            EntityReference documentEntityReference = entityReference.extractReference(EntityType.DOCUMENT);
            if (documentEntityReference != null) {
                DocumentReference parentDocumentReference = documentEntityReference instanceof DocumentReference
                    ? (DocumentReference) documentEntityReference : new DocumentReference(documentEntityReference);

                // Verify recursively if the parent is storing the collection definition.
                return getVersionedCollectionReference(parentDocumentReference);
            } else {
                // Check if the parent is a space.
                SpaceReference parentSpaceReference = getSpaceReference(entityReference);

                // Check if the parent itself is a collection
                DocumentReference parentDocumentReference = null;
                if (parentSpaceReference != null) {
                    parentDocumentReference = new DocumentReference(
                        this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE, parentSpaceReference);
                    if (isBook(parentDocumentReference) || isLibrary(parentDocumentReference)) {
                        return parentDocumentReference;
                    }
                }

                // If the parent is a space, but it's the last space of the given reference,
                // then go upper with one level, to the parent of the parent to avoid Stack Overflow.
                if (parentSpaceReference != null
                    && parentSpaceReference.equals(documentReference.getLastSpaceReference()))
                {
                    parentSpaceReference = getSpaceReference(parentSpaceReference.getParent());
                }

                // Get the document reference of the root for the parent space.
                parentDocumentReference = parentSpaceReference != null ? new DocumentReference(
                    this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE, parentSpaceReference) : null;

                // Verify recursively if this document is storing the collection definition.
                return getVersionedCollectionReference(parentDocumentReference);
            }
        }

        return null;
    }

    @Override
    public boolean hasContentForVersion(DocumentReference documentReference, String version)
        throws QueryException, XWikiException
    {
        if (version == null || version.isBlank() || documentReference == null) {
            return false;
        }

        XWikiContext xcontext = this.getXWikiContext();
        DocumentReference pageReference = documentReference;

        // If the reference is not a page, go to its parent, assuming that the current ref is a versioned content page.
        if (!isPage(documentReference)) {

            SpaceReference parentSpaceReference = getSpaceReference(documentReference);
            if (parentSpaceReference != null
                && parentSpaceReference.equals(documentReference.getLastSpaceReference()))
            {
                parentSpaceReference = getSpaceReference(parentSpaceReference.getParent());
            }
            if (parentSpaceReference != null) {
                pageReference = new DocumentReference(this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE,
                    parentSpaceReference);
            }

            // The parent is not a page either.
            if (!isPage(pageReference)) {
                return false;
            }
        }

        DocumentReference versionedContentReference =
            pageReference != null ? this.getVersionedContentReference(pageReference, version) : null;
        return versionedContentReference != null && xcontext.getWiki().exists(versionedContentReference, xcontext);
    }

    @Override
    public String getVersionName(DocumentReference versionReference)
    {
        String versionName = getEscapedName(versionReference);
        if (versionName != null && versionName.equals(this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE)) {
            versionName = getEscapedName(versionReference.getLastSpaceReference().getName());
        }

        return versionName;
    }

    @Override
    public String getVariantName(DocumentReference variantReference)
    {
        String variantName = getEscapedName(variantReference);
        if (variantName != null && variantName.equals(this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE)) {
            variantName = getEscapedName(variantReference.getLastSpaceReference().getName());
        }

        return variantName;
    }

    @Override
    public DocumentReference getVersionedContentReference(DocumentReference documentReference)
        throws XWikiException, QueryException
    {
        return isVersionedPage(documentReference)
            ? getVersionedContentReference(documentReference, getSelectedVersion(documentReference)) : null;
    }

    @Override
    public DocumentReference getVersionedContentReference(XWikiDocument document) throws XWikiException, QueryException
    {
        if (isVersionedPage(document)) {
            DocumentReference versionedCollectionReference =
                getVersionedCollectionReference(document.getDocumentReference());

            return versionedCollectionReference != null ? getVersionedContentReference(document.getDocumentReference(),
                getSelectedVersion(versionedCollectionReference)) : null;
        }

        return null;
    }

    @Override
    public DocumentReference getVersionedContentReference(DocumentReference documentReference, String version)
        throws QueryException, XWikiException
    {
        if (version != null && !version.isBlank() && documentReference != null) {
            DocumentReference versionDocumentReference =
                referenceResolver.resolve(version).setWikiReference(this.getXWikiContext().getWikiReference());
            return getVersionedContentReference(documentReference, versionDocumentReference);
        }

        List<String> collectionVersions = getCollectionVersions(documentReference);
        if (collectionVersions != null && collectionVersions.size() > 0) {
            return referenceResolver.resolve(collectionVersions.get(0))
                .setWikiReference(this.getXWikiContext().getWikiReference());
        }

        return null;
    }

    @Override
    public DocumentReference getVersionedContentReference(DocumentReference pageReference,
        DocumentReference versionReference)
    {
        String versionName = getVersionName(versionReference);

        logger.debug("[getVersionedContentReference] versionName : [{}]", versionName);

        return versionName != null
            ? new DocumentReference(new EntityReference(versionName, EntityType.DOCUMENT, pageReference.getParent()))
            : null;
    }

    private List<DocumentReference> getVersionsAscending(DocumentReference collectionReference,
        DocumentReference versionReference)
        throws XWikiException, QueryException
    {
        List<DocumentReference> result = new ArrayList<>();
        if (versionReference == null || !isVersion(versionReference)) {
            return result;
        }

        int versionQuantity = getCollectionVersions(collectionReference).size();

        String versionName = getVersionName(versionReference);
        DocumentReference previousVersionReference = versionReference;
        int i = 0;
        result.add(versionReference);

        logger.debug("[getVersionsAscending] get versions before : [{}]", versionName);
        while (i < getCollectionVersions(collectionReference).size() + 1) {
            previousVersionReference = getPreviousVersion(previousVersionReference);
            if (previousVersionReference == null) {
                logger.debug("[getVersionsAscending] no more previous versions.");
                break;
            } else {
                versionName = getVersionName(previousVersionReference);
                logger.debug("[getVersionsAscending] previous version is [{}]", versionName);
                result.add(previousVersionReference);
            }
            i++;
        }

        if (i > versionQuantity) {
            logger.error("[getVersionsAscending] Infinite loop detected in versions preceding [{}].", versionName);
            return Collections.emptyList();
        }
        return result;
    }

    @Override
    public DocumentReference getInheritedVersionedContentReference(DocumentReference documentReference)
        throws XWikiException, QueryException
    {
        DocumentReference versionDocumentReference = getVersionedContentReference(documentReference);

        return versionDocumentReference != null
            ? getInheritedContentVersionReference(documentReference, versionDocumentReference) : documentReference;
    }

    @Override
    public DocumentReference getInheritedContentVersionReference(DocumentReference pageReference,
        DocumentReference versionReference) throws QueryException, XWikiException
    {
        if (pageReference == null || versionReference == null) {
            return null;
        }

        // TO DO: check if the page is unversioned, or not
        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();

        String versionName = getVersionName(versionReference);

        logger.debug("[getInheritedContentVersionReference] versionName : [{}]", versionName);

        while (versionName != null && !versionName.isEmpty()) {
            DocumentReference versionedContentRef =
                new DocumentReference(new EntityReference(versionName, EntityType.DOCUMENT, pageReference.getParent()));

            logger.debug("[getInheritedContentVersionReference] versionedContentRef : [{}]", versionedContentRef);

            if (xwiki.exists(versionedContentRef, xcontext)) {
                // Content exists for this version of the page
                return versionedContentRef;
            } else {
                // Content does not exists for this version. Lets check if there is content in a version to be
                // inherited
                return getPrecedingContentVersionReference(pageReference, versionReference);
            }
        }

        return null;
    }

    private DocumentReference getPrecedingContentVersionReference(DocumentReference pageReference,
        DocumentReference versionReference) throws QueryException, XWikiException
    {
        if (versionReference == null) {
            // This is the first version in the tree, there's nothing to inherit from
            return null;
        }

        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        DocumentReference versionedContentReference = new DocumentReference(
            new EntityReference(getVersionName(versionReference), EntityType.DOCUMENT, pageReference.getParent()));

        if (xwiki.exists(versionedContentReference, xcontext)) {
            // Found the content corresponding to the given version
            return versionedContentReference;
        } else {
            // No versioned content found, so search in the preceding version
            return xwiki.exists(versionedContentReference, xcontext) ? versionedContentReference
                : getPrecedingContentVersionReference(pageReference, getPreviousVersion(versionReference));
        }
    }

    @Override
    public DocumentReference getPreviousVersion(DocumentReference versionReference) throws XWikiException
    {
        XWikiContext xcontext = this.getXWikiContext();

        if (versionReference == null) {
            return null;
        }

        XWikiDocument versionDocument = xcontext.getWiki().getDocument(versionReference, xcontext);
        BaseObject versionObject = versionDocument.getXObject(BookVersionsConstants.VERSION_CLASS_REFERENCE);
        if (versionObject == null) {
            logger.warn("Version [{}] is missing the object [{}].", versionDocument.getDocumentReference().toString(),
                BookVersionsConstants.VERSION_CLASS_REFERENCE.toString());
            return null;
        }

        String previousVersion = versionObject.getStringValue(BookVersionsConstants.VERSION_PROP_PRECEDINGVERSION);
        return previousVersion != null && !previousVersion.isBlank()
            ? referenceResolver.resolve(previousVersion, versionReference)
            : null;
    }

    @Override
    public DocumentReference getInheritedContentReference(DocumentReference pageReference,
        DocumentReference versionReference) throws QueryException, XWikiException
    {
        if (pageReference == null || versionReference == null) {
            return null;
        }

        XWikiContext xcontext = this.getXWikiContext();
        DocumentReference versionPageReference = getInheritedContentVersionReference(pageReference, versionReference);

        logger.debug("[getInheritedContentReference] versionPageReference : [{}]", versionPageReference);

        if (versionPageReference != null) {
            String versionName = getVersionName(versionPageReference);

            logger.debug("[getInheritedContentReference] versionName : [{}]", versionName);

            if (versionName != null) {
                DocumentReference versionedContentRef = new DocumentReference(
                    new EntityReference(versionName, EntityType.DOCUMENT, pageReference.getParent()));

                logger.debug("[getInheritedContentReference] versionedContentRef : [{}]", versionedContentRef);

                if (xcontext.getWiki().exists(versionedContentRef, xcontext)) {
                    // Content exists for this version of the page
                    return versionedContentRef;
                }
            }
        }

        return null;
    }

    private SpaceReference getSpaceReference(EntityReference entityReference)
    {
        EntityReference spaceEntityReference = entityReference.extractReference(EntityType.SPACE);

        return entityReference != null && spaceEntityReference != null ? spaceEntityReference instanceof SpaceReference
            ? (SpaceReference) spaceEntityReference : new SpaceReference(spaceEntityReference) : null;
    }

    @Override
    public void setLibrary(DocumentReference bookReference, DocumentReference libraryReference)
        throws QueryException, XWikiException
    {
        setLibrary(bookReference, libraryReference,
            referenceResolver.resolve(getCollectionVersions(libraryReference).get(0), libraryReference));
    }

    @Override
    public void setLibrary(DocumentReference bookReference, DocumentReference libraryReference,
        DocumentReference libraryVersionReference) throws QueryException, XWikiException
    {
        if (isBook(bookReference) && isLibrary(libraryReference)
            && isFromLibrary(libraryReference, libraryVersionReference))
        {
            List<String> versionsLocalRef = getCollectionVersions(bookReference);
            for (String versionLocalRef : versionsLocalRef) {
                DocumentReference versionRef = referenceResolver.resolve(versionLocalRef);
                setVersionLibrary(versionRef, libraryReference, libraryVersionReference);
            }
        }
    }

    private void setVersionLibrary(DocumentReference versionReference, DocumentReference libraryReference,
        DocumentReference libraryVersionReference) throws XWikiException
    {
        if (versionReference == null || libraryReference == null || libraryVersionReference == null) {
            return;
        }

        XWikiContext xcontext = getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        XWikiDocument versionDoc = xwiki.getDocument(versionReference, xcontext).clone();
        if (versionDoc == null) {
            return;
        }

        List<BaseObject> libRefObjects =
            versionDoc.getXObjects(BookVersionsConstants.BOOKLIBRARYREFERENCE_CLASS_REFERENCE);
        boolean createObject = true;
        for (BaseObject libRefObject : libRefObjects) {
            String libraryPropValue =
                libRefObject.getStringValue(BookVersionsConstants.BOOKLIBRARYREFERENCE_PROP_LIBRARY);

            if (libraryPropValue != null
                && libraryReference.equals(referenceResolver.resolve(libraryPropValue, libraryReference)))
            {

                if (libraryVersionReference.equals(referenceResolver.resolve(
                    libRefObject.getStringValue(BookVersionsConstants.BOOKLIBRARYREFERENCE_PROP_LIBRARYVERSION),
                    libraryVersionReference)))
                {
                    // The version library is already set with the proper value, nothing to do
                    return;
                } else {
                    // The library configuration object already exists but without the proper value
                    libRefObject.set(BookVersionsConstants.BOOKLIBRARYREFERENCE_PROP_LIBRARYVERSION,
                        libraryVersionReference, xcontext);
                    createObject = false;
                }
            }
        }

        if (createObject) {
            // No object already existing for the library configuration, add one
            BaseObject newObject =
                versionDoc.newXObject(BookVersionsConstants.BOOKLIBRARYREFERENCE_CLASS_REFERENCE, xcontext);
            newObject.set(BookVersionsConstants.BOOKLIBRARYREFERENCE_PROP_LIBRARY, libraryReference, xcontext);
            newObject.set(BookVersionsConstants.BOOKLIBRARYREFERENCE_PROP_LIBRARYVERSION, libraryVersionReference,
                xcontext);
        }
        UserReference userReference = userReferenceResolver.resolve(xcontext.getUserReference());
        versionDoc.getAuthors().setEffectiveMetadataAuthor(userReference);
        versionDoc.getAuthors().setOriginalMetadataAuthor(userReference);
        xwiki.saveDocument(versionDoc, "Setting version configuration for library ["
            + libraryReference.getParent().toString() + "]: [" + libraryVersionReference.toString() + "].", xcontext);
    }

    @Override
    public DocumentReference getConfiguredLibraryVersion(DocumentReference documentReference,
        DocumentReference libraryReference) throws XWikiException, QueryException
    {
        if (!isLibrary(libraryReference)) {
            return null;
        }
        DocumentReference collectionRef = getVersionedCollectionReference(documentReference);
        String selectedVersionStringRef = getSelectedVersion(documentReference);
        if (isVersionedContent(documentReference)) {
            SpaceReference versionParentSpaceReference =
                new SpaceReference(new EntityReference(BookVersionsConstants.VERSIONS_LOCATION, EntityType.SPACE,
                    collectionRef.getParent()));
            DocumentReference versionDocumentReference = new DocumentReference(
                new EntityReference(documentReference.getName(), EntityType.DOCUMENT, versionParentSpaceReference));
            selectedVersionStringRef = localSerializer.serialize(versionDocumentReference);
        }
        if (selectedVersionStringRef == null || selectedVersionStringRef.isEmpty()) {
            // in case no version has been selected yet, get the most recent one
            List<String> versions = getCollectionVersions(collectionRef);
            if (versions.size() == 0) {
                return null;
            }
            selectedVersionStringRef = getCollectionVersions(collectionRef).get(0);
        }
        DocumentReference selectedVersionRef = referenceResolver.resolve(selectedVersionStringRef, collectionRef);
        return getConfiguredLibraryVersion(collectionRef, libraryReference, selectedVersionRef);
    }

    /**
     * Get the library version configured for the given book version which uses the given library.
     *
     * @param bookReference the book
     * @param libraryReference the library used
     * @param versionReference the version of the book
     * @return the reference of the library version configured for the book version
     * @throws XWikiException
     */
    private DocumentReference getConfiguredLibraryVersion(DocumentReference bookReference,
        DocumentReference libraryReference, DocumentReference versionReference) throws XWikiException
    {
        if (!isBook(bookReference) || !isLibrary(libraryReference) || !isVersion(versionReference)) {
            return null;
        }

        XWikiContext xcontext = getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        XWikiDocument selectedVersionDoc = xwiki.getDocument(versionReference, xcontext);
        List<BaseObject> libRefObjects =
            selectedVersionDoc.getXObjects(BookVersionsConstants.BOOKLIBRARYREFERENCE_CLASS_REFERENCE);
        for (BaseObject libRefObject : libRefObjects) {
            if (libraryReference.equals(referenceResolver.resolve(
                libRefObject.getStringValue(BookVersionsConstants.BOOKLIBRARYREFERENCE_PROP_LIBRARY),
                libraryReference)))
            {
                return referenceResolver.resolve(
                    libRefObject.getStringValue(BookVersionsConstants.BOOKLIBRARYREFERENCE_PROP_LIBRARYVERSION),
                    libraryReference);
            }
        }

        return null;
    }

    @Override
    public DocumentReference getLinkedLibraryContentReference(DocumentReference documentReference,
        DocumentReference keyReference) throws XWikiException, QueryException
    {
        if (documentReference == null || keyReference == null) {
            return null;
        }

        DocumentReference libraryRef = getVersionedCollectionReference(keyReference);
        if (isLibrary(libraryRef) && isPage(keyReference)) {
            // the passed reference is part of a library
            if (isVersionedPage(keyReference)) {
                // versioned page => get the content depending on the book configuration
                DocumentReference libraryVersionRef = getConfiguredLibraryVersion(documentReference, libraryRef);
                return getInheritedContentReference(keyReference, libraryVersionRef);
            } else {
                // unversioned page
                return keyReference;
            }
        }

        return null;
    }

    @Override
    public List<DocumentReference> getUsedLibraries(DocumentReference bookReference)
        throws XWikiException, QueryException
    {
        List<DocumentReference> result = new ArrayList<DocumentReference>();
        if (bookReference != null && isBook(bookReference)) {
            logger.debug("[getUsedLibraries] Get libraries used in [{}].", bookReference);

            SpaceReference spaceReference = bookReference.getLastSpaceReference();
            String spaceSerialized = localSerializer.serialize(spaceReference);
            String spacePrefix = spaceSerialized.replaceAll("([%_/])", "/$1").concat(".%");

            // Query inspired from getDocumentReferences of DefaultModelBridge.java in xwiki-platform
            List<String> resultStrings = this.queryManagerProvider.get()
                .createQuery("select distinct obj.libraryReference from Document doc, "
                    + "doc.object(BookVersions.Code.LibraryReferenceClass) as obj where doc.space "
                    + "like :space escape '/' order by obj.libraryReference asc", Query.XWQL)
                .bindValue("space", spacePrefix).setWiki(bookReference.getWikiReference().getName()).execute();

            for (String resultString : resultStrings) {
                if (StringUtils.isNotEmpty(resultString)) {
                    result.add(currentMixedReferenceResolver.resolve(resultString, bookReference));
                }
            }

            logger.debug("[getUsedLibraries] Libraries used in book: [{}]", result);
        }

        return result;
    }

    @Override
    public Map<DocumentReference, DocumentReference> getUsedPublishedLibraries(DocumentReference bookReference,
        DocumentReference versionReference) throws XWikiException, QueryException
    {
        Map<DocumentReference, DocumentReference> result = new HashMap<>();
        if (bookReference != null && isBook(bookReference)) {
            List<DocumentReference> usedLibraries = getUsedLibraries(bookReference);
            for (DocumentReference libraryReference : usedLibraries) {
                DocumentReference libraryVersionReference =
                    getConfiguredLibraryVersion(bookReference, libraryReference, versionReference);
                if (libraryVersionReference == null) {
                    logger.warn("Library [{}] is used in book [{}] but no library's version has been configured "
                        + "for book's version [{}].", libraryReference, bookReference, versionReference);
                    result.put(libraryReference, null);
                    continue;
                }
                DocumentReference publishedSpace =
                    getCollectionPublishedSpace(libraryReference, libraryVersionReference.getName(), libraryReference);
                if (publishedSpace == null) {
                    logger.warn(
                        "Library [{}], configured in book [{}] to use version [{}]" + " doesn't seem to be published.",
                        libraryReference, bookReference, libraryVersionReference);
                    result.put(libraryReference, null);
                    continue;
                }
                result.put(libraryReference, publishedSpace);
            }
        }
        return result;
    }

    /**
     * Get the published space for each of the libraries used in the given book, for the current version and the
     * preceding ones.
     *
     * @param bookReference the reference of the book
     * @param versionReference the version of the book, which corresponds to a library's version in the book
     *     configuration
     * @return the published space reference of each library used in the book as a map of {bookVersionName, {
     *     libraryReference, publishedSpaceReference}}
     * @throws XWikiException In case a getDocument method or a check of type (isBook, ...) has an issue
     * @throws QueryException If any exception occurs while querying the database for the used libraries in the
     *     book.
     */
    private Map<String, Map<DocumentReference, DocumentReference>> getUsedPublishedLibrariesWithInheritance(
        DocumentReference bookReference, DocumentReference versionReference) throws XWikiException, QueryException
    {
        Map<String, Map<DocumentReference, DocumentReference>> result = new HashMap<>();
        if (bookReference != null && isBook(bookReference)) {
            List<DocumentReference> versions = getVersionsAscending(bookReference, versionReference);
            List<DocumentReference> usedLibraries = getUsedLibraries(bookReference);
            for (DocumentReference ascendingVersionReference : versions) {
                Map<DocumentReference, DocumentReference> libResult = new HashMap<>();
                String versionName = getVersionName(ascendingVersionReference);
                for (DocumentReference libraryReference : usedLibraries) {
                    if (libraryReference == null) {
                        continue;
                    }
                    DocumentReference libraryVersionReference =
                        getConfiguredLibraryVersion(bookReference, libraryReference, ascendingVersionReference);
                    if (libraryVersionReference == null) {
                        logger.warn("Library [{}] is used in book [{}] but no library's version has been configured "
                            + "for book's version [{}].", libraryReference, bookReference, ascendingVersionReference);
                        libResult.put(libraryReference, null);
                        continue;
                    }
                    DocumentReference publishedSpace =
                        getCollectionPublishedSpace(libraryReference, libraryVersionReference.getName(),
                            libraryReference);
                    if (publishedSpace == null) {
                        logger.warn("Library [{}], configured in book [{}] to use version [{}] doesn't seem to be "
                                + "published.", libraryReference, bookReference, libraryVersionReference);
                        libResult.put(libraryReference, null);
                        continue;
                    }
                    libResult.put(libraryReference, publishedSpace);
                }
                result.put(versionName, libResult);
            }
        }
        return result;
    }

    @Override
    public void switchDeletedMark(DocumentReference documentReference) throws XWikiException
    {
        if (documentReference == null) {
            return;
        }

        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        XWikiDocument document = xwiki.getDocument(documentReference, xcontext).clone();

        if (isMarkedDeleted(document)) {
            BaseObject object = document.getXObject(BookVersionsConstants.MARKEDDELETED_CLASS_REFERENCE);
            if (object != null) {
                document.removeXObject(object);
                UserReference userReference = userReferenceResolver.resolve(xcontext.getUserReference());
                document.getAuthors().setEffectiveMetadataAuthor(userReference);
                document.getAuthors().setOriginalMetadataAuthor(userReference);
                xwiki.saveDocument(document, "Unmarked document as \"Deleted\"", xcontext);
            }
        } else {
            document.newXObject(BookVersionsConstants.MARKEDDELETED_CLASS_REFERENCE, xcontext);
            UserReference userReference = userReferenceResolver.resolve(xcontext.getUserReference());
            document.getAuthors().setEffectiveMetadataAuthor(userReference);
            document.getAuthors().setOriginalMetadataAuthor(userReference);
            xwiki.saveDocument(document, "Marked document as \"Deleted\"", xcontext);
        }
    }

    @Override
    public String publish(DocumentReference configurationReference) throws JobException
    {
        if (configurationReference == null) {
            return null;
        }

        DefaultRequest jobRequest = new DefaultRequest();
        String jobId = BookVersionsConstants.PUBLICATION_JOBID_PREFIX
            + BookVersionsConstants.PUBLICATION_JOBID_SEPARATOR + configurationReference.getName()
            + BookVersionsConstants.PUBLICATION_JOBID_SEPARATOR + Instant.now().toString();
        if (jobId != null) {
            jobRequest.setId(jobId);
            jobRequest.setProperty("configurationReference", configurationReference);
            // The context won't be full in publishInternal as it is executed by a job, so the user executing the
            // publication and locale have to be passed as parameters.
            XWikiContext xcontext = this.getXWikiContext();
            jobRequest.setProperty("userReference", xcontext.getUserReference());
            jobRequest.setProperty("userLocale", xcontext.getLocale());
            jobExecutor.execute(BookVersionsConstants.PUBLICATIONJOB_TYPE, jobRequest);
        }
        return jobId;
    }

    @Override
    public Map<String, Object> loadPublicationConfiguration(DocumentReference configurationReference)
        throws XWikiException
    {
        Map<String, Object> configuration = new HashMap<String, Object>();
        logger.debug("[loadPublicationConfiguration] Loading publication configuration from [{}]",
            configurationReference);
        if (configurationReference == null) {
            logger.error("Configuration reference is null");
            return configuration;
        }

        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        XWikiDocument configurationDoc = xwiki.getDocument(configurationReference, xcontext);
        BaseObject configurationObject =
            configurationDoc.getXObject(BookVersionsConstants.PUBLICATIONCONFIGURATION_CLASS_REFERENCE);

        if (configurationObject == null) {
            logger.error("[loadPublicationConfiguration] Configuration page has no [{}]",
                BookVersionsConstants.PUBLICATIONCONFIGURATION_CLASS_REFERENCE);
            return configuration;
        }

        String sourceReferenceString =
            configurationObject.getStringValue(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE);
        String destinationReferenceString =
            configurationObject.getStringValue(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_DESTINATIONSPACE);
        String versionReferenceString =
            configurationObject.getStringValue(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VERSION);
        String behaviour =
            configurationObject.getStringValue(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR);
        if (sourceReferenceString == null || sourceReferenceString.isBlank() || destinationReferenceString == null
            || behaviour == null || destinationReferenceString.isBlank() || versionReferenceString.isBlank())
        {
            logger.error("One of the mandatory element in the configuration (source, destination, version or behaviour) "
                + "is missing.");
            return configuration;
        }

        try {
            configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE,
                currentExplicitReferenceResolver.resolve(sourceReferenceString,
                    configurationReference.getWikiReference()));
        } catch (Exception e) {
            sourceReferenceString = sourceReferenceString + ".WebHome";
            configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE,
                currentExplicitReferenceResolver.resolve(sourceReferenceString,
                    configurationReference.getWikiReference()));
        }
        configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_DESTINATIONSPACE,
            spaceReferenceResolver.resolve(destinationReferenceString, configurationReference.getWikiReference()));
        configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VERSION,
            referenceResolver.resolve(versionReferenceString, configurationReference.getWikiReference()));
        configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR, behaviour);

        String variantReferenceString =
            configurationObject.getStringValue(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VARIANT);
        configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VARIANT,
            (variantReferenceString == null || variantReferenceString.isBlank()) ? null
                : referenceResolver.resolve(variantReferenceString, configurationReference.getWikiReference()));

        configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_LANGUAGE,
            configurationObject.getStringValue(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_LANGUAGE));
        configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHONLYCOMPLETE, configurationObject
            .getIntValue(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHONLYCOMPLETE) != 0);
        configuration.put(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHPAGEORDER,
            configurationObject.getIntValue(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHPAGEORDER) != 0);

        logger.debug("[loadPublicationConfiguration] Configuration loaded: [{}].", configuration);

        return configuration;
    }

    @Override
    public List<Map<String, Object>> previewPublication(
        DocumentReference configurationReference,
        DocumentReference userDocumentReference)
        throws XWikiException, QueryException, ComponentLookupException, ParseException
    {
        List<Map<String, Object>> previewLines = new ArrayList<>();
    
        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
    
        // 1. If configurationReference is null, nothing to do.
        if (configurationReference == null) {
            return previewLines;
        }
    
        previewLines.add(createLine(
            "StartPublicationJob",
            "configurationReference", configurationReference
        ));
    
        // Load publication config
        Map<String, Object> configuration = loadPublicationConfiguration(configurationReference);
        if (configuration == null || configuration.isEmpty()) {
            return previewLines;
        }
    
        DocumentReference sourceReference = (DocumentReference)
            configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE);
        if (sourceReference == null) {
            return previewLines;
        }
        SpaceReference targetReference = (SpaceReference)
            configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_DESTINATIONSPACE);
        if (targetReference == null) {
            return previewLines;
        }
    
        String publicationBehaviour = (String)
            configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR);
        if (publicationBehaviour == null) {
            return previewLines;
        }
        
        DocumentReference targetDocumentReference = new DocumentReference(
            new EntityReference("WebHome", EntityType.DOCUMENT, targetReference)
        );
        List<String> subTargetDocumentsString = queryPages(targetDocumentReference);
    
        // 2. Behavior checks
        if (publicationBehaviour.equals(
            BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR_CANCEL)
            && !isEmptyTargetSpace(subTargetDocumentsString, targetDocumentReference))
        {
            previewLines.add(createLine(
                "CancelPublicationSpaceNotEmpty",
                "destinationSpace", targetReference
            ));
            return previewLines;
        }
        else if (publicationBehaviour.equals(
            BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR_REPUBLISH))
        {
            previewLines.add(createLine(
                "RepublishSpaceNotEmpty",
                "destinationSpace", targetReference
            ));
        }
    
        // 3. Identify "collection" and "version"
        DocumentReference collectionReference = getVersionedCollectionReference(sourceReference);
        XWikiDocument collection =
            collectionReference != null ? xwiki.getDocument(collectionReference, xcontext) : null;
        DocumentReference versionReference = (DocumentReference)
            configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VERSION);

        // 4. Check for any libraries that might not be published
        Map<String, Map<DocumentReference, DocumentReference>> publishedLibraries = null;
        if (collection != null && versionReference != null && isBook(collectionReference)) {
            // Here we do a "preview" version of getUsedPublishedLibrariesWithInheritance
            publishedLibraries = previewUsedPublishedLibrariesWithInheritance(
                collection.getDocumentReference(), versionReference, previewLines);
        }
    
        // 5. Now we do the "Start publication" lines etc. (the pages)
        List<String> pageReferenceTree = getPageReferenceTree(sourceReference);
        if (pageReferenceTree == null || pageReferenceTree.isEmpty()) {
            return previewLines;
        }
    
        int i = 1;
        int pageQuantity = pageReferenceTree.size();
    
        for (String pageStringReference : pageReferenceTree) {
            if (pageStringReference == null) {
                continue;
            }
    
            previewLines.add(createLine(
                "StartPagePublication",
                "pageIndex", i,
                "totalPages", pageQuantity,
                "pageStringReference", pageStringReference
            ));
    
            DocumentReference pageReference = referenceResolver.resolve(pageStringReference, configurationReference);
            if (!isPage(pageReference)) {
                continue;
            }
    
            XWikiDocument page = xwiki.getDocument(pageReference, xcontext);
            DocumentReference contentPageReference = getContentPage(page, configuration);
            if (contentPageReference == null) {
                continue;
            }
    
            // Figure out the "published" target
            DocumentReference publishedReference =
                getPublishedReference(pageReference, collectionReference != null
                    ? collectionReference : sourceReference, targetReference);
            if (publishedReference == null) {
                continue;
            }
    
            previewLines.add(createLine(
                "CopyPage",
                "copyFrom", contentPageReference,
                "copyTo", publishedReference
            ));
    
            previewLines.add(createLine(
                "EndPagePublication",
                "pageStringReference", pageStringReference
            ));
    
            i++;
        }
    
        return previewLines;
    }
    
    /**
     * This "preview" version replicates the logic in getUsedPublishedLibrariesWithInheritance, 
     * but instead of actually returning the final result for usage, it also logs warnings as 
     * "preview lines" if the library or its version is missing.
     */
    private Map<String, Map<DocumentReference, DocumentReference>> previewUsedPublishedLibrariesWithInheritance(
        DocumentReference bookReference,
        DocumentReference versionReference,
        List<Map<String, Object>> previewLines)
        throws XWikiException, QueryException
    {
        Map<String, Map<DocumentReference, DocumentReference>> result = new HashMap<>();
    
        if (bookReference != null && isBook(bookReference)) {
            // getVersionsAscending/book's version references
            List<DocumentReference> versions = getVersionsAscending(bookReference, versionReference);
            // getUsedLibraries() - the libraries used by the book
            List<DocumentReference> usedLibraries = getUsedLibraries(bookReference);
    
            for (DocumentReference ascendingVersionReference : versions) {
                Map<DocumentReference, DocumentReference> libResult = new HashMap<>();
                String versionName = getVersionName(ascendingVersionReference);
    
                for (DocumentReference libraryReference : usedLibraries) {
                    if (libraryReference == null) {
                        continue;
                    }
                    // The library version used by this book version
                    DocumentReference libraryVersionReference =
                        getConfiguredLibraryVersion(bookReference, libraryReference, ascendingVersionReference);
    
                    if (libraryVersionReference == null) {
                        // logger.warn("Library [{}] is used in book [{}] but no library's version configured...")
                        previewLines.add(createLine(
                            "LibraryNoVersionConfigured",
                            "libraryReference", libraryReference,
                            "bookReference", bookReference,
                            "bookVersion", ascendingVersionReference
                        ));
                        libResult.put(libraryReference, null);
                        continue;
                    }
    
                    // The published space for that library version
                    DocumentReference publishedSpace = getCollectionPublishedSpace(
                        libraryReference,
                        libraryVersionReference.getName(),
                        libraryReference
                    );
                    if (publishedSpace == null) {
                        // logger.warn("Library [{}], configured in book [{}] to use version [{}] doesn't seem published.")
                        previewLines.add(createLine(
                            "LibraryNotPublished",
                            "libraryReference", libraryReference,
                            "bookReference", bookReference,
                            "libraryVersion", libraryVersionReference
                        ));
                        libResult.put(libraryReference, null);
                    } else {
                        // If you want to also record success, you can do so:
                        // (This is optional; remove if you only want "missing" warnings.)
                        previewLines.add(createLine(
                            "LibraryPublishedOK",
                            "libraryReference", libraryReference,
                            "bookReference", bookReference,
                            "libraryVersion", libraryVersionReference,
                            "publishedSpace", publishedSpace
                        ));
                        libResult.put(libraryReference, publishedSpace);
                    }
                }
                result.put(versionName, libResult);
            }
        }
        return result;
    }
    
    /**
     * Small helper to create a structured map describing a single log event.
     */
    private Map<String, Object> createLine(String action, Object... kvPairs)
    {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("action", action);
    
        for (int i = 0; i < kvPairs.length; i += 2) {
            String key = String.valueOf(kvPairs[i]);
            Object value = i + 1 < kvPairs.length ? kvPairs[i + 1] : null;
            line.put(key, value);
        }
        return line;
    }
    

    @Override
    public void publishInternal(DocumentReference configurationReference, DocumentReference userDocumentReference,
        Locale userLocale)
        throws XWikiException, QueryException, ComponentLookupException, ParseException
    {
        if (configurationReference == null || userDocumentReference == null) {
            logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal."
                + "nullParameter", userLocale));
            return;
        }
        if (userLocale == null) {
            userLocale = new Locale(BookVersionsConstants.DEFAULT_LOCALE);
        }

        logger.debug("[publishInternal] Publication required with configuration [{}]", configurationReference);
        logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal.start",
            userLocale, configurationReference));
        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();

        // Load Publication
        logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                + ".loadConfiguration", userLocale));
        Map<String, Object> configuration = loadPublicationConfiguration(configurationReference);
        if (configuration == null || configuration.isEmpty()) {
            logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal."
                    + "noConfig", userLocale));
            return;
        }
        DocumentReference sourceReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE);
        if (sourceReference == null) {
            logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal."
                + "noSource", userLocale, configurationReference));
            return;
        }
        SpaceReference targetReference =
            (SpaceReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_DESTINATIONSPACE);
        if (targetReference == null) {
            logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal."
                + "noTarget", userLocale, configurationReference));
            return;
        }

        String publicationBehaviour =
            (String) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR);
        if (publicationBehaviour == null) {
            logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal."
                + "noBehaviour", userLocale, configurationReference));
            return;
        }
        String language = (String) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_LANGUAGE);
        DocumentReference targetDocumentReference =
            new DocumentReference(new EntityReference(xwiki.DEFAULT_SPACE_HOMEPAGE,
                EntityType.DOCUMENT, targetReference));
        List<String> subTargetDocumentsString = queryPages(targetDocumentReference);
        if (publicationBehaviour.equals(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR_CANCEL)
            && !isEmptyTargetSpace(subTargetDocumentsString, targetDocumentReference))
        {
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal."
                + "targetNotEmpty", userLocale, targetReference));
            return;
        } else if (publicationBehaviour.equals(
            BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR_REPUBLISH))
        {
            // Clear the destination space
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal."
                + "targetEmptying", userLocale, targetReference));
            if (xwiki.exists(targetDocumentReference, xcontext)) {
                // also removing the top page
                subTargetDocumentsString.add(localSerializer.serialize(targetDocumentReference));
            }
            logger.debug("[publishInternal] Emptying the destination space, removing [{}] from wiki [{}].",
                subTargetDocumentsString, targetDocumentReference.getWikiReference());
            removeDocuments(subTargetDocumentsString, targetDocumentReference, userDocumentReference);
        }

        // The source is store as page, but we need a complete location.WebHome reference
        if (!Objects.equals(sourceReference.getName(), this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE)) {
            SpaceReference sourceParentSpaceReference = new SpaceReference(
                    new EntityReference(sourceReference.getName(), EntityType.SPACE, sourceReference.getParent()));
            sourceReference =
                    new DocumentReference(new EntityReference(this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE,
                            EntityType.DOCUMENT, sourceParentSpaceReference));
        }
        if (!xwiki.exists(sourceReference, xcontext)) {
            logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal."
                + "sourceNotExist", userLocale, sourceReference));
            return;
        }
        DocumentReference collectionReference = getVersionedCollectionReference(sourceReference);
        XWikiDocument collection =
            collectionReference != null ? xwiki.getDocument(collectionReference, xcontext) : null;
        DocumentReference versionReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VERSION);
        XWikiDocument version = versionReference != null ? xwiki.getDocument(versionReference, xcontext) : null;

        String publicationComment = collection != null && version != null ? publicationComment =
            "Published from [" + collection.getTitle() + "], version [" + version.getTitle() + "]." : null;

        DocumentReference variantReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VARIANT);
        XWikiDocument variant = variantReference != null ? xwiki.getDocument(variantReference, xcontext) : null;

        Map<String, Map<DocumentReference, DocumentReference>> publishedLibraries =
            collection != null && versionReference != null && isBook(collectionReference)
                ? getUsedPublishedLibrariesWithInheritance(collection.getDocumentReference(), versionReference) : null;

        UserReference userReference = userReferenceResolver.resolve(userDocumentReference);

        // Execute publication job
        logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                + ".startPublication", userLocale));
        List<String> pageReferenceTree = getPageReferenceTree(sourceReference);
        List<DocumentReference> markedAsDeletedReferences = new ArrayList<>();
        int i = 1;
        int pageQuantity = pageReferenceTree != null ? pageReferenceTree.size() : 0;
        progressManager.pushLevelProgress(pageQuantity, this);
        for (String pageStringReference : pageReferenceTree) {
            if (pageStringReference == null) {
                logger.debug("[publishInternal] Page publication cancelled because the page reference is null.");
                logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                    + ".refNotFound", userLocale));
                continue;
            }

            progressManager.startStep(this, pageStringReference);
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                + ".startPagePublication", userLocale, i, pageQuantity, pageStringReference));
            i++;
            DocumentReference pageReference = referenceResolver.resolve(pageStringReference, configurationReference);

            if (!isPage(pageReference)) {
                logger.debug("[publishInternal] Page does not have a [{}] object.",
                    BookVersionsConstants.BOOKPAGE_CLASS_REFERENCE);
                logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                    + ".notCollection", userLocale));
                continue;
            }
            XWikiDocument page = xwiki.getDocument(pageReference, xcontext);

            // Get the relevant content for the page
            DocumentReference contentPageReference = getContentPage(page, configuration);
            logger.debug("[publishInternal] For page [{}], the content will be taken from [{}]",
                page.getDocumentReference(), contentPageReference);
            if (contentPageReference == null) {
                logger.debug("[publishInternal] Page publication cancelled because the content to be published can't "
                    + "be found by getContentPage. One input is probably null.");
                logger.warn(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                    + ".noContent", userLocale));
                continue;
            }

            // Get the published reference
            DocumentReference publishedReference =
                getPublishedReference(pageReference, collectionReference, targetReference);
            if (publishedReference == null) {
                logger.debug("[publishInternal] Page publication cancelled because the published reference can't be "
                    + "computed by getPublishedReference. One input is null.");
                logger.error(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                    + ".targetRefNotComputed", userLocale));
                continue;
            }

            // Check if the content should be published
            XWikiDocument contentPage = xwiki.getDocument(contentPageReference, xcontext).clone();
            if (!isToBePublished(contentPage, variant, configuration, userLocale)) {
                if (isMarkedDeleted(contentPage)) {
                    // The original document is marked as deleted, add the published copy to be deleted from target
                    markedAsDeletedReferences.add(publishedReference);
                }
                continue;
            }

            // Create the published document
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                + ".copyPage", userLocale, contentPage.getDocumentReference(), publishedReference));
            XWikiDocument publishedDocument = xwiki.getDocument(publishedReference, xcontext);
            if (StringUtils.isNotEmpty(language)) {
                // Change the original content if a translation is to be published
                mergeTranslatedContent(contentPage, publishedDocument, language);
            }
            copyContentsToNewVersion(contentPage, publishedDocument, xcontext);

            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                + ".transformContent", userLocale));
            prepareForPublication(contentPage, publishedDocument, publishedLibraries, configuration);

            logger.debug("[publishInternal] Publish page.");
            publishedDocument.getAuthors().setEffectiveMetadataAuthor(userReference);
            publishedDocument.getAuthors().setOriginalMetadataAuthor(userReference);
            xwiki.saveDocument(publishedDocument, publicationComment, xcontext);
            logger.debug("[publishInternal] End working on page [{}].", pageStringReference);
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                + ".endPagePublication", userLocale, pageStringReference));
            progressManager.endStep(this);
        }

        // Remove the pages marked as deleted
        if (publicationBehaviour.equals(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHBEHAVIOUR_UPDATE)
            && markedAsDeletedReferences.size() > 0)
        {
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                + ".removeMarkedAsDeleted", userLocale));
            logger.debug("[publishInternal] Removing the following marked as deleted pages [{}].",
                markedAsDeletedReferences);
            removeDocuments(markedAsDeletedReferences, userDocumentReference);
        }

        if ((boolean) configuration.get("publishPageOrder")) {
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
                + ".updatePageOrder", userLocale));
            copyPinnedPagesInfo(sourceReference, collectionReference, targetReference, publicationComment,
                configurationReference, userReference);
        }

        // Add metadata in the collection page (master) and top page (published space)
        logger.debug("[publishInternal] Adding metadata on master and published space top pages.");
        addMasterPublicationData(collection, configuration, userReference);
        addTopPublicationData(targetReference, publicationComment, collection, configuration, userReference,
            collectionReference);

        logger.debug("[publishInternal] Publication ended.");
        logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.publishInternal"
            + ".endPublication", userLocale, targetDocumentReference));
        progressManager.popLevelProgress(this);
    }

    /**
     * Remove the given documents
     *
     * @param documentReferencesString the documents to remove
     * @param referenceParameter the reference used as a parameter to resolve the document references
     * @param userReference the reference of the user who removes the documents
     * @throws XWikiException happens if there is an issue with the document deletion
     */
    private void removeDocuments(List<String> documentReferencesString, DocumentReference referenceParameter,
        DocumentReference userReference)
        throws XWikiException
    {
        if (documentReferencesString == null || referenceParameter == null) {
            return;
        }
        List<DocumentReference> toDeleteReferences = new ArrayList<>();
        for (String toDeleteRefString : documentReferencesString) {
            toDeleteReferences.add(referenceResolver.resolve(toDeleteRefString, referenceParameter));
        }
        removeDocuments(toDeleteReferences, userReference);
    }

    /**
     * Remove the given documents
     *
     * @param documentReferences the documents to remove
     * @param userReference the reference of the user who removes the documents
     * @throws XWikiException happens if there is an issue with the document deletion or checking its existence
     */
    private void removeDocuments(List<DocumentReference> documentReferences, DocumentReference userReference)
        throws XWikiException
    {
        if (documentReferences == null) {
            return;
        }
        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();

        for (DocumentReference toDeleteRef : documentReferences) {
            if (xwiki.exists(toDeleteRef, xcontext)) {
                logger.debug("[removeDocuments] Deleting [{}].", toDeleteRef);
                xcontext.setUserReference(userReference);
                xwiki.deleteDocument(xwiki.getDocument(toDeleteRef, xcontext), xcontext);
            }
        }
    }

    /**
     * Merge the new document's translated content with the current published document's content. This has some
     * limitations:
     * - only the first "Translated" macro of the right language will be considered
     * - the content outside the macro will be ignored
     * - New content will only be added to the end of the published document
     * @param masterDocument the document to take the translated content from
     * @param publishedDocument the document to which the translated content has to be merged into
     * @param language the language to publish
     * @throws XWikiException happens if checking if document exists or setting the XDOM content have an issue
     */
    private void mergeTranslatedContent(XWikiDocument masterDocument, XWikiDocument publishedDocument, String language)
        throws XWikiException
    {
        if (masterDocument == null || publishedDocument == null || language == null) {
            logger.error("[mergeTranslatedContent] A parameter is null: masterDocument [{}], publishedDocument [{}], "
                    + "language [{}]", masterDocument.getDocumentReference(), publishedDocument.getDocumentReference(),
                language);
            return;
        }

        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        // Look for the content to be published
        logger.debug("[mergeTranslatedContent] Searching for the translated content of [{}] language in the "
            + "content to be published.", language);
        XDOM masterXdom = masterDocument.getXDOM();
        MacroBlock masterBlock = getTranslatedMacroBlock(masterXdom, language);
        if (masterBlock == null) {
            logger.debug("[mergeTranslatedContent] No [{}] translated content to be published found.", language);
            return;
        }

        // Search if some translated content has to be replaced in the currently published document
        Block toPublishBlock = null;
        if (xwiki.exists(publishedDocument.getDocumentReference(), xcontext)) {
            logger.debug("[mergeTranslatedContent] Searching for existing translated content of [{}] in already "
                + "existing published document.", language);
            XDOM publishedXdom = publishedDocument.getXDOM();
            MacroBlock publishedBlock = getTranslatedMacroBlock(publishedXdom, language);
            if (publishedBlock == null) {
                logger.debug("[mergeTranslatedContent] No existing translated published content, adding translated "
                    + "content macro at the end of the published of the published document.");
                publishedXdom.addChild(masterBlock);
                toPublishBlock = publishedXdom;
            } else {
                logger.debug("[mergeTranslatedContent] Found existing published content, replacing it by the new the "
                    + "content to be published.");
                publishedXdom.replaceChild(masterBlock, publishedBlock);
                toPublishBlock = publishedXdom;
            }
        } else {
            logger.debug("[mergeTranslatedContent] No existing published content, adding macro as new document.");
            toPublishBlock = masterBlock;
        }
        masterDocument.setContent(new XDOM(Collections.singletonList(toPublishBlock)));
    }

    /**
     * Get the first XDOM block of content translation macro for the given language, with the status "Translated", in
     * the given document
     *
     * @param xdom the xdom of the document to get the macro block from
     * @param language the language of the macro
     * @return the macro block found, or null if none
     */
    private MacroBlock getTranslatedMacroBlock(XDOM xdom, String language)
    {
        if (xdom == null || language == null) {
            logger.error("[mergeTranslatedContent] A parameter is null: xdom [{}], language [{}]", xdom,
                language);
            return null;
        }

        logger.debug("[getTranslatedMacroBlock] Looking for the first [{}] macro, with the [{}] status, for language "
                + "[{}].", BookVersionsConstants.CONTENTTRANSLATION_MACRO_ID,
            PageTranslationStatus.TRANSLATED, language);
        List<MacroBlock> listBlock = xdom.getBlocks(new ClassBlockMatcher(
            new MacroBlock(BookVersionsConstants.CONTENTTRANSLATION_MACRO_ID, Collections.emptyMap(),
                true).getClass()), Block.Axes.DESCENDANT_OR_SELF);
        logger.debug("[getTranslatedMacroBlock] Found [{}] [{}] macros", listBlock.size(),
            BookVersionsConstants.CONTENTTRANSLATION_MACRO_ID);
        for (MacroBlock macroBlock : listBlock) {
            String macroLanguage = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);
            if (macroLanguage != null && !macroLanguage.isEmpty()) {
                if (macroLanguage.equals(language)) {
                    logger.debug("[getTranslatedMacroBlock] Macro language is [{}].", macroLanguage);
                    String macroStatus = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_STATUS);
                    if (StringUtils.isNotEmpty(macroStatus)
                        && macroStatus.toLowerCase().equals(PageTranslationStatus.TRANSLATED.getTranslationStatus()))
                    {
                        logger.debug("[getTranslatedMacroBlock] Macro status is [{}]. Returning this one.",
                            macroStatus);
                        return macroBlock;
                    } else {
                        logger.debug("[getTranslatedMacroBlock] Macro status is [{}]. Continue searching.",
                            macroStatus);
                    }
                } else {
                    logger.debug("[getTranslatedMacroBlock] Macro language is [{}]. Continue searching.",
                        macroLanguage);
                }
            }
        }
        logger.debug("[getTranslatedMacroBlock] No [{}] content found for language [{}].",
            BookVersionsConstants.PAGETRANSLATION_STATUS, language);
        return null;
    }

    /**
     * Return if the given space is empty, except for his WebHome and WebPreferences pages
     *
     * @param subTargetDocumentsString list of pages under the target space
     * @param targetDocumentReference WebHome document of the target space
     * @return true if space is empty, except for his WebHome and WebPreferences pages. False if any parameter is null
     */
    private boolean isEmptyTargetSpace(List<String> subTargetDocumentsString, DocumentReference targetDocumentReference)
    {
        if (subTargetDocumentsString == null || targetDocumentReference == null) {
            return false;
        }
        subTargetDocumentsString.remove(localSerializer.serialize(targetDocumentReference));
        subTargetDocumentsString.remove(localSerializer.serialize(
            new DocumentReference(new EntityReference(BookVersionsConstants.XWIKI_PAGEADMINISTRATION_NAME,
                EntityType.DOCUMENT, targetDocumentReference.getLastSpaceReference()))));
        return subTargetDocumentsString.isEmpty();
    }

    // Adapted from org.xwiki.workflowpublication.internal.DefaultPublicationWorkflow (Publication Workflow Application)
    protected boolean copyContentsToNewVersion(XWikiDocument fromDocument, XWikiDocument toDocument,
        XWikiContext xcontext) throws XWikiException
    {
        if (fromDocument == null || toDocument == null) {
            return false;
        }

        // use a fake 3 way merge: previous is toDocument without comments, rights and wf object
        // current version is current toDocument
        // next version is fromDocument without comments, rights and wf object
        XWikiDocument previousDoc = toDocument.clone();
        this.removeObjectsForPublication(previousDoc);
        // set reference and language

        // make sure that the attachments are properly loaded in memory for the duplicate to work fine, otherwise it's a
        // bit impredictable about attachments
        fromDocument.loadAttachments(xcontext);
        XWikiDocument nextDoc = fromDocument.duplicate(toDocument.getDocumentReference());
        this.removeObjectsForPublication(nextDoc);

        // and now merge. Normally the attachments which are not in the next doc are deleted from the current doc
        MergeResult result = toDocument.merge(previousDoc, nextDoc, new MergeConfiguration(), xcontext);

        // for some reason the creator doesn't seem to be copied if the toDocument is new, so let's put it
        if (toDocument.isNew()) {
            toDocument.setCreatorReference(fromDocument.getCreatorReference());
        }
        // Author does not seem to be merged anymore in the merge function in newer versions, so we'll do it here
        toDocument.setAuthorReference(fromDocument.getAuthorReference());

        List<LogEvent> exception = result.getLog().getLogs(LogLevel.ERROR);
        if (exception.isEmpty()) {
            return true;
        } else {
            StringBuffer exceptions = new StringBuffer();
            for (LogEvent e : exception) {
                if (exceptions.length() == 0) {
                    exceptions.append(";");
                }
                exceptions.append(e.getMessage());
            }
            throw new XWikiException(XWikiException.MODULE_XWIKI_DOC, XWikiException.ERROR_XWIKI_UNKNOWN,
                "Could not copy document contents from "
                    + localSerializer.serialize(fromDocument.getDocumentReference()) + " to document "
                    + localSerializer.serialize(toDocument.getDocumentReference()) + ". Caused by: "
                    + exceptions.toString());
        }
    }

    private void addTopPublicationData(SpaceReference targetTopReference, String publicationComment,
        XWikiDocument collection, Map<String, Object> configuration,
        UserReference userReference, DocumentReference collectionReference) throws XWikiException
    {
        if (targetTopReference == null || collection == null || configuration == null) {
            return;
        }

        logger.debug("[addTopPublicationData] Adding metadata to [{}]", targetTopReference);
        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        DocumentReference versionReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VERSION);
        XWikiDocument version = xwiki.getDocument(versionReference, xcontext);
        DocumentReference variantReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VARIANT);

        // Set metadata
        DocumentReference targetDocumentReference =
            new DocumentReference(new EntityReference(xwiki.DEFAULT_SPACE_HOMEPAGE, EntityType.DOCUMENT,
                targetTopReference));
        XWikiDocument targetTop = xwiki.getDocument(targetDocumentReference, xcontext).clone();
        BaseObject publicationObject =
            targetTop.getXObject(BookVersionsConstants.PUBLISHEDCOLLECTION_CLASS_REFERENCE);
        if (publicationObject == null) {
            publicationObject =
                targetTop.newXObject(BookVersionsConstants.PUBLISHEDCOLLECTION_CLASS_REFERENCE, xcontext);
        }
        publicationObject.set(BookVersionsConstants.PUBLISHEDCOLLECTION_PROP_MASTERNAME, collection.getTitle(),
            xcontext);
        publicationObject.set(BookVersionsConstants.PUBLISHEDCOLLECTION_PROP_VERSIONNAME, version.getTitle(), xcontext);
        List languages = publicationObject.getListValue(BookVersionsConstants.PUBLISHEDCOLLECTION_PROP_LANGUAGES);
        String publicationLanguage =
            (String) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_LANGUAGE);
        if (StringUtils.isNotEmpty(publicationLanguage)) {
            if (!languages.contains(publicationLanguage)) {
                languages.add(publicationLanguage);
            }
        } else {
            languages = getConfiguredLanguages(collectionReference);
        }
        publicationObject.set(BookVersionsConstants.PUBLISHEDCOLLECTION_PROP_LANGUAGES, languages, xcontext);
        if (variantReference != null) {
            XWikiDocument variant = xwiki.getDocument(variantReference, xcontext);
            publicationObject.set(BookVersionsConstants.PUBLISHEDCOLLECTION_PROP_VARIANTNAME, variant.getTitle(),
                xcontext);
        }
        targetTop.getAuthors().setEffectiveMetadataAuthor(userReference);
        targetTop.getAuthors().setOriginalMetadataAuthor(userReference);
        xwiki.saveDocument(targetTop, publicationComment != null ? publicationComment : "", xcontext);
    }

    private void addMasterPublicationData(XWikiDocument collection, Map<String, Object> configuration,
        UserReference userReference) throws XWikiException
    {
        if (collection == null || configuration == null) {
            return;
        }

        logger.debug("[addMasterPublicationData] Adding metadata to [{}]", collection.getDocumentReference());
        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        DocumentReference sourceReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE);
        SpaceReference destinationSpaceReference =
            (SpaceReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_DESTINATIONSPACE);
        DocumentReference destinationReference =
            new DocumentReference(new EntityReference(xwiki.DEFAULT_SPACE_HOMEPAGE,
                EntityType.DOCUMENT, destinationSpaceReference));
        DocumentReference versionReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VERSION);
        XWikiDocument version = versionReference != null ? xwiki.getDocument(versionReference, xcontext) : null;
        DocumentReference variantReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VARIANT);
        XWikiDocument collectionClone = collection.clone();

        if (sourceReference == null || versionReference == null || version == null) {
            logger.error(
                "Couldn't read the publication configuration when attempting to add metadata in the master and "
                    + "published locations.");
            return;
        }

        String publicationId;
        String publicationComment;
        if (variantReference == null) {
            publicationId = versionReference.getName();
            publicationComment =
                "Publication of space [" + sourceReference + "], version [" + version.getTitle() + "].";
        } else {
            XWikiDocument variant = xwiki.getDocument(variantReference, xcontext);
            publicationId = versionReference.getName() + "-" + variantReference.getName();
            publicationComment = "Publication of space [" + sourceReference + "], version [" + version.getTitle()
                + "], variant [" + variant.getTitle() + "].";
        }

        // Check existing publication objects
        logger.debug("[addMasterPublicationData] Checking if an object already exists for ID [{}] and source [{}].",
            publicationId, sourceReference);
        BaseObject publicationObject = null;
        for (BaseObject XObject : collectionClone.getXObjects(BookVersionsConstants.PUBLICATION_CLASS_REFERENCE)) {
            String objectId = XObject.getStringValue(BookVersionsConstants.PUBLICATION_PROP_ID);
            String objectSource = XObject.getStringValue(BookVersionsConstants.PUBLICATION_PROP_SOURCE);
            if (publicationId != null && publicationId.equals(objectId) && objectSource != null
                && sourceReference.toString().equals(objectSource))
            {
                // Previous publication of the same space, with same version and variant found
                logger.debug("[addMasterPublicationData] An object already exists.");
                publicationObject = XObject;
                break;
            }
        }
        if (publicationObject == null) {
            // No previous publication found, create a new one
            logger.debug("[addMasterPublicationData] A new object is added.");
            publicationObject = collectionClone.newXObject(BookVersionsConstants.PUBLICATION_CLASS_REFERENCE, xcontext);
        }

        // Add metadata
        publicationObject.set(BookVersionsConstants.PUBLICATION_PROP_ID, publicationId, xcontext);
        publicationObject.set(BookVersionsConstants.PUBLICATION_PROP_SOURCE, sourceReference.toString(), xcontext);
        publicationObject.set(BookVersionsConstants.PUBLICATION_PROP_PUBLISHEDSPACE, destinationReference.toString(),
            xcontext);
        collectionClone.getAuthors().setEffectiveMetadataAuthor(userReference);
        collectionClone.getAuthors().setOriginalMetadataAuthor(userReference);
        xwiki.saveDocument(collectionClone, publicationComment, xcontext);
    }

    /**
     * Get the published space of a given collection for a given publication ID and source.
     *
     * @param collectionReference the collection
     * @param publicationId the publication ID
     * @param sourceReference the source
     * @return the reference of the published space
     * @throws XWikiException
     */
    private DocumentReference getCollectionPublishedSpace(DocumentReference collectionReference, String publicationId,
        DocumentReference sourceReference) throws XWikiException
    {
        if (publicationId == null || collectionReference == null || sourceReference == null
            || (!isBook(collectionReference) && !isLibrary(collectionReference)))
        {
            return null;
        }

        logger.debug("[getCollectionPublishedSpace] Search the published space for collection [{}], id [{}] and "
            + "source [{}].", collectionReference, publicationId, sourceReference);
        XWikiContext xcontext = this.getXWikiContext();
        XWiki xwiki = xcontext.getWiki();
        XWikiDocument collection = xwiki.getDocument(collectionReference, xcontext);
        DocumentReference publishedSpaceReference = null;
        for (BaseObject XObject : collection.getXObjects(BookVersionsConstants.PUBLICATION_CLASS_REFERENCE)) {
            String objectId = XObject.getStringValue(BookVersionsConstants.PUBLICATION_PROP_ID);
            String objectSource = XObject.getStringValue(BookVersionsConstants.PUBLICATION_PROP_SOURCE);
            if (objectId != null && publicationId.equals(objectId) && objectSource != null
                && sourceReference.toString().equals(objectSource))
            {
                publishedSpaceReference = referenceResolver
                    .resolve(XObject.getStringValue(BookVersionsConstants.PUBLICATION_PROP_PUBLISHEDSPACE));
                logger.debug("[getCollectionPublishedSpace] The collection was published in [{}]",
                    publishedSpaceReference);
                break;
            }
        }

        return publishedSpaceReference;
    }

    private static DocumentReference getPublishedReference(DocumentReference originalReference,
        DocumentReference collectionReference, SpaceReference targetReference)
    {
        if (originalReference == null || collectionReference == null || targetReference == null) {
            return null;
        }

        DocumentReference publishedReference =
            originalReference.replaceParent(collectionReference.getParent(), targetReference);

        return publishedReference;
    }

    private XWikiDocument removeObjectsForPublication(XWikiDocument publishedPage)
    {
        if (publishedPage == null) {
            return null;
        }

        for (EntityReference objectRef : BookVersionsConstants.PUBLICATION_REMOVEDOBJECTS) {
            publishedPage.removeXObjects(objectRef);
        }

        return publishedPage;
    }

    private XWikiDocument prepareForPublication(XWikiDocument originalDocument, XWikiDocument publishedDocument,
        Map<String, Map<DocumentReference, DocumentReference>> publishedLibraries, Map<String, Object> configuration)
        throws XWikiException, ComponentLookupException, ParseException, QueryException
    {
        if (originalDocument == null || publishedDocument == null || configuration == null) {
            return null;
        }

        // Execute here all transformations on the document: change links, point to published library,
        logger.debug("[prepareForPublication] Apply changes on [{}] for publication.",
            publishedDocument.getDocumentReference());
        // Work directly on the document
        publishedDocument.setTitle(originalDocument.getTitle());
        publishedDocument.setHidden(false);
        // Work on the XDOM
        XDOM xdom = publishedDocument.getXDOM();
        String syntax = publishedDocument.getSyntax().toIdString();
        transformXDOM(xdom, syntax, originalDocument.getDocumentReference(), publishedLibraries, configuration);
        // Set the modified XDOM
        publishedDocument.setContent(xdom);
        return publishedDocument;
    }

    // Heavily inspired from "Bulk update links according to a TSV mapping using XDOM" on https://snippets.xwiki.org
    private boolean transformXDOM(XDOM xdom, String syntaxId, DocumentReference originalDocumentReference,
        Map<String, Map<DocumentReference, DocumentReference>> publishedLibraries, Map<String, Object> configuration)
        throws ComponentLookupException, ParseException, QueryException, XWikiException
    {
        if (xdom == null || syntaxId == null || originalDocumentReference == null || configuration == null) {
            logger.error("[transformXDOM] Can't execute because one input is null.");
            return false;
        }

        boolean hasXDOMChanged = false;
        DocumentReference publishedVariantReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VARIANT);
        DocumentReference versionReference =
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VERSION);
        String language = (String) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_LANGUAGE);

        // First, update any document macro that could contain nested content (variant macro)

        for (Block b : xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT_OR_SELF)) {
            MacroBlock block = (MacroBlock) b;
            String id = block.getId();

            // Should never happen.
            if (id == null) {
                logger.error("[transformXDOM] Can't find macro block id.");
                continue;
            }

            String content = block.getContent();
            logger.debug("[transformXDOM] Checking macro [{}] - [{}]", id, block.getClass());

            // Get the possible variants from the variant macro
            String variants = block.getParameter(BookVersionsConstants.VARIANT_MACRO_PROP_NAME);
            List<DocumentReference> variantReferences = new ArrayList<>();
            if (variants != null) {
                for (String variant : variants.split(",")) {
                    variantReferences.add(referenceResolver.resolve(variant, originalDocumentReference));
                }
            }

            if (id.equals(BookVersionsConstants.VARIANT_MACRO_ID) && ((publishedVariantReference == null)
                || (publishedVariantReference != null && !variantReferences.contains(publishedVariantReference))))
            {
                // The macro is removed if it's a macro variant AND ((no variant is published)) OR (variant is
                // published but macro is not for the published variant)
                logger.debug("[transformXDOM] Variant macro is for [{}], it is removed from content.",
                    variantReferences);
                block.getParent().removeBlock(block);
            } else {
                if (content == null || StringUtils.isEmpty(content)) {
                    continue;
                }

                // We will take a quick shortcut here and directly parse the macro content with the syntax of the
                // document
                logger.debug("[transformXDOM] Calling parse on [{}] with syntax [{}]", id, syntaxId);
                Parser parser = componentManagerProvider.get().getInstance(Parser.class, syntaxId);
                XDOM contentXDOM = parser.parse(new StringReader(content));
                boolean hasMacroContentChanged =
                    transformXDOM(contentXDOM, syntaxId, originalDocumentReference, publishedLibraries, configuration);
                if (hasMacroContentChanged || id.equals(BookVersionsConstants.VARIANT_MACRO_ID)) {
                    logger.debug("[transformXDOM] The content of macro [{}] has changed", id);
                    if (id.equals(BookVersionsConstants.VARIANT_MACRO_ID)) {
                       logger.debug("[transformXDOM] Variant macro is for [{}], it is replaced by the {} macro.",
                           variantReferences, BookVersionsConstants.INLINE_MACRO_ID);
                    }
                    WikiPrinter printer = new DefaultWikiPrinter();
                    BlockRenderer renderer =
                        this.componentManagerProvider.get().getInstance(BlockRenderer.class, syntaxId);
                    renderer.render(contentXDOM, printer);
                    String newMacroContent = printer.toString();
                    // Create a new macro block and swap it
                    MacroBlock newMacroBlock =
                        new MacroBlock(id, block.getParameters(), newMacroContent, block.isInline());
                    if (id.equals(BookVersionsConstants.VARIANT_MACRO_ID)) {
                        newMacroBlock = new MacroBlock(BookVersionsConstants.INLINE_MACRO_ID, new HashMap<>(),
                        newMacroContent, block.isInline());
                    }
                    block.getParent().replaceChild(newMacroBlock, block);
                    hasXDOMChanged = true;
                }
            }
        }

        boolean transformedTranslation = transformTranslation(xdom, language);
        boolean transformedLibrary =
            transformLibrary(xdom, originalDocumentReference, publishedLibraries, versionReference);
        Map<DocumentReference, DocumentReference> currentPublishedLibraries =
            publishedLibraries != null ? publishedLibraries.get(getVersionName(versionReference)) : new HashMap<>();
        boolean transformedReferences = publicationReferencesTransformationHelper.transform(xdom,
            originalDocumentReference, currentPublishedLibraries, configuration);
        return hasXDOMChanged || transformedTranslation || transformedLibrary || transformedReferences;
    }

    private boolean transformTranslation(XDOM xdom, String language)
    {
        if (xdom == null) {
            return false;
        }
        logger.debug("[transformTranslation] Starting to remove macro [{}] with not a [{}] status.",
            BookVersionsConstants.CONTENTTRANSLATION_MACRO_ID, PageTranslationStatus.TRANSLATED);
        if (StringUtils.isNotEmpty(language)) {
            logger.debug("[transformTranslation] Removing canceled as only [{}] are part of the publication of "
                + "language [{}].", PageTranslationStatus.TRANSLATED, language);
        }

        boolean hasChanged = false;
        List<MacroBlock> listBlock = xdom.getBlocks(new ClassBlockMatcher(
            new MacroBlock(BookVersionsConstants.CONTENTTRANSLATION_MACRO_ID, Collections.emptyMap(),
                true).getClass()), Block.Axes.DESCENDANT_OR_SELF);
        logger.debug("[transformTranslation] Found [{}] [{}] macros.", listBlock.size(),
            BookVersionsConstants.CONTENTTRANSLATION_MACRO_ID);
        for (MacroBlock macroBlock : listBlock) {
            String macroStatus = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_STATUS);
            if (StringUtils.isNotEmpty(macroStatus)
                && !macroStatus.toLowerCase().equals(PageTranslationStatus.TRANSLATED.getTranslationStatus()))
            {
                logger.debug("[transformTranslation] Status is [{}], macro is removed.", macroStatus);
                macroBlock.getParent().removeBlock(macroBlock);
                hasChanged = true;
            } else {
                logger.debug("[transformTranslation] Status is [{}], macro is kept.", macroStatus);
            }
        }
        return hasChanged;
    }

    private boolean transformLibrary(XDOM xdom, DocumentReference originalDocumentReference,
        Map<String, Map<DocumentReference, DocumentReference>> publishedLibraries, DocumentReference versionReference)
        throws QueryException, XWikiException
    {
        if (xdom == null || originalDocumentReference == null || versionReference == null) {
            return false;
        }

        logger.debug("[transformLibrary] Starting to transform includeLibrary macro reference");
        boolean hasChanged = false;
        List<MacroBlock> listBlock = xdom.getBlocks(
            new ClassBlockMatcher(
                new MacroBlock(BookVersionsConstants.INCLUDELIBRARY_MACRO_ID, Collections.emptyMap(), true).getClass()),
            Block.Axes.DESCENDANT_OR_SELF);
        logger.debug("[transformLibrary] [{}] '{}' macros found in the passed XDOM", listBlock.size(),
            BookVersionsConstants.INCLUDELIBRARY_MACRO_ID);

        String versionName = getVersionName(versionReference);
        if (isVersionedContent(originalDocumentReference)) {
            // If versioned page is published, use the inherited version, provided by the document to be published.
            // This allows to use library version corresponding to the inherited content version instead of the
            // selected version for publication.
            versionName = originalDocumentReference.getName();
        }

        for (MacroBlock macroBlock : listBlock) {
            // Get the key reference (library page reference)
            String keyRefString = macroBlock.getParameter(BookVersionsConstants.INCLUDELIBRARY_MACRO_PROP_KEYREFERENCE);
            if (keyRefString == null || StringUtils.isEmpty(keyRefString)) {
                logger.debug("[transformLibrary] {} macro found without {} parameter. Macro is ignored.",
                    BookVersionsConstants.INCLUDELIBRARY_MACRO_ID,
                    BookVersionsConstants.INCLUDELIBRARY_MACRO_PROP_KEYREFERENCE);
                continue;
            }

            DocumentReference libraryPageReference = referenceResolver.resolve(keyRefString, originalDocumentReference);
            logger.debug("[transformLibrary] Updating {} macro referencing [{}].",
                BookVersionsConstants.INCLUDELIBRARY_MACRO_ID, libraryPageReference);
            // Get the library reference
            DocumentReference libraryReference = getVersionedCollectionReference(libraryPageReference);
            // Get the published library reference
            DocumentReference publishedLibraryReference =
                publishedLibraries != null && versionName != null && libraryReference != null
                    ? publishedLibraries.get(versionName).get(libraryReference) : null;
            if (publishedLibraryReference == null) {
                logger.error("[transformLibrary] The library [{}] has not been published. Macro is ignored.",
                    libraryReference);
                continue;
            }

            // Compute the published page reference
            DocumentReference publishedPageReference =
                getPublishedReference(libraryPageReference, libraryReference,
                    publishedLibraryReference.getLastSpaceReference());
            if (publishedPageReference != null) {
                logger.debug("[transformLibrary] Page reference is changed to [{}].", publishedPageReference);
                // Replace the macro by include macro and change to the published reference
                MacroBlock newMacroBlock = new MacroBlock(BookVersionsConstants.INCLUDE_MACRO_ID,
                    Map.of(BookVersionsConstants.INCLUDE_MACRO_PROP_REFERENCE, publishedPageReference.toString()),
                    macroBlock.isInline());
                macroBlock.getParent().replaceChild(newMacroBlock, macroBlock);
                hasChanged = true;
            }
        }

        return hasChanged;
    }

    private void copyPinnedPagesInfo(DocumentReference sourceReference, DocumentReference collectionReference,
        SpaceReference targetReference, String publicationComment, DocumentReference configurationReference,
        UserReference userReference)
        throws QueryException, XWikiException
    {
        XWikiContext xcontext = getXWikiContext();
        XWiki xwiki = xcontext.getWiki();

        SpaceReference spaceReference = sourceReference.getLastSpaceReference();
        String spaceSerialized = localSerializer.serialize(spaceReference);
        String hql = "SELECT doc.fullName FROM XWikiDocument as doc "
            + "WHERE doc.name = 'WebPreferences' "
            + "  AND doc.fullName LIKE :docFullNamePrefix";
        List<String> webPreferences = this.queryManagerProvider.get()
            .createQuery(hql, Query.HQL)
            .bindValue("docFullNamePrefix").like(spaceSerialized + ".").anyChars().like("WebPreferences").query()
            .setWiki(sourceReference.getWikiReference().getName())
            .execute();

        for (String source : webPreferences) {
            DocumentReference sourceRef = referenceResolver.resolve(source, configurationReference);
            DocumentReference publishedReference =
                getPublishedReference(sourceRef, collectionReference, targetReference);
            if (publishedReference == null) {
                logger.debug("[publishInternal] Ignore pinned page for [{}] because 'publishedReference' is null",
                    sourceRef);
                continue;
            }

            XWikiDocument sourceDoc = xwiki.getDocument(sourceRef, xcontext);
            XWikiDocument publishedDoc = xwiki.getDocument(publishedReference, xcontext).clone();
            if (publishedDoc.isNew()) {
                publishedDoc.setHidden(true);
                publishedDoc.newXObject(new LocalDocumentReference("XWiki", "XWikiPreferences"), xcontext);
            }

            LocalDocumentReference pinnedPagesClass = new LocalDocumentReference("XWiki", "PinnedChildPagesClass");
            BaseObject pinnedPageObject = sourceDoc.getXObject(pinnedPagesClass);
            if (pinnedPageObject != null) {
                logger.debug("[publishInternal] Update pinned page for space [{}]",
                    sourceRef.getLastSpaceReference());
                BaseObject publishedDocPinnedPageObject = publishedDoc.getXObject(pinnedPagesClass);

                if (publishedDocPinnedPageObject == null) {
                    publishedDocPinnedPageObject = publishedDoc.newXObject(pinnedPagesClass, xcontext);
                }
                List<String> pinnedPagesList = (List<String>) pinnedPageObject.getListValue("pinnedChildPages");
                List<String> pinnedPagesListFiltered = pinnedPagesList.stream()
                    .filter(x -> {
                        // Note in the pinned pages in the list end with '/', so to resolve the page reference we
                        // need to remove this
                        String pageName = x.replaceAll("/$", "");
                        DocumentReference targetPinnedPage =
                            new DocumentReference("WebHome",
                                new SpaceReference(pageName, publishedReference.getLastSpaceReference()));
                        try {
                            return xwiki.exists(targetPinnedPage, xcontext);
                        } catch (XWikiException e) {
                            logger.error("Can't detect if page exist at ref [{}]", targetPinnedPage, e);
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

                publishedDocPinnedPageObject.setStringListValue("pinnedChildPages", pinnedPagesListFiltered);

                publishedDoc.getAuthors().setEffectiveMetadataAuthor(userReference);
                publishedDoc.getAuthors().setOriginalMetadataAuthor(userReference);
                xwiki.saveDocument(publishedDoc, publicationComment, xcontext);
            }
        }
    }

    private DocumentReference getContentPage(XWikiDocument page, Map<String, Object> configuration)
        throws QueryException, XWikiException
    {
        if (page == null || configuration == null) {
            return null;
        }

        boolean unversioned = (page.getXObject(BookVersionsConstants.BOOKPAGE_CLASS_REFERENCE)
            .getIntValue(BookVersionsConstants.BOOKPAGE_PROP_UNVERSIONED) == 1);
        return unversioned ? page.getDocumentReference() : getInheritedContentReference(page.getDocumentReference(),
            (DocumentReference) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_VERSION));
    }

    private boolean isToBePublished(XWikiDocument page, XWikiDocument variant, Map<String, Object> configuration,
        Locale userLocale)
    {
        if (page == null || configuration == null) {
            return false;
        }
        if (userLocale == null) {
            userLocale = new Locale(BookVersionsConstants.DEFAULT_LOCALE);
        }

        DocumentReference pageReference = page.getDocumentReference();
        List<DocumentReference> variants = getPageVariants(page);
        String status = getPageStatus(page);
        boolean publishOnlyComplete =
            (boolean) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_PUBLISHONLYCOMPLETE);
        boolean excludePagesOutsideVariant = false;
        if (variant != null) {
            excludePagesOutsideVariant = (variant.getXObject(BookVersionsConstants.VARIANT_CLASS_REFERENCE)
                .getIntValue(BookVersionsConstants.VARIANT_PROP_EXCLUDE) == 1);
        }
        String language = (String) configuration.get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_LANGUAGE);
        if (isMarkedDeleted(page)) {
            // Page is marked as deleted
            logger.debug("[isToBePublished] Page [{}] is ignored because it is marked as deleted.", pageReference);
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.isToBePublished."
                + "markedAsDeleted", userLocale, pageReference));
            return false;
        } else if (publishOnlyComplete && status != null
            && !status.equals(BookVersionsConstants.PAGESTATUS_PROP_STATUS_COMPLETE))
        {
            // Page doesn't have a "complete" status but only those are published
            logger.debug("[isToBePublished] Page [{}] is ignored because its status is [{}] and only [{}] page are "
                + "published.", pageReference, status, BookVersionsConstants.PAGESTATUS_PROP_STATUS_COMPLETE);
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.isToBePublished."
                + "status", userLocale, pageReference, status,
                BookVersionsConstants.PAGESTATUS_PROP_STATUS_COMPLETE));
            return false;
        } else if (variant == null && variants != null && !variants.isEmpty()) {
            // No variant to be published AND page is associated with variant(s)
            logger.debug("[isToBePublished] Page [{}] is ignored because it is associated with variants.",
                pageReference);
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.isToBePublished."
                + "variantPage", userLocale, pageReference));
            return false;
        } else if (variant != null && variants != null && !variants.contains(variant.getDocumentReference())
            && (excludePagesOutsideVariant || (!excludePagesOutsideVariant && !variants.isEmpty())))
        {
            // A variant is to be published AND the page is associated with other variant(s) AND
            // (pages outside the variant are excluded
            // OR pages outside the variant are excluded but the page is associated to the published variant)
            logger.debug("[isToBePublished] Page [{}] is ignored because it is not associated with the published "
                + "variant [{}].", pageReference, variant);
            logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.isToBePublished."
                    + "otherVariant", userLocale, pageReference, variant));
            return false;
        } else if (StringUtils.isNotEmpty(language)) {
            Map<String, Map<String, Object>> languageData = getLanguageData(page);
            if (languageData.get(language) == null) {
                // The page has no translation
                logger.debug("[isToBePublished] Page [{}] is ignored because it is not associated with the "
                    + "published language [{}].", pageReference, language);
                logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.isToBePublished."
                        + "noTranslation", userLocale, pageReference, language));
                return false;
            } else if (languageData.get(language).get(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED) == null
                || !((boolean) languageData.get(language).get(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED)))
            {
                // The page has no "Translated" translation
                logger.debug("[isToBePublished] Page [{}] is ignored because the translation doesn't have a [{}] "
                    + "status.", pageReference, PageTranslationStatus.TRANSLATED);
                logger.info(localization.getTranslationPlain("BookVersions.DefaultBookVersionsManager.isToBePublished."
                        + "noCompleteTranslation", userLocale, pageReference, PageTranslationStatus.TRANSLATED));
                return false;
            }
        }
        return true;
    }

    @Override
    public String getPageStatus(XWikiDocument page)
    {
        if (page == null) {
            return "";
        }
        BaseObject statusObj = page.getXObject(BookVersionsConstants.PAGESTATUS_CLASS_REFERENCE);
        if (statusObj == null) {
            return "";
        }
        return statusObj.getStringValue(BookVersionsConstants.PAGESTATUS_PROP_STATUS);
    }

    private List<String> getPageReferenceTree(DocumentReference sourceReference) throws QueryException
    {
        // Can be refactored with queryPages
        if (sourceReference == null) {
            return Collections.emptyList();
        }
        SpaceReference spaceReference = sourceReference.getLastSpaceReference();
        String spaceSerialized = localSerializer.serialize(spaceReference);
        String spacePrefix = spaceSerialized.replaceAll("([%_/])", "/$1").concat(".%");

        logger.debug("[getPageReferenceTree] spaceSerialized : [{}]", spaceSerialized);
        logger.debug("[getPageReferenceTree] spacePrefix : [{}]", spacePrefix);

        // Query inspired from getDocumentReferences of DefaultModelBridge.java in xwiki-platform
        List<String> result = this.queryManagerProvider.get()
            .createQuery(", BaseObject as obj where doc.fullName = obj.name and obj.className = :class "
                + "and doc.space like :space escape '/' order by doc.fullName asc", Query.HQL)
            .bindValue("class", localSerializer.serialize(BookVersionsConstants.BOOKPAGE_CLASS_REFERENCE))
            .bindValue("space", spacePrefix).setWiki(sourceReference.getWikiReference().getName()).execute();
        // add the source as first element, as it's given by the query
        result.add(0, sourceReference.toString());

        logger.debug("[getPageReferenceTree] result : [{}]", result);

        return result;
    }

    private Map<String, Map<String, Object>> getLanguageData(Document document)
    {

        Map<String, Map<String, Object>> languageData = new HashMap<String, Map<String, Object>>();
        if (document == null) {
            return languageData;
        }

        XDOM xdom = document.getXDOM();

        List<MacroBlock> macros = xdom.getBlocks(MACRO_MATCHER, Block.Axes.DESCENDANT_OR_SELF);
        for (MacroBlock macroBlock : macros) {
            if (macroBlock.getId().equals(BookVersionsConstants.CONTENTTRANSLATION_MACRO_ID)) {
                String language = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);

                if (language != null && !language.isEmpty()) {

                    // Title
                    String title = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_TITLE);

                    // Status
                    String statusParameterValue = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_STATUS);
                    PageTranslationStatus status = PageTranslationStatus.NOT_TRANSLATED;
                    if (statusParameterValue != null && !statusParameterValue.isEmpty()
                        && statusParameterValue.toLowerCase().equals("translated"))
                    {
                        status = PageTranslationStatus.TRANSLATED;
                    }
                    if (statusParameterValue != null && !statusParameterValue.isEmpty()
                        && statusParameterValue.toLowerCase().equals("outdated"))
                    {
                        status = PageTranslationStatus.OUTDATED;
                    }

                    // Default language
                    String isDefault = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT);

                    Map<String, Object> currentLanguageData = new HashMap<String, Object>();
                    currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_TITLE,
                        title != null && !title.isEmpty() ? title : "");
                    currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_STATUS,
                        status != null ? status : PageTranslationStatus.NOT_TRANSLATED);
                    if (languageData.get(language) != null
                        && languageData.get(language).get(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED) != null
                        && (boolean) languageData.get(language).get(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED)
                    )
                    {
                        // the current language already has a Translated status which should be kept
                        currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED, true);
                    } else {
                        currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED,
                            status == PageTranslationStatus.TRANSLATED);
                    }
                    currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT,
                        isDefault != null && !isDefault.isEmpty() ? Boolean.valueOf(isDefault) : false);

                    languageData.put(language, currentLanguageData);
                }
            }
        }

        return languageData;
    }

    @Override
    public Map<String, Map<String, Object>> getLanguageData(XWikiDocument document)
    {

        Map<String, Map<String, Object>> languageData = new HashMap<String, Map<String, Object>>();
        if (document == null) {
            return languageData;
        }

        XDOM xdom = document.getXDOM();

        List<MacroBlock> macros = xdom.getBlocks(MACRO_MATCHER, Block.Axes.DESCENDANT_OR_SELF);
        for (MacroBlock macroBlock : macros) {
            if (macroBlock.getId().equals(BookVersionsConstants.CONTENTTRANSLATION_MACRO_ID)) {
                String language = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);

                if (language != null && !language.isEmpty()) {

                    // Title
                    String title = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_TITLE);

                    // Status
                    String statusParameterValue = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_STATUS);
                    PageTranslationStatus status = PageTranslationStatus.NOT_TRANSLATED;
                    if (statusParameterValue != null && !statusParameterValue.isEmpty()
                        && statusParameterValue.toLowerCase().equals("translated"))
                    {
                        status = PageTranslationStatus.TRANSLATED;
                    }
                    if (statusParameterValue != null && !statusParameterValue.isEmpty()
                        && statusParameterValue.toLowerCase().equals("outdated"))
                    {
                        status = PageTranslationStatus.OUTDATED;
                    }

                    // Default language
                    String isDefault = macroBlock.getParameter(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT);

                    Map<String, Object> currentLanguageData = new HashMap<String, Object>();
                    currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_TITLE,
                        title != null && !title.isEmpty() ? title : "");
                    currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_STATUS,
                        status != null ? status : PageTranslationStatus.NOT_TRANSLATED);
                    if (languageData.get(language) != null
                        && languageData.get(language).get(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED) != null
                        && (boolean) languageData.get(language).get(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED)
                    )
                    {
                        // the current language already has a Translated status which should be kept
                        currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED, true);
                    } else {
                        currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_HASTRANSLATED,
                            status == PageTranslationStatus.TRANSLATED);
                    }
                    currentLanguageData.put(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT,
                        isDefault != null && !isDefault.isEmpty() ? Boolean.valueOf(isDefault) : false);

                    languageData.put(language, currentLanguageData);
                }
            }
        }

        return languageData;
    }

    @Override
    public void setLanguageData(XWikiDocument document, Map<String, Map<String, Object>> languageData)
    {
        if (document == null || languageData == null) {
            return;
        }

        for (Entry<String, Map<String, Object>> languageDataEntry : languageData.entrySet()) {
            String language = languageDataEntry.getKey();
            if (languageDataEntry == null || language == null) {
                continue;
            }

            BaseObject translationObject = null;

            for (BaseObject tObj : document.getXObjects(BookVersionsConstants.PAGETRANSLATION_CLASS_REFERENCE)) {
                String languageEntry = tObj.getStringValue(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);
                if (languageEntry != null && !languageEntry.isEmpty() && languageEntry.equals(language)) {
                    translationObject = tObj;
                    break;
                }
            }

            if (translationObject == null) {
                try {
                    translationObject =
                        document.newXObject(BookVersionsConstants.PAGETRANSLATION_CLASS_REFERENCE, getXWikiContext());
                    translationObject.setStringValue(BookVersionsConstants.PAGETRANSLATION_LANGUAGE, language);
                } catch (XWikiException e) {
                    logger.error("Could not set the translation data in document [{}]", document.getDocumentReference(),
                        e);
                    return;
                }
            }

            String title = (String) languageDataEntry.getValue().get(BookVersionsConstants.PAGETRANSLATION_TITLE);
            translationObject.setStringValue(BookVersionsConstants.PAGETRANSLATION_TITLE, title != null ? title : "");

            PageTranslationStatus status =
                (PageTranslationStatus) languageDataEntry.getValue().get(BookVersionsConstants.PAGETRANSLATION_STATUS);
            translationObject.setStringValue(BookVersionsConstants.PAGETRANSLATION_STATUS,
                status != null ? status.getTranslationStatus() : null);

            boolean isDefault =
                (boolean) languageDataEntry.getValue().get(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT);
            translationObject.setIntValue(BookVersionsConstants.PAGETRANSLATION_ISDEFAULT, isDefault ? 1 : 0);
        }

        // Now, remove translations that were deleted by the user or don't have a language defined
        for (BaseObject tObj : document.getXObjects(BookVersionsConstants.PAGETRANSLATION_CLASS_REFERENCE)) {
            String languageEntry = tObj.getStringValue(BookVersionsConstants.PAGETRANSLATION_LANGUAGE);
            if (languageEntry == null || languageEntry.isEmpty() || !languageData.containsKey(languageEntry)) {
                document.removeXObject(tObj);
            }
        }
    }

    @Override
    public void resetTranslations(XWikiDocument document)
    {
        if (document == null) {
            return;
        }

        for (BaseObject tObj : document.getXObjects(BookVersionsConstants.PAGETRANSLATION_CLASS_REFERENCE)) {
            document.removeXObject(tObj);
        }
    }

    @Override
    public List<String> getConfiguredLanguages(DocumentReference bookReference) throws XWikiException
    {
        if (bookReference == null) {
            return new ArrayList<String>();
        }

        XWikiContext xcontext = this.getXWikiContext();

        XWikiDocument bookDocument = xcontext.getWiki().getDocument(bookReference, xcontext);
        if (isBook(bookDocument)) {
            // Go to the location where the Languages definition is stored
            SpaceReference languagesParentSpaceReference =
                new SpaceReference(new EntityReference(BookVersionsConstants.LANGUAGES_LOCATION, EntityType.SPACE,
                    bookReference.getParent()));
            DocumentReference languagesDocumentReference =
                new DocumentReference(new EntityReference(this.getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE,
                    EntityType.DOCUMENT, languagesParentSpaceReference));

            XWikiDocument languagesDocument = xcontext.getWiki().getDocument(languagesDocumentReference, xcontext);
            BaseObject multilingualObj =
                languagesDocument.getXObject(BookVersionsConstants.BOOKLMULTILANGUAL_CLASS_REFERENCE);
            if (multilingualObj != null) {
                return (List<String>) multilingualObj
                    .getListValue(BookVersionsConstants.BOOKLMULTILANGUAL_PROP_LANGUAGES);
            }
        }

        return new ArrayList<String>();
    }

    @Override
    public void addLibraryReferenceClassObject(DocumentReference versionReference) throws XWikiException
    {
        if (versionReference == null) {
            return;
        }

        XWikiContext xcontext = this.getXWikiContext();
        LocalDocumentReference libraryReferenceClassRef =
            new LocalDocumentReference(Arrays.asList("BookVersions", "Code"), "LibraryReferenceClass");
        XWikiDocument versionDocument = xcontext.getWiki().getDocument(versionReference, xcontext).clone();
        versionDocument.createXObject(libraryReferenceClassRef, xcontext);
        UserReference userReference = userReferenceResolver.resolve(xcontext.getUserReference());
        versionDocument.getAuthors().setEffectiveMetadataAuthor(userReference);
        versionDocument.getAuthors().setOriginalMetadataAuthor(userReference);
        xcontext.getWiki().saveDocument(versionDocument, xcontext);
    }

    @Override
    public void removeLibraryReferenceClassObject(DocumentReference versionReference, int objectNumber)
        throws XWikiException
    {
        if (versionReference == null) {
            return;
        }

        XWikiContext xcontext = this.getXWikiContext();
        LocalDocumentReference libraryReferenceClassRef =
            new LocalDocumentReference(Arrays.asList("BookVersions", "Code"), "LibraryReferenceClass");
        XWikiDocument versionDocument = xcontext.getWiki().getDocument(versionReference, xcontext).clone();

        if (!versionDocument.isNew()) {
            List<BaseObject> objects = versionDocument.getXObjects(libraryReferenceClassRef);
            versionDocument.removeXObject(objects.get(objectNumber));
            UserReference userReference = userReferenceResolver.resolve(xcontext.getUserReference());
            versionDocument.getAuthors().setEffectiveMetadataAuthor(userReference);
            versionDocument.getAuthors().setOriginalMetadataAuthor(userReference);
            xcontext.getWiki().saveDocument(versionDocument, xcontext);
        }
    }

    @Override
    public String setPagesStatus(List<String> pageReferences, String namespaces,
        LiveDataConfiguration liveDataConfiguration, String newStatus) throws JobException
    {
        LiveDataBatchRequest jobRequest =
            new LiveDataBatchRequest(namespaces, liveDataConfiguration, pageReferences, newStatus);
        String jobId = BookVersionsConstants.SET_PAGE_STATUS_JOBID_PREFIX
            + BookVersionsConstants.SET_PAGE_STATUS_JOBID_SEPARATOR + Instant.now().toString();
        if (jobId != null) {
            jobRequest.setId(jobId);
            // The context won't be full in setPagesStatusInternal as it is executed by a job, so the user executing the
            // status change has to be passed as a parameter.
            jobRequest.setProperty("userReference", this.getXWikiContext().getUserReference());
            jobExecutor.execute(BookVersionsConstants.SET_PAGE_STATUS_JOBID, jobRequest);
        }
        return jobId;
    }

    /**
     * Get the XWiki context.
     *
     * @return the xwiki context.
     */
    protected XWikiContext getXWikiContext()
    {
        return contextProvider.get();
    }
}
