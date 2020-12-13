/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.texttranslation.ui;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.TextViewer;
import org.sleuthkit.datamodel.AbstractFile;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.autopsy.textextractors.configs.ImageConfig;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import java.util.List;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.texttranslation.ui.TranslationContentPanel.DisplayDropdownOptions;

/**
 * A TextViewer that displays machine translation of text.
 */
@ServiceProvider(service = TextViewer.class, position = 4)
public final class TranslatedTextViewer implements TextViewer {

    private static final Logger logger = Logger.getLogger(TranslatedTextViewer.class.getName());

    private static final int MAX_EXTRACT_SIZE_BYTES = 25600;
    private static final List<String> INSTALLED_LANGUAGE_PACKS = PlatformUtil.getOcrLanguagePacks();
    private final TranslationContentPanel panel = new TranslationContentPanel();

    private volatile Node node;
    private volatile ExtractAndTranslateTextTask backgroundTask;
    private final ThreadFactory translationThreadFactory
            = new ThreadFactoryBuilder().setNameFormat("translation-content-viewer-%d").build();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(translationThreadFactory);

    @NbBundle.Messages({
        "TranslatedTextViewer.maxPayloadSize=Up to the first %dKB of text will be translated"
    })
    @Override
    public void setNode(final Node node) {
        this.node = node;
        SelectionChangeListener displayDropDownListener = new DisplayDropDownChangeListener();
        panel.addDisplayTextActionListener(displayDropDownListener);
        panel.addOcrDropDownActionListener(new OCRDropdownChangeListener());
        if (UserPreferences.getUseOcrInTranslation()) {
            panel.addLanguagePackNames(INSTALLED_LANGUAGE_PACKS);
        }
        panel.enableOCRSelection(UserPreferences.getUseOcrInTranslation());

        int payloadMaxInKB = TextTranslationService.getInstance().getMaxTextChars() / 1000;
        panel.setWarningLabelMsg(String.format(Bundle.TranslatedTextViewer_maxPayloadSize(), payloadMaxInKB));

        //Force a background task.
        displayDropDownListener.actionPerformed(null);
    }

    @NbBundle.Messages({"TranslatedTextViewer.title=Translation"})
    @Override
    public String getTitle() {
        return Bundle.TranslatedTextViewer_title();
    }

    @NbBundle.Messages({"TranslatedTextViewer.toolTip=Displays translated file text."})
    @Override
    public String getToolTip() {
        return Bundle.TranslatedTextViewer_toolTip();
    }

    @Override
    public TextViewer createInstance() {
        return new TranslatedTextViewer();
    }

    @Override
    public Component getComponent() {
        return panel;
    }

    @Override
    public void resetComponent() {
        panel.reset();
        this.node = null;
        if (backgroundTask != null) {
            backgroundTask.cancel(true);
        }
        backgroundTask = null;
    }

    @Override
    public boolean isSupported(Node node) {
        if (null == node) {
            return false;
        }

        if (!TextTranslationService.getInstance().hasProvider()) {
            return false;
        }

        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        return file != null;
    }

    @Override
    public int isPreferred(Node node) {
        // Returning zero makes it unlikely this object will be the preferred content viewer, 
        // i.e., the active tab, when the content viewers are first displayed.
        return 0;
    }

    /**
     * Extracts text from a file and optionally translates it.
     */
    private class ExtractAndTranslateTextTask extends TranslateTextTask {

        private final AbstractFile file;

        private ExtractAndTranslateTextTask(AbstractFile file, boolean translateText) {
            super(translateText, String.format("%s (objId=%d)", file.getName(), file.getId()));
            this.file = file;
        }

