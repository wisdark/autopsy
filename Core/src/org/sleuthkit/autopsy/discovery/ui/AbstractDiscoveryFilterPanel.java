/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.discovery.ui;

import org.sleuthkit.autopsy.discovery.search.AbstractFilter;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.event.ListSelectionListener;

/**
 * Abstract class extending JPanel for filter controls.
 */
abstract class AbstractDiscoveryFilterPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Setup the file filter settings.
     *
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged or that there are no items to select.
     */
    abstract void configurePanel(boolean selected, int[] indicesSelected);

    /**
     * Get the checkbox which enables and disables this filter.
     *
     * @return The JCheckBox which enables and disables this filter.
     */
    abstract JCheckBox getCheckbox();

    /**
     * Get the list of values associated with this filter if one exists. If one
     * does not exist this should return null.
     *
     * @return The JList which contains the values available for selection for
     *         this filter.
     */
    abstract JList<?> getList();

    /**
     * Get any additional text that should be displayed under the checkbox. If
     * no text should be displayed this should return null.
     *
     * @return The JLabel to display under the JCheckBox.
     */
    abstract JLabel getAdditionalLabel();

    /**
     * Check if this filter is configured to valid settings.
     *
     * @return If the settings are invalid returns the error that has occurred,
     *         otherwise returns empty string.
     */
    abstract String checkForError();

    /**
     * Add listeners to the checkbox/list set if listeners have not already been
     * added.
     *
     * @param actionlistener The listener for the checkbox selection events.
     * @param listListener   The listener for the list selection events.
     */
    void addListeners(ActionListener actionListener, ListSelectionListener listListener) {
        if (getCheckbox() != null) {
            getCheckbox().addActionListener(actionListener);
        }
        if (getList() != null) {
            getList().addListSelectionListener(listListener);
        }
    }

    /**
     * Get the AbstractFilter which is represented by this Panel.
     *
     * @return The AbstractFilter for the selected settings, null if the
     *         settings are not in use.
     */
    abstract AbstractFilter getFilter();

    /**
     * Remove listeners from the checkbox and the list if they exist.
     */
    void removeListeners() {
        if (getCheckbox() != null) {
            for (ActionListener listener : getCheckbox().getActionListeners()) {
                getCheckbox().removeActionListener(listener);
            }
        }
        if (getList() != null) {
            for (ListSelectionListener listener : getList().getListSelectionListeners()) {
                getList().removeListSelectionListener(listener);
            }
        }
    }

    /**
     * Return whether or not this filter has a panel.
     *
     * @return True if there is a panel to display associated with this filter,
     *         return false if the filter only has a checkbox.
     */
    boolean hasPanel() {
        return true;
    }

}
