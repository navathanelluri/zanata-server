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
package org.zanata.webtrans.client.presenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.customware.gwt.presenter.client.EventBus;
import net.customware.gwt.presenter.client.widget.WidgetPresenter;

import org.zanata.webtrans.client.events.AttentionModeActivationEvent;
import org.zanata.webtrans.client.events.KeyShortcutEvent;
import org.zanata.webtrans.client.events.KeyShortcutEventHandler;
import org.zanata.webtrans.client.keys.EventWrapper;
import org.zanata.webtrans.client.keys.KeyShortcut;
import org.zanata.webtrans.client.keys.KeyShortcut.KeyEvent;
import org.zanata.webtrans.client.keys.KeyShortcutManager;
import org.zanata.webtrans.client.keys.Keys;
import org.zanata.webtrans.client.keys.ShortcutContext;
import org.zanata.webtrans.client.keys.SurplusKeyListener;
import org.zanata.webtrans.client.keys.TimedAction;
import org.zanata.webtrans.client.keys.Timer;
import org.zanata.webtrans.client.keys.TimerFactory;
import org.zanata.webtrans.client.resources.WebTransMessages;
import org.zanata.webtrans.client.view.KeyShortcutDisplay;

import com.allen_sauer.gwt.log.client.Log;
import com.google.common.collect.Iterables;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.view.client.ListDataProvider;
import com.google.inject.Inject;

/**
 * Detects shortcut key combinations such as Alt+KEY and Shift+Alt+KEY and
 * broadcasts corresponding {@link KeyShortcutEvent}s.
 * 
 * Handlers are registered directly with this presenter to avoid excessive
 * traffic on the main event bus and to make handling of events simpler.
 * 
 * @author David Mason, <a
 *         href="mailto:damason@redhat.com">damason@redhat.com</a> *
 */
public class KeyShortcutPresenter extends WidgetPresenter<KeyShortcutDisplay>
{
   private static final int ATTENTION_MODE_DURATION = 5000;

   private final KeyShortcutManager shortcutManager;
   private final KeyShortcutManager attentionKeyManager;
   private boolean isAttentionMode;
   private Timer attentionTimer;

   private Map<ShortcutContext, Set<SurplusKeyListener>> surplusKeyMap;

   private Set<ShortcutContext> activeContexts;

   private WebTransMessages messages;

   private final EventWrapper event;
   private final TimerFactory timers;

   // TODO unregister when user changes attention shortcut
   private HandlerRegistration attentionShortcutHandle;

   @Inject
   public KeyShortcutPresenter(KeyShortcutDisplay display,
                               EventBus eventBus,
                               final WebTransMessages webTransMessages,
                               final EventWrapper event,
                               final TimerFactory timer)
   {
      super(display, eventBus);
      this.messages = webTransMessages;
      this.event = event;
      this.timers = timer;

      this.shortcutManager = new KeyShortcutManager(event)
      {
         @Override
         public Set<Keys> getKeys(KeyShortcut shortcut)
         {
            return shortcut.getAllKeys();
         }

         @Override
         protected boolean handleIfMatchingShortcut(NativeEvent evt, KeyShortcut shortcut, KeyShortcutEvent shortcutEvent)
         {
            boolean contextActive = ensureActiveContexts().contains(shortcut.getContext());
            if (contextActive)
            {
               triggerShortcutEvent(evt, shortcut, shortcutEvent);
               return true;
            }
            return false;
         }

         @Override
         protected void handleNonMatchedShortcut(NativeEvent evt, KeyShortcutEvent shortcutEvent)
         {
            fireActiveSurplusKeyListeners(evt, shortcutEvent);
         }
      };

      isAttentionMode = false;

      this.attentionKeyManager = new KeyShortcutManager(event)
      {
         @Override
         public Set<Keys> getKeys(KeyShortcut shortcut)
         {
            return shortcut.getAllAttentionKeys();
         }

         @Override
         protected boolean handleIfMatchingShortcut(NativeEvent evt, KeyShortcut shortcut, KeyShortcutEvent shortcutEvent)
         {
            boolean contextActive = ensureActiveContexts().contains(shortcut.getContext());
            if (contextActive)
            {
               triggerShortcutEvent(evt, shortcut, shortcutEvent);
               setAttentionMode(false);
               return true;
            }
            return false;
         }

         @Override
         protected void handleNonMatchedShortcut(NativeEvent evt, KeyShortcutEvent shortcutEvent)
         {
            if (KeyEvent.KEY_DOWN.nativeEventType.equals(event.getType(evt)))
            {
               // attention mode should always eat the first key press,
               evt.stopPropagation();
               evt.preventDefault();

               Log.warn("Unrecognized attention key input");
               setAttentionMode(false);
            }
         }
      };
   }

