package org.zanata.webtrans.client.ui;

import com.google.gwt.user.client.ui.Focusable;
import net.customware.gwt.presenter.client.EventBus;

import org.zanata.webtrans.client.editor.table.EditorTextArea;
import org.zanata.webtrans.client.editor.table.TableResources;
import org.zanata.webtrans.client.editor.table.ToggleWidget;
import org.zanata.webtrans.client.resources.NavigationMessages;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.Widget;

public class Editor extends Composite implements ToggleWidget {
    interface EditorUiBinder extends UiBinder<Widget, Editor> {
    }
    private static EditorUiBinder uiBinder = GWT.create(EditorUiBinder.class);
    private static final int INITIAL_LINES = 3;

    private static final int HEIGHT_PER_LINE = 16;

    @UiField
    FlowPanel topContainer;

    @UiField
    HorizontalPanel buttons;

    @UiField
    PushButton validateButton, saveButton, fuzzyButton, cancelButton;

    @UiField
    TableResources images;

    @UiField
    NavigationMessages messages;
    private EditorTextArea textArea;


    private HighlightingLabel label;
//   TableResources images = GWT.create(TableResources.class);

    public Editor(String displayString, String findMessage) {
        initWidget(uiBinder.createAndBindUi(this));

        validateButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                if (getContent() != null && !getContent().isEmpty()) {
                    // fireValidationEvent(eventBus);
                }
            }
        });

        saveButton.addClickHandler(acceptHandler);

        fuzzyButton.addClickHandler(fuzzyHandler);

        cancelButton.addClickHandler(cancelHandler);

        label = new HighlightingLabel(displayString);
        if (displayString == null || displayString.isEmpty()) {
            label.setText(messages.clickHere());
            label.setStylePrimaryName("TableEditorContent-Empty");
        } else {
            label.setText(displayString);
            label.setStylePrimaryName("TableEditorContent");
        }

        if (findMessage != null && !findMessage.isEmpty()) {
            label.highlightSearch(findMessage);
        }

        label.setTitle(messages.clickHere());

        textArea = new EditorTextArea();
        textArea.setStyleName("TableEditorContent-Edit");
        textArea.setVisible(false);

        textArea.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> event) {
                autoSize();
                // fireValidationEvent(eventBus);
            }

        });

        topContainer.add(label);
        topContainer.add(textArea);

        this.addHandler(new MouseDownHandler() {
            @Override
            public void onMouseDown(MouseDownEvent event) {
                toggleView();

            }
        }, MouseDownEvent.getType());

        sinkEvents(Event.ONCLICK);

    }

    @Override
    public ViewMode getViewMode() {
        if (label.isVisible()) {
            return ViewMode.VIEW;
        } else {
            return ViewMode.EDIT;
        }
    }

    @Override
    public void setViewMode(ViewMode viewMode) {
        label.setVisible(viewMode == ViewMode.VIEW);
        textArea.setVisible(viewMode == ViewMode.EDIT);
        buttons.setVisible(viewMode == ViewMode.EDIT);
    }

    @Override
    public void setText(String text) {
        label.setText(text);
        textArea.setText(text);
    }

    @Override
    public String getText() {
        return textArea.getText();
    }

    /**
     * The click listener used to save as fuzzy.
     */
    private ClickHandler fuzzyHandler = new ClickHandler() {
        public void onClick(ClickEvent event) {
            // acceptFuzzyEdit();
        }
    };

    /**
     * The click listener used to cancel.
     */
    private ClickHandler cancelHandler = new ClickHandler() {
        public void onClick(ClickEvent event) {
            // cancelEdit();
        }
    };

    /**
     * The click listener used to accept.
     */
    private ClickHandler acceptHandler = new ClickHandler() {
        public void onClick(ClickEvent event) {
            // saveApprovedAndMoveRow(NavigationType.NextEntry);
        }
    };

    private void toggleView() {
        if (textArea.isVisible()) {
            textArea.setVisible(false);
            label.setVisible(true);
        } else {
            textArea.setVisible(true);
            label.setVisible(false);
        }
    }

    private void fireValidationEvent(final EventBus eventBus) {
        // eventBus.fireEvent(new RunValidationEvent(cellValue.getId(),
        // cellValue.getSource(), textArea.getText(), false));
    }

    private void autoSize() {
        shrinkSize(true);
        growSize();
    }

    /**
     * forceShrink will resize the textArea to initialLines(3 lines) and growSize
     * according to the scroll height
     *
     * @param forceShrink
     */
    private void shrinkSize(boolean forceShrink) {
        if (forceShrink) {
            textArea.setVisibleLines(INITIAL_LINES);
        } else {
            if (textArea.getElement().getScrollHeight() <= (INITIAL_LINES * HEIGHT_PER_LINE)) {
                textArea.setVisibleLines(INITIAL_LINES);
            } else {
                if (textArea.getElement().getScrollHeight() >= textArea.getElement().getClientHeight()) {
                    int newHeight = textArea.getElement().getScrollHeight() - textArea.getElement().getClientHeight() > 0 ? textArea.getElement().getScrollHeight() - textArea.getElement().getClientHeight() : HEIGHT_PER_LINE;
                    int newLine = (newHeight / HEIGHT_PER_LINE) - 1 > INITIAL_LINES ? (newHeight / HEIGHT_PER_LINE) - 1 : INITIAL_LINES;
                    textArea.setVisibleLines(textArea.getVisibleLines() - newLine);
                }
                growSize();
            }
        }
    }

    private String getContent() {
        return textArea.getText();
    }

    private void growSize() {
        if (textArea.getElement().getScrollHeight() > textArea.getElement().getClientHeight()) {
            int newHeight = textArea.getElement().getScrollHeight() - textArea.getElement().getClientHeight();
            int newLine = (newHeight / HEIGHT_PER_LINE) + 1;
            textArea.setVisibleLines(textArea.getVisibleLines() + newLine);
        }
    }
}