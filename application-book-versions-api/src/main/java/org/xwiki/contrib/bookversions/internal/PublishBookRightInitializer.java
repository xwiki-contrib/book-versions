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
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.WikiReadyEvent;
import org.xwiki.bridge.event.ApplicationReadyEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.bookversions.PublishBookRight;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.security.authorization.UnableToRegisterRightException;

/**
 * Initializer for registering book publication rights.
 *
 * We use an event listener coupled with an initializer because we want to address the case of
 *
 * @version $Id$
 * @since 0.1
 */
@Component
@Named(PublishBookRightInitializer.NAME)
@Singleton
public class PublishBookRightInitializer extends AbstractEventListener implements Initializable, Disposable
{
    /**
     * The listener name.
     */
    public static final String NAME = "publishBookRightInitializer";

    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    private Logger logger;

    /**
     * The default constructor.
     */
    public PublishBookRightInitializer()
    {
        super(NAME, new WikiReadyEvent(), new ApplicationReadyEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        registerPublishBookRight();
    }

    @Override
    public void initialize() throws InitializationException
    {
        registerPublishBookRight();
    }

    @Override
    public void dispose() throws ComponentLifecycleException
    {
        // Do not unregister the right, because it causes performance issues and it can bring the instance down.
        // Every Right has a value, and that value needs to be below 64 because of
        // the way we store right sets. And when you unregister a right that is not the last registered right, you break
        // that whole system. So, basically, unregistering of rights is only supported when you unregister the last
        // registered right. Plus, the values that are associated with rights need to be continuous.
    }

    private void registerPublishBookRight()
    {
        try {
            // Only registering the right if it wasn't registered before
            if (PublishBookRight.getRight().equals(Right.ILLEGAL)) {
                this.authorizationManager.register(PublishBookRight.INSTANCE);
            }
        } catch (UnableToRegisterRightException e) {
            logger.error("Error when trying to register the custom rights", e);
        }
    }
}
