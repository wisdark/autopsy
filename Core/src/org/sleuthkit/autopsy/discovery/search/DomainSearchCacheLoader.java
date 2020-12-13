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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes.AttributeType;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes.DataSourceAttribute;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.SearchKey;
import org.sleuthkit.autopsy.discovery.search.SearchFiltering.ArtifactDateRangeFilter;
import org.sleuthkit.autopsy.discovery.search.SearchFiltering.ArtifactTypeFilter;
import org.sleuthkit.autopsy.discovery.search.SearchFiltering.DataSourceFilter;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbAccessQueryCallback;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Loads domain search results for cache misses. This loader is a Guava cache
 * loader, which will be used in tandem with the DomainSearchCache, which is
 * backed by a Guava LoadingCache.
 */
class DomainSearchCacheLoader extends CacheLoader<SearchKey, Map<GroupKey, List<Result>>> {

    @Override
    public Map<GroupKey, List<Result>> load(SearchKey key) throws DiscoveryException, SQLException, TskCoreException, InterruptedException {
        List<Result> domainResults = getResultDomainsFromDatabase(key);
        // Grouping by CR Frequency, for example, will require further processing
        // in order to make the correct decision. The attribute types that require
        // more information implement their logic by overriding `addAttributeToResults`.
        List<AttributeType> searchAttributes = new ArrayList<>();
        searchAttributes.add(key.getGroupAttributeType());
        searchAttributes.addAll(key.getFileSortingMethod().getRequiredAttributes());
        for (AttributeType attr : searchAttributes) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            attr.addAttributeToResults(domainResults,
                    key.getSleuthkitCase(), key.getCentralRepository());
        }
        // Apply secondary in memory filters
        for (AbstractFilter filter : key.getFilters()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            if (filter.useAlternateFilter()) {
                domainResults = filter.applyAlternateFilter(domainResults, key.getSleuthkitCase(), key.getCentralRepository());
            }
        }
        // Sort the ResultDomains by the requested criteria.
        final SearchResults searchResults = new SearchResults(
                key.getGroupSortingType(),
                key.getGroupAttributeType(),
                key.getFileSortingMethod());
        searchResults.add(domainResults);
        return searchResults.toLinkedHashMap();
    }

    /**
     * Queries for domain names from the case database.
     *
     * @param key The SearchKey passed to the cache.
     *
     * @return A list of results corresponding to the domains found in the case
     *         database.
     */
    List<Result> getResultDomainsFromDatabase(SearchKey key) throws TskCoreException, SQLException, DiscoveryException, InterruptedException {

        // Filters chosen in the UI are aggregated into SQL statements to be used in 
        // the queries that follow.
        final Pair<String, String> filterClauses = createWhereAndHavingClause(key.getFilters());
        final String whereClause = filterClauses.getLeft();
        final String havingClause = filterClauses.getRight();

        // You may think of each row of this result as a TSK_DOMAIN attribute, where the parent
        // artifact type is within the (optional) filter and the parent artifact
        // had a date time attribute that was within the (optional) filter. With this
        // table in hand, we can simply group by domain and apply aggregate functions
        // to get, for example, # of downloads, # of visits in last 60, etc.
        final String domainsTable
                = "SELECT LOWER(MAX(value_text))  AS domain,"
                + "       MAX(value_int64) AS date,"
                + "       artifact_id AS parent_artifact_id,"
                + "       MAX(artifact_type_id) AS parent_artifact_type_id "
                + "FROM   blackboard_attributes "
                + "WHERE  " + whereClause + " "
                + "GROUP BY artifact_id "
                + "HAVING " + havingClause;

        // Needed to populate the visitsInLast60 data.
        final Instant currentTime = Instant.now();
        final Instant sixtyDaysAgo = currentTime.minus(60, ChronoUnit.DAYS);

        // Check the group attribute, if by data source then the GROUP BY clause
        // should group by data source id before grouping by domain.
        final AttributeType groupAttribute = key.getGroupAttributeType();
        final String groupByClause = (groupAttribute instanceof DataSourceAttribute)
                ? "data_source_obj_id, domain" : "domain";

        final Optional<AbstractFilter> dataSourceFilter = key.getFilters().stream()
                .filter(filter -> filter instanceof DataSourceFilter)
                .findFirst();

        String dataSourceWhereClause = null;
        if (dataSourceFilter.isPresent()) {
            dataSourceWhereClause = dataSourceFilter.get().getWhereClause();
        }

        // This query just processes the domains table, performing additional 
        // groupings and applying aggregate functions to calculate discovery data.
        final String domainsQuery
                = /*
                 * SELECT
                 */ " domain,"
                + "           MIN(date) AS activity_start,"
                + "           MAX(date) AS activity_end,"
                + "           SUM(CASE "
                + "                 WHEN artifact_type_id = " + TSK_WEB_DOWNLOAD.getTypeID() + " THEN 1 "
                + "                 ELSE 0 "
                + "               END) AS fileDownloads,"
                + "           SUM(CASE "
                + "                 WHEN artifact_type_id = " + TSK_WEB_HISTORY.getTypeID() + " THEN 1 "
                + "                 ELSE 0 "
                + "               END) AS totalVisits,"
                + "           SUM(CASE "
                + "                 WHEN artifact_type_id = " + TSK_WEB_HISTORY.getTypeID() + " AND"
                + "                      date BETWEEN " + sixtyDaysAgo.getEpochSecond() + " AND " + currentTime.getEpochSecond() + " THEN 1 "
                + "                 ELSE 0 "
                + "               END) AS last60,"
                + "           MAX(data_source_obj_id) AS dataSource "
                + "FROM blackboard_artifacts"
                + "     JOIN (" + domainsTable + ") AS domains_table"
                + "       ON artifact_id = parent_artifact_id "
                + // Add the data source where clause here if present.
                ((dataSourceWhereClause != null) ? "WHERE " + dataSourceWhereClause + " " : "")
                + "GROUP BY " + groupByClause;

        final SleuthkitCase caseDb = key.getSleuthkitCase();
        final CaseDbAccessManager dbManager = caseDb.getCaseDbAccessManager();

        final DomainCallback domainCallback = new DomainCallback(caseDb);
        dbManager.select(domainsQuery, domainCallback);

        if (domainCallback.getSQLException() != null) {
            throw domainCallback.getSQLException();
        }

        if (domainCallback.getTskCoreException() != null) {
            throw domainCallback.getTskCoreException();
        }

        if (domainCallback.getInterruptedException() != null) {
            throw domainCallback.getInterruptedException();
        }

        return domainCallback.getResultDomains();
    }

    /**
     * A utility method to transform filters into the necessary SQL statements
     * for the domainsTable query. The complexity of that query requires this
     * transformation process to be conditional. The date time filter is a good
     * example of the type of conditional handling that follows in the method
     * below. If no dateTime filter is supplied, then in order for the query to
     * be correct, an additional clause needs to be added in.
     *
     * @param filters The list of filters to apply create the where clause from.
     *
     * @return The whereClause and havingClause as a pair. These methods are one
     *         to stress that these clauses are tightly coupled.
     */
    Pair<String, String> createWhereAndHavingClause(List<AbstractFilter> filters) {
        final StringJoiner whereClause = new StringJoiner(" OR ");
        final StringJoiner havingClause = new StringJoiner(" AND ");

        String artifactTypeFilter = null;
        boolean hasDateTimeFilter = false;

        for (AbstractFilter filter : filters) {
            if (filter instanceof ArtifactTypeFilter) {
                artifactTypeFilter = filter.getWhereClause();
            } else if (!(filter instanceof DataSourceFilter) && !filter.useAlternateFilter()) {
                if (filter instanceof ArtifactDateRangeFilter) {
                    hasDateTimeFilter = true;
                }

                whereClause.add("(" + filter.getWhereClause() + ")");
                havingClause.add("SUM(CASE WHEN " + filter.getWhereClause() + " THEN 1 ELSE 0 END) > 0");
            }
        }

        if (!hasDateTimeFilter) {
            whereClause.add(ArtifactDateRangeFilter.createAttributeTypeClause());
        }

        String domainAttributeFilter = "attribute_type_id = " + TSK_DOMAIN.getTypeID()
                + " AND value_text <> ''";

        whereClause.add("(" + domainAttributeFilter + ")");
        havingClause.add("SUM(CASE WHEN " + domainAttributeFilter + " THEN 1 ELSE 0 END) > 0");

        return Pair.of(
                whereClause.toString() + ((artifactTypeFilter != null) ? " AND (" + artifactTypeFilter + ")" : ""),
                havingClause.toString()
        );
    }

    /**
     * Callback to handle the result set of the domain query. This callback is
     * responsible for mapping result set rows into ResultDomain objects for
     * display.
     */
    private class DomainCallback implements CaseDbAccessQueryCallback {

        private final List<Result> resultDomains;
        private final SleuthkitCase skc;
        private SQLException sqlCause;
        private TskCoreException coreCause;
        private InterruptedException interruptedException;

        private final Set<String> bannedDomains = new HashSet<String>() {
            {
                add("localhost");
                add("127.0.0.1");
            }
        };

        /**
         * Construct a new DomainCallback object.
         *
         * @param skc The case database for the query being performed.
         */
        private DomainCallback(SleuthkitCase skc) {
            this.resultDomains = new ArrayList<>();
            this.skc = skc;
        }

        @Override
        public void process(ResultSet resultSet) {
            try {
                resultSet.setFetchSize(500);

                while (resultSet.next()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }

                    String domain = resultSet.getString("domain");

                    if (bannedDomains.contains(domain)) {
                        // Skip banned domains
                        // Domain names are lowercased in the SQL query
                        continue;
                    }

                    Long activityStart = resultSet.getLong("activity_start");
                    if (resultSet.wasNull()) {
                        activityStart = null;
                    }
                    Long activityEnd = resultSet.getLong("activity_end");
                    if (resultSet.wasNull()) {
                        activityEnd = null;
                    }
                    Long filesDownloaded = resultSet.getLong("fileDownloads");
                    if (resultSet.wasNull()) {
                        filesDownloaded = null;
                    }
                    Long totalVisits = resultSet.getLong("totalVisits");
                    if (resultSet.wasNull()) {
                        totalVisits = null;
                    }

                    Long visitsInLast60 = resultSet.getLong("last60");
                    if (resultSet.wasNull()) {
                        visitsInLast60 = null;
                    }
                    Long dataSourceID = resultSet.getLong("dataSource");

                    Content dataSource = skc.getContentById(dataSourceID);

                    resultDomains.add(new ResultDomain(domain, activityStart,
                            activityEnd, totalVisits, visitsInLast60, filesDownloaded, dataSource));
                }
            } catch (SQLException ex) {
                this.sqlCause = ex;
            } catch (TskCoreException ex) {
                this.coreCause = ex;
            } catch (InterruptedException ex) {
                this.interruptedException = ex;
            }
        }

        /**
         * Get the list of Result object for the domains which were in the
         * search results.
         *
         * @return The list of Result object for the domains which were in the
         *         search results.
         */
        private List<Result> getResultDomains() {
            return Collections.unmodifiableList(this.resultDomains);
        }

        /**
         * Get the SQLEception in an exception occurred.
         *
         * @return The SQLEception in an exception occurred.
         */
        private SQLException getSQLException() {
            return this.sqlCause;
        }

        /**
         * Get the TskCoreException if a SQL exception occurred.
         *
         * @return The TskCoreException if a tsk core exception occurred.
         */
        private TskCoreException getTskCoreException() {
            return this.coreCause;
        }

        /**
         * Get the interrupted exception if the processing thread was
         * interrupted.
         *
         * @return The interrupted exception or null if none was thrown.
         */
        private InterruptedException getInterruptedException() {
            return this.interruptedException;
        }
    }
}
