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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.text.DecimalFormat;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.table.DefaultTableModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceDetailsSummary;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display additional details associated with a specific DataSource
 */
class DataSourceSummaryDetailsPanel extends BaseDataSourceSummaryPanel {

    //Because this panel was made using the gridbaglayout and netbean's Customize Layout tool it will be best to continue to modify it through that
    private static final long serialVersionUID = 1L;
    private static final Integer SIZE_COVERSION_CONSTANT = 1000;
    private static final DecimalFormat APPROXIMATE_SIZE_FORMAT = new DecimalFormat("#.##");
    private static final Logger logger = Logger.getLogger(DataSourceSummaryDetailsPanel.class.getName());

    /**
     * Creates new form DataSourceSummaryDetailsPanel
     */
    @Messages({"DataSourceSummaryDetailsPanel.getDataSources.error.text=Failed to get the list of datasources for the current case.",
        "DataSourceSummaryDetailsPanel.getDataSources.error.title=Load Failure"})
    DataSourceSummaryDetailsPanel() {
        initComponents();
        setDataSource(null);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        if (dataSource == null || !Case.isCaseOpen()) {
            updateDetailsPanelData(null, null, null, null);
        } else {
            updateDetailsPanelData(dataSource,
                    DataSourceDetailsSummary.getSizeOfUnallocatedFiles(dataSource),
                    DataSourceDetailsSummary.getOperatingSystems(dataSource),
                    DataSourceDetailsSummary.getDataSourceType(dataSource));
        }
    }

    /**
     * Update which DataSource this panel should display details about
     *
     * @param selectedDataSource the DataSource to display details about.
     */
    private void updateDetailsPanelData(DataSource selectedDataSource, Long unallocatedFilesSize, String osDetails, String usage) {
        clearTableValues();
        if (selectedDataSource != null) {
            unallocatedSizeValue.setText(getSizeString(unallocatedFilesSize));

            timeZoneValue.setText(selectedDataSource.getTimeZone());
            displayNameValue.setText(selectedDataSource.getName());
            originalNameValue.setText(selectedDataSource.getName());
            deviceIdValue.setText(selectedDataSource.getDeviceId());

            try {
                acquisitionDetailsTextArea.setText(selectedDataSource.getAcquisitionDetails());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get aquisition details for selected data source", ex);
            }

            if (selectedDataSource instanceof Image) {
                setFieldsForImage((Image) selectedDataSource);
            }
        }
        updateFieldVisibility();
        this.repaint();
    }

    /**
     * Sets text fields for an image. This should be called after
     * clearTableValues and before updateFieldVisibility to ensure the proper
     * rendering.
     *
     * @param selectedImage The selected image.
     */
    private void setFieldsForImage(Image selectedImage) {
        imageTypeValue.setText(selectedImage.getType().getName());
        sizeValue.setText(getSizeString(selectedImage.getSize()));
        sectorSizeValue.setText(getSizeString(selectedImage.getSsize()));

        for (String path : selectedImage.getPaths()) {
            ((DefaultTableModel) filePathsTable.getModel()).addRow(new Object[]{path});
        }

        try {
            //older databases may have null as the hash values
            String md5String = selectedImage.getMd5();
            if (md5String == null) {
                md5String = "";
            }
            md5HashValue.setText(md5String);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get MD5 for selected data source", ex);
        }

        try {
            String sha1String = selectedImage.getSha1();
            if (sha1String == null) {
                sha1String = "";
            }
            sha1HashValue.setText(sha1String);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get SHA1 for selected data source", ex);
        }

        try {
            String sha256String = selectedImage.getSha256();
            if (sha256String == null) {
                sha256String = "";
            }
            sha256HashValue.setText(sha256String);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get SHA256 for selected data source", ex);
        }
    }