   @Override
   protected void onBind()
   {
      ensureActiveContexts().add(ShortcutContext.Application);

      event.addNativePreviewHandler(new NativePreviewHandler()
      {

         @Override
         public void onPreviewNativeEvent(NativePreviewEvent nativeEvent)
         {
            NativeEvent evt = nativeEvent.getNativeEvent();

            if ((event.getTypeInt(nativeEvent) & (event.keyDownEvent() | event.keyUpEvent())) != 0)
            {
               if (isAttentionMode)
               {
                  attentionKeyManager.processKeyEvent(evt);
               }
               else
               {
                  shortcutManager.processKeyEvent(evt);
               }
            }
         }
      });

      // @formatter:off
      KeyShortcut hideShortcutSummaryShortcut = KeyShortcut.Builder.builder()
            .addKey(new Keys(Keys.NO_MODIFIER, KeyCodes.KEY_ESCAPE))
            .setContext(ShortcutContext.Application)
            .setDescription(messages.closeShortcutView())
            .setKeyEvent(KeyEvent.KEY_UP)
            .setPreventDefault(true)
            .setStopPropagation(true)
            .setHandler(new KeyShortcutEventHandler()
            {
               @Override
               public void onKeyShortcut(KeyShortcutEvent event)
               {
                  if (display.isShowing())
                  {
                     display.hide(true);
                  }
               }
            }).build();
      register(hideShortcutSummaryShortcut);

      // could try to use ?, although this is not as simple as passing character
      // '?'
      KeyShortcut showShortcutSummaryShortcut = KeyShortcut.Builder.builder()
            .addKey(new Keys(Keys.ALT_KEY, 'Y'))
            .setContext(ShortcutContext.Application)
            .setDescription(messages.showAvailableKeyShortcuts())
            .setHandler(new KeyShortcutEventHandler()
      {
         @Override
         public void onKeyShortcut(KeyShortcutEvent event)
         {
            showShortcuts();
         }
      }).build();
      register(showShortcutSummaryShortcut);

      // TODO use configured value (just use a different Keys object)
      KeyShortcut attentionKeyShortcut = KeyShortcut.Builder.builder()
            .setDescription("Activate attention mode.")
            .addKey(new Keys(Keys.ALT_KEY, 'X'))
            .setContext(ShortcutContext.Application)
            .setKeyEvent(KeyEvent.KEY_DOWN)
            .setHandler(new KeyShortcutEventHandler()
      {
         @Override
         public void onKeyShortcut(KeyShortcutEvent event)
         {
            setAttentionMode(true);
         }
      }).build();
      attentionShortcutHandle = register(attentionKeyShortcut);

      KeyShortcut cancelAttentionShortcut = KeyShortcut.Builder.builder()
            .setDescription("Deactivate attention mode.")
            .addAttentionKey(new Keys(KeyCodes.KEY_ESCAPE))
            .setContext(ShortcutContext.Application)
            .setKeyEvent(KeyEvent.KEY_DOWN)
            .setHandler(new KeyShortcutEventHandler()
         {
            @Override
            public void onKeyShortcut(KeyShortcutEvent event)
            {
               setAttentionMode(false);
            }
         }).build();
      register(cancelAttentionShortcut);

      // @formatter:on

      Log.info("creating attention timer");
      attentionTimer = timers.create(new TimedAction() {
         @Override
         public void run()
         {
            setAttentionMode(false);
         }
      });
   }

   @Override
   protected void onUnbind()
   {
   }

   @Override
   protected void onRevealDisplay()
   {
   }

   private void setAttentionMode(boolean active)
   {
      if (active)
      {
         // clobbers existing countdown
         attentionTimer.schedule(ATTENTION_MODE_DURATION);
      }
      else
      {
         attentionTimer.cancel();
      }

      if (isAttentionMode != active)
      {
         isAttentionMode = active;
         AttentionModeActivationEvent attentionEvent = new AttentionModeActivationEvent(active);
//         attentionEvent.setShortcuts(listAttentionShortcuts());
         eventBus.fireEvent(attentionEvent);
      }
   }

   private List<KeyShortcut> listAttentionShortcuts()
   {
      ArrayList<KeyShortcut> shortcuts = new ArrayList<KeyShortcut>();
      for (Keys key : attentionKeyManager.ensureShortcutMap().keySet())
      {
         shortcuts.addAll(attentionKeyManager.ensureShortcutMap().get(key));
      }
      return shortcuts;
   }

   public void setContextActive(ShortcutContext context, boolean active)
   {
      if (active)
      {
         ensureActiveContexts().add(context);
      }
      else
      {
         if (context == ShortcutContext.Application)
         {
            // TODO throw exception? Remove this check? Just warn but still
            // remove context?
            Log.warn("Tried to set global shortcut context inactive. Ignoring.");
         }
         else
         {
            ensureActiveContexts().remove(context);
         }
      }
   }

