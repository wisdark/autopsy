/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.util.Collection;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Abstract class for common methods of a loadable component.
 */
@NbBundle.Messages({
    "AbstractLoadableComponent_loadingMessage_defaultText=Loading results...",
    "AbstractLoadableComponent_errorMessage_defaultText=There was an error loading results.",
    "AbstractLoadableComponent_noDataExists_defaultText=No data exists.",})
public abstract class AbstractLoadableComponent<T> extends JPanel implements LoadableComponent<T> {

    private static final long serialVersionUID = 1L;
    
    /**
     * The default loading message.
     */
    public static final String DEFAULT_LOADING_MESSAGE = Bundle.AbstractLoadableComponent_loadingMessage_defaultText();

    /**
     * The default error message.
     */
    public static final String DEFAULT_ERROR_MESSAGE = Bundle.AbstractLoadableComponent_errorMessage_defaultText();

    /**
     * The default 'no results' message.
     */
    public static final String DEFAULT_NO_RESULTS_MESSAGE = Bundle.AbstractLoadableComponent_noDataExists_defaultText();

    private static final Logger logger = Logger.getLogger(AbstractLoadableComponent.class.getName());

    /**
     * @return The default error message.
     */
    public static String getDefaultErrorMessage() {
        return DEFAULT_ERROR_MESSAGE;
    }

    /**
     * @return The default message for no results.
     */
    public static String getDefaultNoResultsMessage() {
        return DEFAULT_NO_RESULTS_MESSAGE;
    }

    /**
     * Clears the results from the underlying JTable and shows the provided
     * message.
     *
     * @param message The message to be shown.
     */
    public synchronized void showMessage(String message) {
        setResults(null);
        setMessage(true, message);
        repaint();
    }

    /**
     * Shows a default loading message on the table. This will clear any results
     * in the table.
     */
    public void showDefaultLoadingMessage() {
        showMessage(DEFAULT_LOADING_MESSAGE);
    }

    /**
     * Shows the list as rows of data in the table. If overlay message will be
     * cleared if present.
     *
     * @param data The data to be shown where each item represents a row of
     *             data.
     */
    public synchronized void showResults(T data) {
        setMessage(false, null);
        setResults(data);
        repaint();
    }

    /**
     * Shows the data in a DataFetchResult. If there was an error during the
     * operation, the errorMessage will be displayed. If the operation completed
     * successfully and no data is present, noResultsMessage will be shown.
     * Otherwise, the data will be shown as rows in the table.
     *
     * @param result           The DataFetchResult.
     * @param errorMessage     The error message to be shown in the event of an
     *                         error.
     * @param noResultsMessage The message to be shown if there are no results
     *                         but the operation completed successfully.
     */
    public void showDataFetchResult(DataFetchResult<T> result, String errorMessage, String noResultsMessage) {
        if (result == null) {
            logger.log(Level.SEVERE, "Null data fetch result received.");
            return;
        }

        switch (result.getResultType()) {
            case SUCCESS:
                T data = result.getData();
                if (data == null || (data instanceof Collection<?> && ((Collection<?>) data).isEmpty())) {
                    showMessage(noResultsMessage);
                } else {
                    showResults(data);
                }
                break;
            case ERROR:
                // if there is an error, log accordingly, set result list to 
                // empty and display error message
                logger.log(Level.WARNING, "An exception was caused while results were loaded.", result.getException());
                showMessage(errorMessage);
                break;
            default:
                // an unknown loading state was specified.  log accordingly.
                logger.log(Level.SEVERE, "No known loading state was found in result.");
                break;
        }
    }

    /**
     * Shows the data in a DataFetchResult. If there was an error during the
     * operation, the DEFAULT_ERROR_MESSAGE will be displayed. If the operation
     * completed successfully and no data is present, DEFAULT_NO_RESULTS_MESSAGE
     * will be shown. Otherwise, the data will be shown as rows in the table.
     *
     * @param result The DataFetchResult.
     */
    public void showDataFetchResult(DataFetchResult<T> result) {
        showDataFetchResult(result, DEFAULT_ERROR_MESSAGE, DEFAULT_NO_RESULTS_MESSAGE);
    }

    /**
     * Sets the message and visibility of the message. Repaint does not need to
     * be handled in this method.
     *
     * @param visible The visibility of the message.
     * @param message The message to be displayed if visible.
     */
    protected abstract void setMessage(boolean visible, String message);

    /**
     * Sets the data to be shown in the JTable. Repaint does not need to be
     * handled in this method.
     *
     * @param data The list of data objects to be shown.
     */
    protected abstract void setResults(T data);
}
