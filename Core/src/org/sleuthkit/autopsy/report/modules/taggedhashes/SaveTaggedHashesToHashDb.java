/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.taggedhashes;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JPanel;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.GeneralReportSettings;
import org.sleuthkit.autopsy.report.NoReportModuleSettings;
import org.sleuthkit.autopsy.report.ReportModuleSettings;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class plug in to the reporting infrastructure to provide a
 * convenient way to add content hashes to hash set databases.
 */
@ServiceProvider(service = GeneralReportModule.class)
public class SaveTaggedHashesToHashDb implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(SaveTaggedHashesToHashDb.class.getName());
    private SaveTaggedHashesToHashDbConfigPanel configPanel;

    public SaveTaggedHashesToHashDb() {
    }

    @Override
    public String getName() {
        return "Save Tagged Hashes";
    }

    @Override
    public String getDescription() {
        return "Adds hashes of tagged files to a hash set.";
    }

    @Override
    public String getRelativeFilePath() {
        return null;
    }
    
    /**
     * Get default configuration for this report module.
     *
     * @return Object which contains default report module settings.
     */
    @Override
    public ReportModuleSettings getDefaultConfiguration() {
        return new HashesReportModuleSettings();
    }

    /**
     * Get current configuration for this report module.
     *
     * @return Object which contains current report module settings.
     */
    @Override
    public ReportModuleSettings getConfiguration() {
        initializePanel();
        return configPanel.getConfiguration();
    }

    /**
     * Set report module configuration.
     *
     * @param settings Object which contains report module settings.
     */
    @Override
    public void setConfiguration(ReportModuleSettings settings) {
        initializePanel();
        if (settings == null || settings instanceof NoReportModuleSettings) {
            configPanel.setConfiguration((HashesReportModuleSettings) getDefaultConfiguration());
            return;
        }
        
        if (settings instanceof HashesReportModuleSettings) {
            configPanel.setConfiguration((HashesReportModuleSettings) settings);
            return;
        }

        throw new IllegalArgumentException("Expected settings argument to be an instance of HashesReportModuleSettings");
    }    

    @Messages({
        "AddTaggedHashesToHashDb.error.noHashSetsSelected=No hash set selected for export.",
        "AddTaggedHashesToHashDb.error.unableToOpenCase=Exception while getting open case.",
        "AddTaggedHashesToHashDb.error.noTagsSelected=No tags selected for export."
    })
    @Override
    public void generateReport(GeneralReportSettings settings, ReportProgressPanel progressPanel) {
        Case openCase;
        try {
            openCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(SaveTaggedHashesToHashDb.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex);
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, Bundle.AddTaggedHashesToHashDb_error_unableToOpenCase());
            return;
        }
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        progressPanel.updateStatusLabel("Adding hashes...");

        HashDb hashSet = configPanel.getSelectedHashDatabase();
        if (hashSet == null) {
            logger.log(Level.WARNING, "No hash set selected for export."); //NON-NLS
            progressPanel.setIndeterminate(false);
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, Bundle.AddTaggedHashesToHashDb_error_noHashSetsSelected());
            return;
        }
        
        progressPanel.updateStatusLabel("Adding hashes to " + hashSet.getHashSetName() + " hash set...");

        TagsManager tagsManager = openCase.getServices().getTagsManager();
        List<TagName> tagNames = configPanel.getSelectedTagNames();
        if (tagNames.isEmpty()) {
            logger.log(Level.WARNING, "No tags selected for export."); //NON-NLS
            progressPanel.setIndeterminate(false);
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, Bundle.AddTaggedHashesToHashDb_error_noTagsSelected());
            return;
        }
        
        ArrayList<String> failedExports = new ArrayList<>();
        for (TagName tagName : tagNames) {
            if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                break;
            }

            progressPanel.updateStatusLabel("Adding " + tagName.getDisplayName() + " hashes to " + hashSet.getHashSetName() + " hash set...");
            try {
                List<ContentTag> tags = tagsManager.getContentTagsByTagName(tagName);
                for (ContentTag tag : tags) {
                    // TODO: Currently only AbstractFiles have md5 hashes. Here only files matter. 
                    Content content = tag.getContent();
                    if (content instanceof AbstractFile) {
                        if (null != ((AbstractFile) content).getMd5Hash()) {
                            try {
                                hashSet.addHashes(tag.getContent(), openCase.getDisplayName());
                            } catch (TskCoreException ex) {
                                Logger.getLogger(SaveTaggedHashesToHashDb.class.getName()).log(Level.SEVERE, "Error adding hash for obj_id = " + tag.getContent().getId() + " to hash set " + hashSet.getHashSetName(), ex);
                                failedExports.add(tag.getContent().getName());
                            }
                        } else {
                            progressPanel.updateStatusLabel("Unable to add the " + (tags.size() > 1 ? "files" : "file") + " to the hash set. Hashes have not been calculated. Please configure and run an appropriate ingest module.");
                            break;
                        }
                    }
                }
            } catch (TskCoreException ex) {
                Logger.getLogger(SaveTaggedHashesToHashDb.class.getName()).log(Level.SEVERE, "Error adding to hash set", ex);
                progressPanel.updateStatusLabel("Error getting selected tags for case.");
            }
        }
        if (!failedExports.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("Failed to export hashes for the following files: ");
            for (int i = 0; i < failedExports.size(); ++i) {
                errorMessage.append(failedExports.get(i));
                if (failedExports.size() > 1 && i < failedExports.size() - 1) {
                    errorMessage.append(",");
                }
                if (i == failedExports.size() - 1) {
                    errorMessage.append(".");
                }
            }
            progressPanel.updateStatusLabel(errorMessage.toString());
        }
        
        progressPanel.setIndeterminate(false);
        progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
    }

    @Override
    public JPanel getConfigurationPanel() {
        initializePanel();
        return configPanel;
    }
    
    private void initializePanel() {
        if (configPanel == null) {
            configPanel = new SaveTaggedHashesToHashDbConfigPanel();
        }
    }
}