   /**
    * Register a {@link KeyShortcut} to respond to a specific key combination
    * for a context.
    * 
    * @param shortcut to register
    * 
    * @return a {@link HandlerRegistration} that can be used to un-register the
    *         shortcut
    */
   public HandlerRegistration register(KeyShortcut shortcut)
   {
      Log.debug("Registering key shortcut. Key codes follow:" + Iterables.toString(shortcut.getAllKeys()));
      shortcutManager.add(shortcut);
      attentionKeyManager.add(shortcut);
      return new KeyShortcutHandlerRegistration(shortcut);
   }

   /**
    * Register a {@link SurplusKeyListener} to catch all non-shortcut key events
    * for a context.
    * 
    * @param listener surplus key listener
    * @return a {@link HandlerRegistration} that can be used to un-register the
    *         listener
    */
   public HandlerRegistration register(SurplusKeyListener listener)
   {
      Set<SurplusKeyListener> listeners = ensureSurplusKeyListenerMap().get(listener.getContext());
      if (listeners == null)
      {
         listeners = new HashSet<SurplusKeyListener>();
         ensureSurplusKeyListenerMap().put(listener.getContext(), listeners);
      }
      listeners.add(listener);
      return new SurplusKeyListenerHandlerRegistration(listener);
   }

   private void fireActiveSurplusKeyListeners(NativeEvent evt, KeyShortcutEvent shortcutEvent)
   {
      for (ShortcutContext context : ensureActiveContexts())
      {
         Set<SurplusKeyListener> listeners = ensureSurplusKeyListenerMap().get(context);
         if (listeners != null && !listeners.isEmpty())
         {
            for (SurplusKeyListener listener : listeners)
            {
               if (listener.getKeyEvent().nativeEventType.equals(evt.getType()))
               {
                  // could add interface with these methods to reduce
                  // duplication
                  if (listener.isStopPropagation())
                  {
                     evt.stopPropagation();
                  }
                  if (listener.isPreventDefault())
                  {
                     evt.preventDefault();
                  }
                  listener.getHandler().onKeyShortcut(shortcutEvent);
               }
            }
         }
      }
   }

   private String getContextName(ShortcutContext context)
   {
      String contextName = "";
      switch (context)
      {
      case Application:
         contextName = messages.applicationScope();
         break;
      case ProjectWideSearch:
         contextName = messages.projectWideSearchAndReplace();
         break;
      case Edit:
         contextName = messages.editScope();
         break;
      case Navigation:
         contextName = messages.navigationScope();
         break;
      case TM:
         contextName = messages.tmScope();
         break;
      case Glossary:
         contextName = messages.glossaryScope();
         break;
      case Chat:
         contextName = messages.chatScope();
         break;
      }
      return contextName;
   }

   private Set<ShortcutContext> ensureActiveContexts()
   {
      if (activeContexts == null)
      {
         activeContexts = new HashSet<ShortcutContext>();
      }
      return activeContexts;
   }

   private Map<ShortcutContext, Set<SurplusKeyListener>> ensureSurplusKeyListenerMap()
   {
      if (surplusKeyMap == null)
      {
         surplusKeyMap = new HashMap<ShortcutContext, Set<SurplusKeyListener>>();
      }
      return surplusKeyMap;
   }

   // TODO update to show attention key + selector for attention key + attention keys for shortcuts
   public void showShortcuts()
   {
      display.clearPanel();
      for (ShortcutContext context : ensureActiveContexts())
      {
         ListDataProvider<KeyShortcut> dataProvider = display.addContext(getContextName(context));

         for (Set<KeyShortcut> shortcutSet : shortcutManager.ensureShortcutMap().values())
         {
            for (KeyShortcut shortcut : shortcutSet)
            {
               if (shortcut.getContext() == context && shortcut.isDisplayInView() && !dataProvider.getList().contains(shortcut))
               {
                  dataProvider.getList().add(shortcut);
               }
            }
         }
         Collections.sort(dataProvider.getList());
      }
      display.showPanel();
   }

   private class KeyShortcutHandlerRegistration implements HandlerRegistration
   {

      private KeyShortcut shortcut;

      public KeyShortcutHandlerRegistration(KeyShortcut shortcut)
      {
         this.shortcut = shortcut;
      }

      @Override
      public void removeHandler()
      {
         shortcutManager.remove(shortcut);
         attentionKeyManager.remove(shortcut);
      }

   }

   private class SurplusKeyListenerHandlerRegistration implements HandlerRegistration
   {

      private SurplusKeyListener listener;

      public SurplusKeyListenerHandlerRegistration(SurplusKeyListener listener)
      {
         this.listener = listener;
      }

      @Override
      public void removeHandler()
      {
         Set<SurplusKeyListener> listeners = ensureSurplusKeyListenerMap().get(listener.getContext());
         if (listeners != null)
         {
            listeners.remove(listener);
         }
      }

   }
}
