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

package org.xwiki.contrib.bookversions.internal.displayers;

import java.lang.reflect.Type;

import javax.inject.Singleton;
import org.xwiki.component.annotation.Component;
import org.xwiki.properties.converter.AbstractConverter;

/**
 * XWiki Properties Bean Converter to convert Strings into {@link PageTranslationLanguage}.
 *
 * @version $Id$
 * @see org.xwiki.properties.converter.Converter
 */
@Component
@Singleton
public class PageTranslationLanguageConverter extends AbstractConverter<PageTranslationLanguage>
{
    @SuppressWarnings("unchecked")
    @Override
    protected PageTranslationLanguage convertToType(Type targetType, Object value)
    {
        if (value != null) {
            return new PageTranslationLanguage(value.toString());
        } else {
            return null;
        }
    }

    @Override
    protected String convertToString(PageTranslationLanguage value)
    {
        return value.toString();
    }
}