    /**
     * Get a long size in bytes as a string formated to be read by users.
     *
     * @param size Long value representing a size in bytes
     *
     * @return return a string formated with a user friendly version of the size
     *         as a string, returns empty String when provided empty size
     */
    @Messages({
        "DataSourceSummaryDetailsPanel.units.bytes= bytes",
        "DataSourceSummaryDetailsPanel.units.kilobytes= kB",
        "DataSourceSummaryDetailsPanel.units.megabytes= MB",
        "DataSourceSummaryDetailsPanel.units.gigabytes= GB",
        "DataSourceSummaryDetailsPanel.units.terabytes= TB",
        "DataSourceSummaryDetailsPanel.units.petabytes= PB"
    })
    private static String getSizeString(Long size) {
        if (size == null) {
            return "";
        }
        double approximateSize = size;
        if (approximateSize < SIZE_COVERSION_CONSTANT) {
            return String.valueOf(size) + Bundle.DataSourceSummaryDetailsPanel_units_bytes();
        }
        approximateSize /= SIZE_COVERSION_CONSTANT;
        if (approximateSize < SIZE_COVERSION_CONSTANT) {
            return APPROXIMATE_SIZE_FORMAT.format(approximateSize) + Bundle.DataSourceSummaryDetailsPanel_units_kilobytes()
                    + " (" + String.valueOf(size) + Bundle.DataSourceSummaryDetailsPanel_units_bytes() + ")";
        }
        approximateSize /= SIZE_COVERSION_CONSTANT;
        if (approximateSize < SIZE_COVERSION_CONSTANT) {
            return APPROXIMATE_SIZE_FORMAT.format(approximateSize) + Bundle.DataSourceSummaryDetailsPanel_units_megabytes()
                    + " (" + String.valueOf(size) + Bundle.DataSourceSummaryDetailsPanel_units_bytes() + ")";
        }
        approximateSize /= SIZE_COVERSION_CONSTANT;
        if (approximateSize < SIZE_COVERSION_CONSTANT) {
            return APPROXIMATE_SIZE_FORMAT.format(approximateSize) + Bundle.DataSourceSummaryDetailsPanel_units_gigabytes()
                    + " (" + String.valueOf(size) + Bundle.DataSourceSummaryDetailsPanel_units_bytes() + ")";
        }
        approximateSize /= SIZE_COVERSION_CONSTANT;
        if (approximateSize < SIZE_COVERSION_CONSTANT) {
            return APPROXIMATE_SIZE_FORMAT.format(approximateSize) + Bundle.DataSourceSummaryDetailsPanel_units_terabytes()
                    + " (" + String.valueOf(size) + Bundle.DataSourceSummaryDetailsPanel_units_bytes() + ")";
        }
        approximateSize /= SIZE_COVERSION_CONSTANT;
        return APPROXIMATE_SIZE_FORMAT.format(approximateSize) + Bundle.DataSourceSummaryDetailsPanel_units_petabytes()
                + " (" + String.valueOf(size) + Bundle.DataSourceSummaryDetailsPanel_units_bytes() + ")";
    }

    /**
     * Update the visibility of all fields and their labels based on whether
     * they have contents. Empty fields have them and their contents hidden.
     */
    private void updateFieldVisibility() {
        displayNameValue.setVisible(!displayNameValue.getText().isEmpty());
        displayNameLabel.setVisible(!displayNameValue.getText().isEmpty());
        originalNameValue.setVisible(!originalNameValue.getText().isEmpty());
        originalNameLabel.setVisible(!originalNameValue.getText().isEmpty());
        deviceIdValue.setVisible(!deviceIdValue.getText().isEmpty());
        deviceIdLabel.setVisible(!deviceIdValue.getText().isEmpty());
        timeZoneValue.setVisible(!timeZoneValue.getText().isEmpty());
        timeZoneLabel.setVisible(!timeZoneValue.getText().isEmpty());
        acquisitionDetailsTextArea.setVisible(!acquisitionDetailsTextArea.getText().isEmpty());
        acquisitionDetailsLabel.setVisible(!acquisitionDetailsTextArea.getText().isEmpty());
        acquisitionDetailsScrollPane.setVisible(!acquisitionDetailsTextArea.getText().isEmpty());
        imageTypeValue.setVisible(!imageTypeValue.getText().isEmpty());
        imageTypeLabel.setVisible(!imageTypeValue.getText().isEmpty());
        sizeValue.setVisible(!sizeValue.getText().isEmpty());
        sizeLabel.setVisible(!sizeValue.getText().isEmpty());
        sectorSizeValue.setVisible(!sectorSizeValue.getText().isEmpty());
        sectorSizeLabel.setVisible(!sectorSizeValue.getText().isEmpty());
        md5HashValue.setVisible(!md5HashValue.getText().isEmpty());
        md5HashLabel.setVisible(!md5HashValue.getText().isEmpty());
        sha1HashValue.setVisible(!sha1HashValue.getText().isEmpty());
        sha1HashLabel.setVisible(!sha1HashValue.getText().isEmpty());
        sha256HashValue.setVisible(!sha256HashValue.getText().isEmpty());
        sha256HashLabel.setVisible(!sha256HashValue.getText().isEmpty());
        unallocatedSizeValue.setVisible(!unallocatedSizeValue.getText().isEmpty());
        unallocatedSizeLabel.setVisible(!unallocatedSizeValue.getText().isEmpty());
        filePathsTable.setVisible(filePathsTable.getRowCount() > 0);
        filePathsLabel.setVisible(filePathsTable.getRowCount() > 0);
        filePathsScrollPane.setVisible(filePathsTable.getRowCount() > 0);
    }

