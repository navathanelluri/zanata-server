/*
 * Copyright 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.zanata.webtrans.client.editor.table;

import java.util.Collection;
import java.util.List;

import javax.inject.Provider;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import net.customware.gwt.presenter.client.EventBus;

import org.zanata.webtrans.client.events.CopyDataToEditorEvent;
import org.zanata.webtrans.client.events.CopyDataToEditorHandler;
import org.zanata.webtrans.client.events.InsertStringInEditorEvent;
import org.zanata.webtrans.client.events.InsertStringInEditorHandler;
import org.zanata.webtrans.client.events.NavTransUnitEvent;
import org.zanata.webtrans.client.events.NotificationEvent;
import org.zanata.webtrans.client.events.RequestValidationEvent;
import org.zanata.webtrans.client.events.RequestValidationEventHandler;
import org.zanata.webtrans.client.events.RunValidationEvent;
import org.zanata.webtrans.client.events.UpdateValidationWarningsEvent;
import org.zanata.webtrans.client.events.UpdateValidationWarningsEventHandler;
import org.zanata.webtrans.client.events.NotificationEvent.Severity;
import org.zanata.webtrans.client.presenter.SourceContentsPresenter;
import org.zanata.webtrans.client.resources.NavigationMessages;
import org.zanata.webtrans.client.resources.TableEditorMessages;
import org.zanata.webtrans.client.ui.ToggleEditor;
import org.zanata.webtrans.client.ui.ValidationMessagePanel;
import org.zanata.webtrans.shared.model.TransUnit;

import com.allen_sauer.gwt.log.client.Log;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class TargetContentsPresenter implements TargetContentsDisplay.Listener
{
   private final EventBus eventBus;
   private final TableEditorMessages messages;
   private final SourceContentsPresenter sourceContentsPresenter;
   private final ValidationMessagePanel validationMessagePanel;

   private TargetContentsDisplay currentDisplay;
   private Provider<TargetContentsDisplay> displayProvider;
   private List<TargetContentsDisplay> displayList;
   private ToggleEditor currentEditor;
   private List<ToggleEditor> currentEditors;

   @Inject
   public TargetContentsPresenter(Provider<TargetContentsDisplay> displayProvider, final EventBus eventBus, final TableEditorMessages messages, final SourceContentsPresenter sourceContentsPresenter)
   {
      this.displayProvider = displayProvider;
      this.eventBus = eventBus;
      this.messages = messages;
      this.sourceContentsPresenter = sourceContentsPresenter;

      validationMessagePanel = new ValidationMessagePanel(true, messages);

      eventBus.addHandler(UpdateValidationWarningsEvent.getType(), new UpdateValidationWarningsEventHandler()
      {
         @Override
         public void onUpdate(UpdateValidationWarningsEvent event)
         {
            validationMessagePanel.setContent(event.getErrors());
         }
      });

      eventBus.addHandler(RequestValidationEvent.getType(), new RequestValidationEventHandler()
      {
         @Override
         public void onRequestValidation(RequestValidationEvent event)
         {
            if (isEditing())
            {
               eventBus.fireEvent(new RunValidationEvent(sourceContentsPresenter.getSelectedSource(), currentEditor.getText(), false));
            }
         }
      });

      eventBus.addHandler(InsertStringInEditorEvent.getType(), new InsertStringInEditorHandler()
      {
         @Override
         public void onInsertString(InsertStringInEditorEvent event)
         {
            if (isEditing())
            {
               currentEditor.insertTextInCursorPosition(event.getSuggestion());
               eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifyCopied()));
            }
            else
            {
               eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyUnopened()));
            }
         }
      });

      eventBus.addHandler(CopyDataToEditorEvent.getType(), new CopyDataToEditorHandler()
      {
         @Override
         public void onTransMemoryCopy(CopyDataToEditorEvent event)
         {
            if (isEditing())
            {
               currentEditor.setText(event.getTargetResult());
               eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifyCopied()));
            }
            else
            {
               eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyUnopened()));
            }
         }
      });
   }

   boolean isEditing()
   {
      return currentDisplay != null && currentDisplay.isEditing();
   }

   public void setToViewMode()
   {
      if (currentDisplay != null)
      {
         currentDisplay.setToView();
      }
   }

   public void setCurrentEditorText(String text)
   {
      if (currentEditor != null)
      {
         currentEditor.setText(text);
      }
   }

   public void showEditors(int rowIndex)
   {
      Log.info("show editors at row:" + rowIndex);
      currentDisplay = displayList.get(rowIndex);
      currentEditors = currentDisplay.getEditors();
      currentEditor = getOpenEditorOrFirstOneIfNoneIsOpen();
      currentEditor.setViewMode(ToggleEditor.ViewMode.EDIT);
   }

   private ToggleEditor getOpenEditorOrFirstOneIfNoneIsOpen()
   {
      Collection<ToggleEditor> openingEditors = findOpeningEditors();
      if (openingEditors.isEmpty())
      {
         // if no editor is open, return the first one
         return currentEditors.get(0);
      }
      else
      {
         return openingEditors.iterator().next();
      }
   }

   private Collection<ToggleEditor> findOpeningEditors()
   {
      Collection<ToggleEditor> openingEditor = Collections2.filter(currentEditors, new Predicate<ToggleEditor>()
      {
         @Override
         public boolean apply(ToggleEditor editor)
         {
            return editor.getViewMode() == ToggleEditor.ViewMode.EDIT;
         }
      });
      Preconditions.checkState(openingEditor.size() <= 1, "more than one editor is opened!");
      return openingEditor;
   }

   public TargetContentsDisplay getNextTargetContentsDisplay(int rowIndex, TransUnit transUnit)
   {
      TargetContentsDisplay result = displayList.get(rowIndex);
      result.setTargets(transUnit.getTargets());
      currentDisplay = result;
      return result;
   }

   public void initWidgets(int pageSize)
   {
      displayList = Lists.newArrayList();
      for (int i = 0; i < pageSize; i++)
      {
         TargetContentsDisplay display = displayProvider.get();
         display.setListener(this);
         displayList.add(display);
      }
   }

   public TargetContentsDisplay getCurrentDisplay()
   {
      return currentDisplay;
   }

   @Override
   public void validate(ToggleEditor editor)
   {
      eventBus.fireEvent(new RunValidationEvent(sourceContentsPresenter.getSelectedSource(), editor.getText(), false));
   }

   @Override
   public void saveAsApproved(ToggleEditor editor)
   {
      // TODO we should get new value out and save
      int editorIndex = currentEditors.indexOf(editor);
      if (editorIndex + 1 < currentEditors.size())
      {
         currentDisplay.setToView();
         currentEditor = currentEditors.get(editorIndex + 1);
         currentEditor.setViewMode(ToggleEditor.ViewMode.EDIT);
      }
      else
      {
         eventBus.fireEvent(new NavTransUnitEvent(NavTransUnitEvent.NavigationType.NextEntry));
      }
   }

   @Override
   public void copySource(ToggleEditor editor)
   {
      editor.setText(sourceContentsPresenter.getSelectedSource());
      editor.autoSize();
      eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifyCopied()));
   }

   @Override
   public void toggleView(ToggleEditor editor)
   {
      currentDisplay.setToView();
      editor.setViewMode(ToggleEditor.ViewMode.EDIT);
      currentEditor = editor;
   }

   public List<String> getNewTargets()
   {
      return currentDisplay.getNewTargets();
   }

   @Override
   public void setValidationMessagePanel(ToggleEditor editor)
   {
      validationMessagePanel.clear();
      editor.addValidationMessagePanel(validationMessagePanel);
   }
}