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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.bookversions.BookVersionsManager;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.EntityReferenceString;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.descriptor.ParameterDescriptor;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

/**
 * This component focuses on transforming references between Book Version pages at publication time. The following
 * transformations are supported :
 * <ul>
 * <li>Transformation of links containing document references, attachment references, page references, page attachment
 * references</li>
 * <li>Transformation of images containing attachment references, page attachment references</li>
 * <li>Transformation of macro parameters that are of type document reference, attachment reference, or have the display
 * type of entity reference string</li>
 * </ul>
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookPublicationReferencesTransformationHelper.class)
@Singleton
public class BookPublicationReferencesTransformationHelper
{
    // Note that we don't support interwiki links, as books cannot span over multiple wikis
    private static final List<ResourceType> SUPPORTED_DOCUMENT_RESOURCES =
        Arrays.asList(ResourceType.DOCUMENT, ResourceType.PAGE);

    private static final List<ResourceType> SUPPORTED_ATTACHMENT_RESOURCES =
        Arrays.asList(ResourceType.ATTACHMENT, ResourceType.PAGE_ATTACHMENT);

    private static final String DOCUMENTTREE_MACRO_ID = "documentTree";

    private static final String DOCUMENTTREE_PARAM_ROOT = "root";

    @Inject
    private Logger logger;

    @Inject
    @Named("current")
    private EntityReferenceResolver<String> currentEntityReferenceResolver;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @Inject
    private ComponentManager rootComponentManager;

    @Inject
    private Provider<BookVersionsManager> bookVersionsManagerProvider;

    @Inject
    private Provider<XWikiContext> contextProvider;

    /**
     * Transform the provided XDOM to update any wiki references stored in its content to point to published spaces.
     *
     * @param sourceCollectionReference the reference of the source collection (master)
     * @param publicationSourceReference the source in the publication configuration
     * @param xdom the xdom to transform
     * @param originalReference the reference of the document containing this xdom
     * @param publishedLibraries a map of the published libraries
     * @param publicationConfiguration the publication configuration
     * @return true if the xdom has been modified
     */
    public boolean transform(DocumentReference sourceCollectionReference, DocumentReference publicationSourceReference,
        XDOM xdom, DocumentReference originalReference, Map<DocumentReference, DocumentReference> publishedLibraries,
        Map<String, Object> publicationConfiguration) throws XWikiException
    {
        // Extract information about the master spaces and the publication space
        DocumentReference sourceDocumentReference = (DocumentReference) publicationConfiguration
            .get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_SOURCE);
        if (sourceDocumentReference == null) {
            return false;
        }

        SpaceReference sourceSpaceReference = (sourceDocumentReference).getLastSpaceReference();

        // If the source is a versioned content, then compute the parent with one level up
        try {
            if (bookVersionsManagerProvider.get().isVersionedContent(publicationSourceReference)) {
                sourceSpaceReference = (SpaceReference) sourceSpaceReference.getParent();
            }
        } catch (XWikiException e) {
            // Should never happen
            logger.error("Failed to check if the source page [{}] is versioned.", originalReference, e);
        }
        // Extract the target space reference
        SpaceReference targetSpaceReference = (SpaceReference) publicationConfiguration
            .get(BookVersionsConstants.PUBLICATIONCONFIGURATION_PROP_DESTINATIONSPACE);

        // Build the space references map
        Map<SpaceReference, SpaceReference> spaceReferencesMap = new HashMap<>();
        Map<SpaceReference, SpaceReference> collectionReferencesMap = new HashMap<>();
        // Add the published space
        spaceReferencesMap.put(sourceSpaceReference, targetSpaceReference);
        // Add the master space reference
        // Build the master reference on the target location of the collection by removing the pages from the target,
        // based on how many pages should be removed from the source
        // Ex : is source is Master.A.B and target is Publish.Me.Here.A.B
        // Then A.B (two pages) must be removed => the target location of the collection is Publish.Me.Here
        SpaceReference sourceCollectionSpaceReference = sourceCollectionReference.getLastSpaceReference();
        if (!sourceCollectionReference.equals(sourceDocumentReference) && sourceCollectionReference != null) {
            collectionReferencesMap.put(sourceCollectionSpaceReference, getPublishCollectionReference(
                sourceDocumentReference, targetSpaceReference, sourceCollectionSpaceReference));
        }

        for (Map.Entry<DocumentReference, DocumentReference> entry : publishedLibraries.entrySet()) {
            if (entry.getValue() != null) {
                spaceReferencesMap.put(entry.getKey().getLastSpaceReference(),
                    entry.getValue().getLastSpaceReference());
            }
        }

        return transform(xdom, originalReference, spaceReferencesMap, collectionReferencesMap);
    }

    private SpaceReference getPublishCollectionReference(DocumentReference sourceSpaceReference,
        SpaceReference targetSpaceReference, SpaceReference sourceCollectionReference)
    {
        SpaceReference publishCollectionReference = targetSpaceReference;
        for (EntityReference entityReference : getReversedSpaceReferences(
            sourceSpaceReference.getLastSpaceReference())) {
            if (EntityType.SPACE.equals(entityReference.getType())
                && sourceCollectionReference.equals(entityReference)) {
                break;
            }
            publishCollectionReference = (SpaceReference) publishCollectionReference.getParent();
        }

        return publishCollectionReference;
    }

    private List<SpaceReference> getReversedSpaceReferences(SpaceReference spaceReference)
    {
        List<SpaceReference> references = new ArrayList<SpaceReference>();

        EntityReference reference = spaceReference;
        while (reference != null) {
            if (reference.getType() == EntityType.SPACE) {
                references.add((SpaceReference) reference);
            }
            reference = reference.getParent();
        }
        // The first entry is the parent of the Document Reference
        return references;
    }

    /**
     * Transform the provided XDOM to update any wiki references stored in its content to point to published spaces.
     *
     * @param xdom the xdom to transform
     * @param originalReference the page containing this xdom
     * @param spaceReferencesMap a mapping between master spaces and published spaces
     * @param collectionReferencesMap a mapping between master collection reference and published collection reference
     * @return true if the xdom has been modified
     */
    public boolean transform(XDOM xdom, DocumentReference originalReference,
        Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        boolean hasXDOMChanged = false;

        // Handle the transformation of links
        hasXDOMChanged |= transformLinkBlocks(xdom, originalReference, spaceReferencesMap, collectionReferencesMap);

        // Handle the transformation of images
        hasXDOMChanged |= transformImageBlocks(xdom, originalReference, spaceReferencesMap, collectionReferencesMap);

        // We assume that transformation of macro content is already handled through calls in #transformXDOM.
        // Here we only care about updating the macro parameters which are declared as document references
        for (Block block : xdom.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT_OR_SELF)) {
            MacroBlock macroBlock = (MacroBlock) block;

            // Try to load the macro definition
            try {
                ComponentManager componentManager = findComponentManager();
                if (componentManager.hasComponent(Macro.class, macroBlock.getId())) {
                    Macro macro = componentManager.getInstance(Macro.class, macroBlock.getId());
                    Map<String, ParameterDescriptor> parameterDescriptors =
                        macro.getDescriptor().getParameterDescriptorMap();

                    hasXDOMChanged |= transformMacroBlock(macroBlock, parameterDescriptors, originalReference,
                        spaceReferencesMap, collectionReferencesMap);

                    if (macroBlock.getId().equals(DOCUMENTTREE_MACRO_ID)) {
                        // The Include Sibling macro is a special case
                        hasXDOMChanged |= transformDocumentTreeMacroBlock(macroBlock, DOCUMENTTREE_PARAM_ROOT,
                            parameterDescriptors, originalReference, spaceReferencesMap, collectionReferencesMap);
                    }
                }
            } catch (ComponentLookupException e) {
                // Should never happen
                logger.error("Failed to lookup macro definition for [{}]", macroBlock.getId(), e);
            }
        }

        return hasXDOMChanged;
    }

    private boolean transformLinkBlocks(XDOM xdom, DocumentReference originalReference,
        Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        boolean hasXDOMChanged = false;

        for (Block block : xdom.getBlocks(new ClassBlockMatcher(LinkBlock.class), Block.Axes.DESCENDANT_OR_SELF)) {
            LinkBlock linkBlock = (LinkBlock) block;
            ResourceType resourceType = linkBlock.getReference().getType();
            if (linkBlock.getReference().getReference().isEmpty()) {
                // Link to same page shouldn't be transformed
                continue;
            }
            if (SUPPORTED_DOCUMENT_RESOURCES.contains(resourceType)) {
                ResourceReference equivalentResourceReference = getEquivalentDocumentResourceReference(
                    linkBlock.getReference(), originalReference, spaceReferencesMap, collectionReferencesMap);

                if (equivalentResourceReference != null) {
                    equivalentResourceReference.setParameters(linkBlock.getReference().getParameters());
                    LinkBlock newLinkBlock = new LinkBlock(linkBlock.getChildren(), equivalentResourceReference,
                        linkBlock.isFreeStandingURI());
                    linkBlock.getParent().replaceChild(newLinkBlock, linkBlock);
                    hasXDOMChanged = true;
                }
            } else if (SUPPORTED_ATTACHMENT_RESOURCES.contains(resourceType)) {
                ResourceReference equivalentResourceReference = getEquivalentAttachmentResourceReference(
                    linkBlock.getReference(), originalReference, spaceReferencesMap, collectionReferencesMap);

                if (equivalentResourceReference != null) {
                    equivalentResourceReference.setParameters(linkBlock.getReference().getParameters());
                    LinkBlock newLinkBlock = new LinkBlock(linkBlock.getChildren(), equivalentResourceReference,
                        linkBlock.isFreeStandingURI());
                    linkBlock.getParent().replaceChild(newLinkBlock, linkBlock);
                    hasXDOMChanged = true;
                }
            }
        }

        return hasXDOMChanged;
    }

    private boolean transformImageBlocks(XDOM xdom, DocumentReference originalReference,
        Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        boolean hasXDOMChanged = false;

        for (Block block : xdom.getBlocks(new ClassBlockMatcher(ImageBlock.class), Block.Axes.DESCENDANT_OR_SELF)) {
            ImageBlock imageBlock = (ImageBlock) block;
            ResourceType resourceType = imageBlock.getReference().getType();

            if (SUPPORTED_ATTACHMENT_RESOURCES.contains(resourceType)) {
                ResourceReference equivalentResourceReference = getEquivalentAttachmentResourceReference(
                    imageBlock.getReference(), originalReference, spaceReferencesMap, collectionReferencesMap);

                if (equivalentResourceReference != null) {
                    ImageBlock newImageBlock = new ImageBlock(equivalentResourceReference,
                        imageBlock.isFreeStandingURI(), imageBlock.getParameters());
                    imageBlock.getParent().replaceChild(newImageBlock, imageBlock);
                    hasXDOMChanged = true;
                }
            }
        }

        return hasXDOMChanged;
    }

    private boolean transformMacroBlock(MacroBlock macroBlock, Map<String, ParameterDescriptor> parameterDescriptors,
        DocumentReference originalReference, Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        boolean hasXDOMChanged = false;

        for (Map.Entry<String, ParameterDescriptor> parameterDescriptorEntry : parameterDescriptors.entrySet()) {
            String parameter = macroBlock.getParameter(parameterDescriptorEntry.getKey());
            if (parameter == null || StringUtils.isBlank(parameter)) {
                continue;
            }

            String equivalentReference = null;
            if (DocumentReference.class.equals(parameterDescriptorEntry.getValue().getParameterType())
                || EntityReferenceString.class.equals(parameterDescriptorEntry.getValue().getDisplayType())) {
                equivalentReference = getEquivalentDocumentStringReference(parameter, originalReference,
                    spaceReferencesMap, collectionReferencesMap);
            } else if (AttachmentReference.class.equals(parameterDescriptorEntry.getValue().getParameterType())) {
                equivalentReference = getEquivalentAttachmentStringReference(parameter, originalReference,
                    spaceReferencesMap, collectionReferencesMap);
            }

            if (equivalentReference != null) {
                macroBlock.setParameter(parameterDescriptorEntry.getKey(), equivalentReference);
                hasXDOMChanged = true;
            }
        }

        return hasXDOMChanged;
    }

    private boolean transformDocumentTreeMacroBlock(MacroBlock macroBlock, String parameterName,
        Map<String, ParameterDescriptor> parameterDescriptors, DocumentReference originalReference,
        Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        boolean hasXDOMChanged = false;
        String root = macroBlock.getParameter(parameterName);
        if (root == null || root.isEmpty()) {
            return false;
        }

        EntityReference entityReference = convertToType(root, originalReference);
        DocumentReference reference = null;
        if (entityReference.getType().equals(EntityType.DOCUMENT)) {
            reference = new DocumentReference(entityReference);
        }

        if (entityReference.getType().equals(EntityType.SPACE)) {
            SpaceReference spaceReference = (SpaceReference) entityReference;
            reference = new DocumentReference(getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE, spaceReference);
        }

        if (reference != null) {
            DocumentReference equivalentReference =
                getEquivalentReference(reference, spaceReferencesMap, collectionReferencesMap);
            String equivalentReferenceSerialized = this.convertToString(equivalentReference);
            if (equivalentReferenceSerialized != null && !root.equals(equivalentReferenceSerialized)) {
                macroBlock.setParameter(parameterName, equivalentReferenceSerialized);
                hasXDOMChanged = true;
            }
        }

        return hasXDOMChanged;
    }

    private String getEquivalentDocumentStringReference(String stringReference, DocumentReference originalReference,
        Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        DocumentReference reference = new DocumentReference(
            currentEntityReferenceResolver.resolve(stringReference, EntityType.DOCUMENT, originalReference));
        DocumentReference equivalentReference =
            getEquivalentReference(reference, spaceReferencesMap, collectionReferencesMap);
        if (!reference.equals(equivalentReference)) {
            // Update the link with the new reference
            return entityReferenceSerializer.serialize(equivalentReference);
        } else {
            return null;
        }
    }

    private String getEquivalentAttachmentStringReference(String stringReference, DocumentReference originalReference,
        Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        AttachmentReference attachmentReference = new AttachmentReference(
            currentEntityReferenceResolver.resolve(stringReference, EntityType.ATTACHMENT, originalReference));
        DocumentReference reference = attachmentReference.getDocumentReference();
        DocumentReference equivalentReference =
            getEquivalentReference(reference, spaceReferencesMap, collectionReferencesMap);
        if (!reference.equals(equivalentReference)) {
            // Update the link with the new reference
            return entityReferenceSerializer
                .serialize(new AttachmentReference(attachmentReference.getName(), equivalentReference));
        } else {
            return null;
        }
    }

    private ResourceReference getEquivalentDocumentResourceReference(ResourceReference resourceReference,
        DocumentReference originalReference, Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        DocumentReference reference = convertDocumentResourceReference(resourceReference, originalReference);
        DocumentReference equivalentReference =
            getEquivalentReference(reference, spaceReferencesMap, collectionReferencesMap);
        if (!reference.equals(equivalentReference)) {
            // Update the link with the new reference
            return new ResourceReference(entityReferenceSerializer.serialize(equivalentReference),
                ResourceType.DOCUMENT);
        } else {
            return null;
        }
    }

    private ResourceReference getEquivalentAttachmentResourceReference(ResourceReference resourceReference,
        DocumentReference originalReference, Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        AttachmentReference reference = convertAttachmentResourceReference(resourceReference, originalReference);
        DocumentReference equivalentReference =
            getEquivalentReference(reference.getDocumentReference(), spaceReferencesMap, collectionReferencesMap);

        if (!reference.getDocumentReference().equals(equivalentReference)) {
            // Update the link with the new reference
            AttachmentReference equivalentAttachmentReference =
                new AttachmentReference(reference.getName(), equivalentReference);
            return new ResourceReference(entityReferenceSerializer.serialize(equivalentAttachmentReference),
                ResourceType.ATTACHMENT);
        } else {
            return null;
        }
    }

    private DocumentReference convertDocumentResourceReference(ResourceReference reference,
        DocumentReference originalReference)
    {
        if (ResourceType.DOCUMENT.equals(reference.getType())) {
            return new DocumentReference(currentEntityReferenceResolver.resolve(reference.getReference(),
                EntityType.DOCUMENT, originalReference));
        } else if (reference.getType().equals(ResourceType.PAGE)) {
            return new DocumentReference(
                currentEntityReferenceResolver.resolve(reference.getReference(), EntityType.PAGE, originalReference));
        } else {
            logger.error("Unsupported resource type for converting to a document reference : [{}]",
                reference.getType());
            return null;
        }
    }

    private AttachmentReference convertAttachmentResourceReference(ResourceReference reference,
        DocumentReference originalReference)
    {
        if (ResourceType.ATTACHMENT.equals(reference.getType())) {
            return new AttachmentReference(currentEntityReferenceResolver.resolve(reference.getReference(),
                EntityType.ATTACHMENT, originalReference));
        } else if (reference.getType().equals(ResourceType.PAGE_ATTACHMENT)) {
            return new AttachmentReference(currentEntityReferenceResolver.resolve(reference.getReference(),
                EntityType.PAGE_ATTACHMENT, originalReference));
        } else {
            logger.error("Unsupported resource type for converting to an attachment reference : [{}]",
                reference.getType());
            return null;
        }
    }

    /**
     * Get the equivalent reference of the published location.
     * 
     * @param reference the internal reference.
     * @param spaceReferencesMap the map of references.
     * @param collectionReferencesMap the collection reference.
     * @return the equivalent reference.
     */
    public DocumentReference getEquivalentReference(DocumentReference reference,
        Map<SpaceReference, SpaceReference> spaceReferencesMap,
        Map<SpaceReference, SpaceReference> collectionReferencesMap)
    {
        // If the page is versioned, we need to work with the root page that is the parent of this reference
        DocumentReference targetReference = getRootPageReference(reference);

        SpaceReference foundSpaceReference = getMatchingSpaceReference(targetReference, spaceReferencesMap);

        if (foundSpaceReference != null) {
            // The page will then be located within the newly published space. We need to re-compute its reference
            // chain.
            return targetReference.replaceParent(foundSpaceReference, spaceReferencesMap.get(foundSpaceReference));
        } else {
            // If the reference is in the same collection, replace the reference's parent with the publish location.
            foundSpaceReference = getMatchingSpaceReference(targetReference, collectionReferencesMap);

            // If no space reference is found, it means that we are in the case where the document reference actually
            // points to a page outside any book or library involved in this publication. We then don't need to
            // update it.
            return foundSpaceReference != null
                ? targetReference.replaceParent(foundSpaceReference, collectionReferencesMap.get(foundSpaceReference))
                : targetReference;
        }
    }

    private SpaceReference getMatchingSpaceReference(DocumentReference targetReference,
        Map<SpaceReference, SpaceReference> spaceReferencesMap)
    {
        if (spaceReferencesMap.isEmpty()) {
            return null;
        }

        SpaceReference spaceReference = null;
        for (EntityReference entityReference : targetReference.getReversedReferenceChain()) {
            if (EntityType.SPACE.equals(entityReference.getType()) && spaceReferencesMap.containsKey(entityReference)) {
                spaceReference = (SpaceReference) entityReference;
                break;
            }
        }

        return spaceReference;
    }

    private DocumentReference getRootPageReference(DocumentReference reference)
    {
        try {
            EntityReference parentEntityReference = reference.getParent();
            if (bookVersionsManagerProvider.get().isVersionedContent(reference) && parentEntityReference != null) {
                // Check if the parent is a document.
                EntityReference parentDocumentEntityReference =
                    parentEntityReference.extractReference(EntityType.DOCUMENT);

                if (parentDocumentEntityReference != null) {
                    // Build the root reference
                    return parentDocumentEntityReference instanceof DocumentReference
                        ? (DocumentReference) parentDocumentEntityReference
                        : new DocumentReference(parentDocumentEntityReference);

                } else {
                    // If not a document, check if it is a space.
                    parentDocumentEntityReference = parentEntityReference.extractReference(EntityType.SPACE);
                    SpaceReference parentSpaceReference = parentDocumentEntityReference instanceof SpaceReference
                        ? (SpaceReference) parentDocumentEntityReference
                        : new SpaceReference(parentDocumentEntityReference);
                    if (parentDocumentEntityReference != null) {
                        // Build the root reference
                        return new DocumentReference(getXWikiContext().getWiki().DEFAULT_SPACE_HOMEPAGE,
                            parentSpaceReference);
                    }
                }
            }

        } catch (XWikiException e) {
            // Should never happen
            logger.error("Failed to lookup root book page for [{}]", reference, e);
        }

        return reference;
    }

    protected EntityReference convertToType(Object value, DocumentReference originalReference)
    {
        String[] parts = StringUtils.split(String.valueOf(value), ":", 2);
        if (parts == null || parts.length != 2) {
            return null;
        }

        try {
            return this.currentEntityReferenceResolver.resolve(parts[1],
                EntityType.valueOf(camelCaseToUnderscore(parts[0]).toUpperCase()), originalReference);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected String convertToString(DocumentReference documentReference)
    {
        if (documentReference == null) {
            return null;
        }

        return underscoreToCamelCase(documentReference.getType().name().toLowerCase()) + ':'
            + this.entityReferenceSerializer.serialize(documentReference);
    }

    private String camelCaseToUnderscore(String nodeType)
    {
        return StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(nodeType), '_');
    }

    private String underscoreToCamelCase(String entityType)
    {
        StringBuilder result = new StringBuilder();
        for (String part : StringUtils.split(entityType, '_')) {
            result.append(StringUtils.capitalize(part));
        }
        return StringUtils.uncapitalize(result.toString());
    }

    private ComponentManager findComponentManager() throws ComponentLookupException
    {
        return this.rootComponentManager.getInstance(ComponentManager.class, "wiki");
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