    /**
     * Set the contents of all fields to be empty.
     */
    private void clearTableValues() {
        displayNameValue.setText("");
        originalNameValue.setText("");
        deviceIdValue.setText("");
        timeZoneValue.setText("");
        acquisitionDetailsTextArea.setText("");
        imageTypeValue.setText("");
        sizeValue.setText("");
        sectorSizeValue.setText("");
        md5HashValue.setText("");
        sha1HashValue.setText("");
        sha256HashValue.setText("");
        unallocatedSizeValue.setText("");
        ((DefaultTableModel) filePathsTable.getModel()).setRowCount(0);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        displayNameLabel = new javax.swing.JLabel();
        originalNameLabel = new javax.swing.JLabel();
        sha1HashValue = new javax.swing.JLabel();
        displayNameValue = new javax.swing.JLabel();
        sha256HashValue = new javax.swing.JLabel();
        originalNameValue = new javax.swing.JLabel();
        deviceIdValue = new javax.swing.JLabel();
        filePathsScrollPane = new javax.swing.JScrollPane();
        filePathsTable = new javax.swing.JTable();
        timeZoneValue = new javax.swing.JLabel();
        imageTypeValue = new javax.swing.JLabel();
        md5HashValue = new javax.swing.JLabel();
        sectorSizeValue = new javax.swing.JLabel();
        sizeValue = new javax.swing.JLabel();
        filePathsLabel = new javax.swing.JLabel();
        sha256HashLabel = new javax.swing.JLabel();
        sha1HashLabel = new javax.swing.JLabel();
        md5HashLabel = new javax.swing.JLabel();
        sectorSizeLabel = new javax.swing.JLabel();
        sizeLabel = new javax.swing.JLabel();
        imageTypeLabel = new javax.swing.JLabel();
        acquisitionDetailsLabel = new javax.swing.JLabel();
        timeZoneLabel = new javax.swing.JLabel();
        deviceIdLabel = new javax.swing.JLabel();
        acquisitionDetailsScrollPane = new javax.swing.JScrollPane();
        acquisitionDetailsTextArea = new javax.swing.JTextArea();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
        unallocatedSizeLabel = new javax.swing.JLabel();
        unallocatedSizeValue = new javax.swing.JLabel();

        jPanel1.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(displayNameLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.displayNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 4);
        jPanel1.add(displayNameLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(originalNameLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.originalNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(originalNameLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha1HashValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.sha1HashValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(sha1HashValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(displayNameValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.displayNameValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 10);
        jPanel1.add(displayNameValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha256HashValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.sha256HashValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 10);
        jPanel1.add(sha256HashValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(originalNameValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.originalNameValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(originalNameValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(deviceIdValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.deviceIdValue.text")); // NOI18N
        deviceIdValue.setToolTipText(org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.deviceIdValue.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 10);
        jPanel1.add(deviceIdValue, gridBagConstraints);

        filePathsScrollPane.setPreferredSize(new java.awt.Dimension(80, 50));

        filePathsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                ""
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        filePathsTable.setTableHeader(null);
        filePathsScrollPane.setViewportView(filePathsTable);
        if (filePathsTable.getColumnModel().getColumnCount() > 0) {
            filePathsTable.getColumnModel().getColumn(0).setHeaderValue(org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.filePathsTable.columnModel.title0")); // NOI18N
        }

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.2;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 10, 10);
        jPanel1.add(filePathsScrollPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.timeZoneValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 10);
        jPanel1.add(timeZoneValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(imageTypeValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.imageTypeValue.text")); // NOI18N
        imageTypeValue.setToolTipText(org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.imageTypeValue.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 10);
        jPanel1.add(imageTypeValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(md5HashValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.md5HashValue.text")); // NOI18N
        md5HashValue.setToolTipText(org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.md5HashValue.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(md5HashValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sectorSizeValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.sectorSizeValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(sectorSizeValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sizeValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.sizeValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(sizeValue, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(filePathsLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.filePathsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weighty = 1.2;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 10, 4);
        jPanel1.add(filePathsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha256HashLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.sha256HashLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 6, 4);
        jPanel1.add(sha256HashLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sha1HashLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.sha1HashLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(sha1HashLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(md5HashLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.md5HashLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(md5HashLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sectorSizeLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.sectorSizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(sectorSizeLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(sizeLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.sizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(sizeLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(imageTypeLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.imageTypeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 4);
        jPanel1.add(imageTypeLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(acquisitionDetailsLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.acquisitionDetailsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weighty = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 6, 4);
        jPanel1.add(acquisitionDetailsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.timeZoneLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 6, 4);
        jPanel1.add(timeZoneLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(deviceIdLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.deviceIdLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 6, 4);
        jPanel1.add(deviceIdLabel, gridBagConstraints);

        acquisitionDetailsTextArea.setEditable(false);
        acquisitionDetailsTextArea.setBackground(javax.swing.UIManager.getDefaults().getColor("TextArea.disabledBackground"));
        acquisitionDetailsTextArea.setColumns(20);
        acquisitionDetailsTextArea.setRows(4);
        acquisitionDetailsTextArea.setText(org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.acquisitionDetailsTextArea.text")); // NOI18N
        acquisitionDetailsTextArea.setBorder(null);
        acquisitionDetailsScrollPane.setViewportView(acquisitionDetailsTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 10);
        jPanel1.add(acquisitionDetailsScrollPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 0.1;
        jPanel1.add(filler1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 0.1;
        jPanel1.add(filler2, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(unallocatedSizeLabel, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.unallocatedSizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 4);
        jPanel1.add(unallocatedSizeLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(unallocatedSizeValue, org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.unallocatedSizeValue.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel1.add(unallocatedSizeValue, gridBagConstraints);

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel acquisitionDetailsLabel;
    private javax.swing.JScrollPane acquisitionDetailsScrollPane;
    private javax.swing.JTextArea acquisitionDetailsTextArea;
    private javax.swing.JLabel deviceIdLabel;
    private javax.swing.JLabel deviceIdValue;
    private javax.swing.JLabel displayNameLabel;
    private javax.swing.JLabel displayNameValue;
    private javax.swing.JLabel filePathsLabel;
    private javax.swing.JScrollPane filePathsScrollPane;
    private javax.swing.JTable filePathsTable;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JLabel imageTypeLabel;
    private javax.swing.JLabel imageTypeValue;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel md5HashLabel;
    private javax.swing.JLabel md5HashValue;
    private javax.swing.JLabel originalNameLabel;
    private javax.swing.JLabel originalNameValue;
    private javax.swing.JLabel sectorSizeLabel;
    private javax.swing.JLabel sectorSizeValue;
    private javax.swing.JLabel sha1HashLabel;
    private javax.swing.JLabel sha1HashValue;
    private javax.swing.JLabel sha256HashLabel;
    private javax.swing.JLabel sha256HashValue;
    private javax.swing.JLabel sizeLabel;
    private javax.swing.JLabel sizeValue;
    private javax.swing.JLabel timeZoneLabel;
    private javax.swing.JLabel timeZoneValue;
    private javax.swing.JLabel unallocatedSizeLabel;
    private javax.swing.JLabel unallocatedSizeValue;
    // End of variables declaration//GEN-END:variables
}
