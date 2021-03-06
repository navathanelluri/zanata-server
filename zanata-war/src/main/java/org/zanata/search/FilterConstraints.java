/*
 * Copyright 2012, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.zanata.search;

//TODO May want to add document(someDocument) to these constraints
//so that only one search method is needed on the interface.

import org.zanata.webtrans.shared.model.ContentStateGroup;

import lombok.Getter;

import com.google.common.base.Objects;

/**
 * Specifies a set of constraints to be applied by a filter.
 * 
 * @author David Mason, damason@redhat.com
 */
@Getter
public class FilterConstraints
{
   private String searchString;
   private boolean isCaseSensitive;
   private boolean searchInSource;
   private boolean searchInTarget;
   private ContentStateGroup includedStates;

   private FilterConstraints(String searchString, boolean caseSensitive,
                             boolean searchInSource, boolean searchInTarget,
                             ContentStateGroup includedStates)
   {
      this.searchString = searchString;
      this.isCaseSensitive = caseSensitive;
      this.searchInSource = searchInSource;
      this.searchInTarget = searchInTarget;
      this.includedStates = includedStates;
   }

   public static Builder builder()
   {
      return new Builder();
   }

   @Override
   public String toString()
   {
      // @formatter:off
      return Objects.toStringHelper(this).
            add("searchString", searchString).
            add("isCaseSensitive", isCaseSensitive).
            add("searchInSource", searchInSource).
            add("searchInTarget", searchInTarget).
            add("includedStates", includedStates).
            toString();
      // @formatter:on
   }

   public static class Builder
   {
      private String searchString;
      private boolean caseSensitive;
      private boolean searchInSource;
      private boolean searchInTarget;
      private ContentStateGroup.Builder states;

      public Builder()
      {
         states = ContentStateGroup.builder();
         setKeepAll();
      }

      public FilterConstraints build()
      {
         return new FilterConstraints(searchString, caseSensitive,
               searchInSource, searchInTarget, states.build());
      }

      public Builder keepAll()
      {
         setKeepAll();
         return this;
      }

      private void setKeepAll()
      {
         searchString = "";
         caseSensitive = false;
         searchInSource = true;
         searchInTarget = true;
         states.addAll();
      }

      public Builder keepNone()
      {
         searchString = "";
         caseSensitive = false;
         searchInSource = false;
         searchInTarget = false;
         states.removeAll();
         return this;
      }

      public Builder filterBy(String searchString)
      {
         this.searchString = searchString;
         return this;
      }

      public Builder caseSensitive(boolean caseSensitive)
      {
         this.caseSensitive = caseSensitive;
         return this;
      }

      public Builder checkInSource(boolean check)
      {
         searchInSource = check;
         return this;
      }

      public Builder checkInTarget(boolean check)
      {
         searchInTarget = check;
         return this;
      }

      public Builder includeStates(ContentStateGroup states)
      {
         this.states.fromStates(states);
         return this;
      }

      public Builder includeNew()
      {
         states.includeNew(true);
         return this;
      }

      public Builder excludeNew()
      {
         states.includeNew(false);
         return this;
      }

      public Builder includeFuzzy()
      {
         states.includeFuzzy(true);
         return this;
      }

      public Builder excludeFuzzy()
      {
         states.includeFuzzy(false);
         return this;
      }

      public Builder includeTranslated()
      {
         states.includeTranslated(true);
         return this;
      }

      public Builder excludeTranslated()
      {
         states.includeTranslated(false);
         return this;
      }

      public Builder includeApproved()
      {
         states.includeApproved(true);
         return this;
      }

      public Builder excludeApproved()
      {
         states.includeApproved(false);
         return this;
      }

      public Builder includeRejected()
      {
         states.includeRejected(true);
         return this;
      }

      public Builder excludeRejected()
      {
         states.includeRejected(false);
         return this;
      }

   }

}
