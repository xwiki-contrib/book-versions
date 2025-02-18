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
package org.xwiki.contrib.bookversions.internal.batch;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.namespace.Namespace;
import org.xwiki.component.namespace.NamespaceUtils;
import org.xwiki.contrib.bookversions.internal.BookVersionsConstants;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.DefaultJobStatus;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataEntryStore;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.livedata.LiveDataSource;
import org.xwiki.livedata.LiveDataSourceManager;
import org.xwiki.model.ModelContext;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * The job dedicated to change the status of the pages.
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named(BookVersionsConstants.SET_PAGE_STATUS_JOBID)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class BatchChangeStatusJob extends AbstractJob<LiveDataBatchRequest, DefaultJobStatus<LiveDataBatchRequest>>
{
    /**
     * Name of the job type.
     */
    public static final String JOB_TYPE = "LiveDataBatchJob";

    @Inject
    private LiveDataSourceManager liveDataSourceManager;

    @Inject
    private DocumentReferenceResolver<String> referenceResolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private ModelContext modelContext;

    @Inject
    private LiveDataConfigurationResolver<LiveDataConfiguration> defaultLiveDataConfigResolver;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @Inject
    private AuthorizationManager authorizationManager;

    @Override
    public String getType()
    {
        return JOB_TYPE;
    }

    @Override
    protected void runInternal() throws Exception
    {
        XWikiContext xcontext = contextProvider.get();
        XWiki xwiki = xcontext.getWiki();
        try {
            List<String> pagesReference;
            LiveDataBatchRequest exportRequest = getRequest();

            Namespace namespaceObj = NamespaceUtils.toNamespace(exportRequest.getNamespace());
            WikiReference wikiReference = new WikiReference(namespaceObj.getValue());
            // Need to set namespace in modelContext to make the livedata to get the correct entries
            modelContext.setCurrentEntityReference(wikiReference);

            if (exportRequest.getPageReferences().isEmpty() && exportRequest.getConfiguration() != null
                && exportRequest.getConfiguration().getId() != null)
            {
                LiveDataConfiguration configuration =
                    defaultLiveDataConfigResolver.resolve(exportRequest.getConfiguration());
                LiveDataQuery query = configuration.getQuery();
                query.setLimit(-1);

                Optional<LiveDataSource> source =
                    this.liveDataSourceManager.get(query.getSource(), exportRequest.getNamespace());
                if (source.isEmpty()) {
                    throw new LiveDataException("Live Data source not found");
                }

                LiveDataEntryStore entryStore = source.get().getEntries();
                LiveData data = entryStore.get(query);
                pagesReference = data.getEntries().stream()
                    .map(x -> (String) x.get("doc.fullName"))
                    .collect(Collectors.toList());
            } else {
                pagesReference = exportRequest.getPageReferences();
            }

            progressManager.pushLevelProgress(pagesReference.size(), this);

            UserReference userReference = userReferenceResolver.resolve(exportRequest.getProperty("userReference"));

            for (String referenceStr : pagesReference) {
                progressManager.startStep(this, referenceStr);
                EntityReference pageRef = referenceResolver.resolve(referenceStr, wikiReference);
                logger.info("Change page status to [{}] for page {}",
                    exportRequest.getStatus(), pageRef);

                if (authorizationManager.hasAccess(Right.EDIT, exportRequest.getProperty("userReference"), pageRef)) {
                    XWikiDocument document = xwiki.getDocument(pageRef, xcontext).clone();
                    BaseObject statusObject = document.getXObject(BookVersionsConstants.PAGESTATUS_CLASS_REFERENCE);
                    statusObject.setStringValue(BookVersionsConstants.PAGESTATUS_PROP_STATUS,
                        exportRequest.getStatus());

                    document.getAuthors().setEffectiveMetadataAuthor(userReference);
                    document.getAuthors().setOriginalMetadataAuthor(userReference);
                    xwiki.saveDocument(document, "Batch change status", xcontext);
                } else {
                    logger.error("Can't change status, user [{}] is not allowed to edit the page {}",
                        exportRequest.getProperty("userReference"), pageRef);
                }

                progressManager.endStep(this);
            }
        } finally {
            this.progressManager.popLevelProgress(this);
        }
    }
}