        /**
         * Extracts text from the current node
         *
         * @return Extracted text
         *
         * @throws Exception
         */
        @NbBundle.Messages({
            "TranslatedContentViewer.extractingText=Extracting text, please wait...",
            "# {0} - exception message", "TranslatedContentViewer.errorExtractingText=An error occurred while extracting the text ({0}).",
        })
        protected String retrieveText() throws IOException, InterruptedException, IllegalStateException {
            SwingUtilities.invokeLater(() -> {
                onProgressDisplay(Bundle.TranslatedContentViewer_extractingText(), ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
            });

            try {
                return getFileText(file);
            } catch (IOException | TextExtractor.InitReaderException ex) {
                logger.log(Level.WARNING, String.format("Error extracting text for file %s (objId=%d)", file.getName(), file.getId()), ex);
                // throw new exception with message to be displayed to user
                throw new IllegalStateException(Bundle.TranslatedContentViewer_errorExtractingText(ex.getMessage()), ex);
            }
        }
            
            
        /**
         * Extracts text from the given node
         *
         * @param file Selected node in UI
         *
         * @return Extracted text
         *
         * @throws IOException
         * @throws InterruptedException
         * @throws
         * org.sleuthkit.autopsy.textextractors.TextExtractor.InitReaderException
         */
        @NbBundle.Messages({
            "TranslatedContentViewer.ocrNotEnabled=OCR is not enabled. To change, go to Tools->Options->Machine Translation",
        })
        private String getFileText(AbstractFile file) throws IOException, InterruptedException, TextExtractor.InitReaderException {
            final boolean isImage = file.getMIMEType().toLowerCase().startsWith("image/"); // NON-NLS
            if (isImage && ! UserPreferences.getUseOcrInTranslation()) {
                return Bundle.TranslatedContentViewer_ocrNotEnabled();
            }
            
            String result = extractText(file, UserPreferences.getUseOcrInTranslation());

            //Correct for UTF-8
            byte[] resultInUTF8Bytes = result.getBytes("UTF8");
            byte[] trimToArraySize = Arrays.copyOfRange(resultInUTF8Bytes, 0,
                    Math.min(resultInUTF8Bytes.length, MAX_EXTRACT_SIZE_BYTES));
            return new String(trimToArraySize, "UTF-8");
        }

        /**
         * Fetches text from a file.
         *
         * @param source     the AbstractFile source to get a Reader for
         * @param ocrEnabled true if OCR is enabled false otherwise
         *
         * @return Extracted Text
         *
         * @throws IOException
         * @throws InterruptedException
         * @throws
         * org.sleuthkit.autopsy.textextractors.TextExtractor.InitReaderException
         */
        private String extractText(AbstractFile source, boolean ocrEnabled) throws IOException, InterruptedException, TextExtractor.InitReaderException {
            Reader textExtractor = getTextExtractor(source, ocrEnabled);

            char[] cbuf = new char[8096];
            StringBuilder textBuilder = new StringBuilder();

            //bytesRead can be an int so long as the max file size
            //is sufficiently small
            int bytesRead = 0;
            int read;

            while ((read = textExtractor.read(cbuf)) != -1) {
                if (this.isCancelled()) {
                    throw new InterruptedException();
                }

                //Short-circuit the read if its greater than our max
                //translatable size
                int bytesLeft = MAX_EXTRACT_SIZE_BYTES - bytesRead;

                if (bytesLeft < read) {
                    textBuilder.append(cbuf, 0, bytesLeft);
                    return textBuilder.toString();
                }

                textBuilder.append(cbuf, 0, read);
                bytesRead += read;
            }

            return textBuilder.toString();
        }

        /**
         * Fetches the appropriate reader for the given file mimetype and
         * configures it to use OCR.
         *
         * @param file       File to be read
         * @param ocrEnabled Determines if the extractor should be configured
         *                   for OCR
         *
         * @return Reader containing Content text
         *
         * @throws IOException
         * @throws NoTextReaderFound
         */
        private Reader getTextExtractor(AbstractFile file, boolean ocrEnabled) throws IOException,
                TextExtractor.InitReaderException {
            Lookup context = null;

            if (ocrEnabled) {
                ImageConfig imageConfig = new ImageConfig();
                imageConfig.setOCREnabled(true);

                String ocrSelection = panel.getSelectedOcrLanguagePack();
                if (!ocrSelection.isEmpty()) {
                    imageConfig.setOCRLanguages(Lists.newArrayList(ocrSelection));
                }

                //Terminate any OS process running in the extractor if this 
                //SwingWorker has been cancelled.
                ProcessTerminator terminator = () -> isCancelled();
                context = Lookups.fixed(imageConfig, terminator);
            }

            try {
                return TextExtractorFactory.getExtractor(file, context).getReader();
            } catch (TextExtractorFactory.NoTextExtractorFound ex) {
                //Fall-back onto the strings extractor
                return TextExtractorFactory.getStringsExtractor(file, context).getReader();
            }
        }


        @Override
        protected void onTextDisplay(String text, ComponentOrientation orientation, int font) {
            panel.display(text, orientation, font);
        }
    }


    /**
     * Listens for drop-down selection changes and pushes processing off of the
     * EDT and into a SwingWorker.
     */
    private abstract class SelectionChangeListener implements ActionListener {

        private String currentSelection;

        abstract String getSelection();

        @Override
        public final void actionPerformed(ActionEvent e) {
            String selection = getSelection();
            if (!selection.equals(currentSelection)) {
                currentSelection = selection;

                if (backgroundTask != null && !backgroundTask.isDone()) {
                    backgroundTask.cancel(true);
                }

                AbstractFile file = node.getLookup().lookup(AbstractFile.class);
                String textDisplaySelection = panel.getDisplayDropDownSelection();
                boolean translateText = !textDisplaySelection.equals(DisplayDropdownOptions.ORIGINAL_TEXT.toString());
                backgroundTask = new ExtractAndTranslateTextTask(file, translateText);

                //Pass the background task to a single threaded pool to keep
                //the number of jobs running to one.
                executorService.execute(backgroundTask);
            }
        }
    }

    /**
     * Fetches the display drop down selection from the panel.
     */
    private class DisplayDropDownChangeListener extends SelectionChangeListener {

        @Override
        String getSelection() {
            return panel.getDisplayDropDownSelection();
        }
    }

    /**
     * Fetches the OCR drop down selection from the panel.
     */
    private class OCRDropdownChangeListener extends SelectionChangeListener {

        @Override
        String getSelection() {
            return panel.getSelectedOcrLanguagePack();
        }
    }
}