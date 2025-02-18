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

import org.xwiki.job.AbstractRequest;
import org.xwiki.livedata.LiveDataConfiguration;

/**
 * Used to pass all required parameters to the job for the batch status changes.
 *
 * @version $Id$
 * @since 1.1
 */
public class LiveDataBatchRequest extends AbstractRequest
{
    private static final String PROPERTY_NAMESPACE = "namespace";

    private static final String PROPERTY_CONFIGURATION = "configuration";

    private static final String PROPERTY_PAGES_REFERENCES = "pagesReferences";

    private static final String PROPERTY_STATUS = "status";

    /**
     * Create a new batch request.
     *
     * @param namespace the namespace of the livedata.
     * @param configuration the livedata configuration.
     * @param pageReferences the list of pages references if user selected some specific pages.
     * @param status the new status to set.
     */
    public LiveDataBatchRequest(String namespace, LiveDataConfiguration configuration, List<String> pageReferences,
        String status)
    {
        setNamespace(namespace);
        setConfiguration(configuration);
        setPageReferences(pageReferences);
        setStatus(status);
    }

    /**
     * @param query see {@link #getConfiguration()}
     */
    public void setConfiguration(LiveDataConfiguration query)
    {
        setProperty(PROPERTY_CONFIGURATION, query);
    }

    /**
     * @return the Live Data configuration
     */
    public LiveDataConfiguration getConfiguration()
    {
        return getProperty(PROPERTY_CONFIGURATION);
    }

    /**
     * @param namespace see {@link #getNamespace()}
     */
    public void setNamespace(String namespace)
    {
        setProperty(PROPERTY_NAMESPACE, namespace);
    }

    /**
     * @return the namespace where the source shall be found
     */
    public String getNamespace()
    {
        return getProperty(PROPERTY_NAMESPACE);
    }

    /**
     * @param pageReferences list of pages references.
     */
    public void setPageReferences(List<String> pageReferences)
    {
        setProperty(PROPERTY_PAGES_REFERENCES, pageReferences);
    }

    /**
     * @return list of pages references.
     */
    public List<String> getPageReferences()
    {
        return getProperty(PROPERTY_PAGES_REFERENCES);
    }

    /**
     * @param status the new page status.
     */
    public void setStatus(String status)
    {
        setProperty(PROPERTY_STATUS, status);
    }

    /**
     * @return the new page status.
     */
    public String getStatus()
    {
        return getProperty(PROPERTY_STATUS);
    }
}
