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
package org.sleuthkit.autopsy.discovery.search;

import com.google.common.cache.CacheLoader;
import java.awt.Image;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.AbstractFile;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

import org.openide.util.ImageUtilities;

/**
 * Loads a thumbnail for the given request. Thumbnail candidates are JPEG files
 * that are either TSK_WEB_DOWNLOAD or TSK_WEB_CACHE artifacts. JPEG files are
 * sorted by most recent if sourced from TSK_WEB_DOWNLOADs. JPEG files are
 * sorted by size if sourced from TSK_WEB_CACHE artifacts. Artifacts are first
 * loaded from the DomainSearchArtifactsCache and then further analyzed.
 */
public class DomainSearchThumbnailLoader extends CacheLoader<DomainSearchThumbnailRequest, Image> {

    private static final String UNSUPPORTED_IMAGE = "org/sleuthkit/autopsy/images/image-extraction-not-supported.png";
    private static final String JPG_EXTENSION = "jpg";
    private static final String JPG_MIME_TYPE = "image/jpeg";
    private final DomainSearchArtifactsCache artifactsCache;

    /**
     * Construct a new DomainSearchThumbnailLoader.
     */
    public DomainSearchThumbnailLoader() {
        this(new DomainSearchArtifactsCache());
    }

    /**
     * Construct a new DomainSearchThumbnailLoader with an existing
     * DomainSearchArtifactsCache.
     *
     * @param artifactsCache The DomainSearchArtifactsCache to use for this
     *                       DomainSearchThumnailLoader.
     */
    DomainSearchThumbnailLoader(DomainSearchArtifactsCache artifactsCache) {
        this.artifactsCache = artifactsCache;
    }

    @Override
    public Image load(DomainSearchThumbnailRequest thumbnailRequest) throws TskCoreException, DiscoveryException, InterruptedException {
        final SleuthkitCase caseDb = thumbnailRequest.getSleuthkitCase();
        final DomainSearchArtifactsRequest webDownloadsRequest = new DomainSearchArtifactsRequest(
                caseDb, thumbnailRequest.getDomain(), TSK_WEB_DOWNLOAD);
        final List<BlackboardArtifact> webDownloads = artifactsCache.get(webDownloadsRequest);
        final List<AbstractFile> webDownloadPictures = getJpegsFromWebDownload(caseDb, webDownloads);
        Collections.sort(webDownloadPictures, (file1, file2) -> Long.compare(file1.getCrtime(), file2.getCrtime()));
        for (int i = webDownloadPictures.size() - 1; i >= 0; i--) {
            if(Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            // Get the most recent image, according to creation time.
            final AbstractFile mostRecent = webDownloadPictures.get(i);

            final Image candidateThumbnail = ImageUtils.getThumbnail(mostRecent, thumbnailRequest.getIconSize());
            if (candidateThumbnail != ImageUtils.getDefaultThumbnail()) {
                return candidateThumbnail;
            }
        }
        final DomainSearchArtifactsRequest webCacheRequest = new DomainSearchArtifactsRequest(
                caseDb, thumbnailRequest.getDomain(), TSK_WEB_CACHE);
        final List<BlackboardArtifact> webCacheArtifacts = artifactsCache.get(webCacheRequest);
        final List<AbstractFile> webCachePictures = getJpegsFromWebCache(caseDb, webCacheArtifacts);
        Collections.sort(webCachePictures, (file1, file2) -> Long.compare(file1.getSize(), file2.getSize()));
        for (int i = webCachePictures.size() - 1; i >= 0; i--) {
            if(Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            // Get the largest image, according to file size.
            final AbstractFile largest = webCachePictures.get(i);
            final Image candidateThumbnail = ImageUtils.getThumbnail(largest, thumbnailRequest.getIconSize());
            if (candidateThumbnail != ImageUtils.getDefaultThumbnail()) {
                return candidateThumbnail;
            }
        }
        return ImageUtilities.loadImage(UNSUPPORTED_IMAGE, false);
    }

    /**
     * Finds all JPEG source files from TSK_WEB_DOWNLOAD instances.
     *
     * @param caseDb    The case database being searched.
     * @param artifacts The list of artifacts to get jpegs from.
     *
     * @return The list of AbstractFiles representing jpegs which were
     *         associated with the artifacts.
     *
     * @throws TskCoreException
     */
    private List<AbstractFile> getJpegsFromWebDownload(SleuthkitCase caseDb, List<BlackboardArtifact> artifacts) throws TskCoreException, InterruptedException {
        final List<AbstractFile> jpegs = new ArrayList<>();
        for (BlackboardArtifact artifact : artifacts) {
            if(Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            final Content sourceContent = caseDb.getContentById(artifact.getObjectID());
            addIfJpeg(jpegs, sourceContent);
        }
        return jpegs;
    }

    /**
     * Finds all JPEG source files from TSK_WEB_CACHE instances.
     *
     * @param caseDb    The case database being searched.
     * @param artifacts The list of artifacts to get jpegs from.
     *
     * @return The list of AbstractFiles representing jpegs which were
     *         associated with the artifacts.
     */
    private List<AbstractFile> getJpegsFromWebCache(SleuthkitCase caseDb, List<BlackboardArtifact> artifacts) throws TskCoreException, InterruptedException {
        final BlackboardAttribute.Type TSK_PATH_ID = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH_ID);
        final List<AbstractFile> jpegs = new ArrayList<>();
        for (BlackboardArtifact artifact : artifacts) {
            if(Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            final BlackboardAttribute tskPathId = artifact.getAttribute(TSK_PATH_ID);
            if (tskPathId != null) {
                final Content sourceContent = caseDb.getContentById(tskPathId.getValueLong());
                addIfJpeg(jpegs, sourceContent);
            }
        }
        return jpegs;
    }

    /**
     * Checks if the candidate source content is indeed a JPEG file.
     *
     * @param files         The list of source content files which are jpegs to
     *                      add to.
     * @param sourceContent The source content to check and possibly add.
     */
    private void addIfJpeg(List<AbstractFile> files, Content sourceContent) {
        if ((sourceContent instanceof AbstractFile) && !(sourceContent instanceof DataSource)) {
            final AbstractFile file = (AbstractFile) sourceContent;
            if (JPG_EXTENSION.equals(file.getNameExtension())
                    || JPG_MIME_TYPE.equals(file.getMIMEType())) {
                files.add(file);
            }
        }
    }
}
