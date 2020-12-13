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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;

/**
 * Provides summary information about user activity in a datasource. At this
 * time, the data being provided for domains is fictitious and is done as a
 * placeholder.
 */
public class UserActivitySummary implements DefaultArtifactUpdateGovernor {

    private static final BlackboardArtifact.Type TYPE_DEVICE_ATTACHED = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED);
    private static final BlackboardArtifact.Type TYPE_WEB_HISTORY = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WEB_HISTORY);

    private static final BlackboardAttribute.Type TYPE_DATETIME = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME);
    private static final BlackboardAttribute.Type TYPE_DATETIME_ACCESSED = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED);
    private static final BlackboardAttribute.Type TYPE_DEVICE_ID = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_ID);
    private static final BlackboardAttribute.Type TYPE_DEVICE_MAKE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE);
    private static final BlackboardAttribute.Type TYPE_DEVICE_MODEL = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL);
    private static final BlackboardAttribute.Type TYPE_MESSAGE_TYPE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE);
    private static final BlackboardAttribute.Type TYPE_TEXT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_TEXT);
    private static final BlackboardAttribute.Type TYPE_DATETIME_RCVD = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD);
    private static final BlackboardAttribute.Type TYPE_DATETIME_SENT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_SENT);
    private static final BlackboardAttribute.Type TYPE_DATETIME_START = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_START);
    private static final BlackboardAttribute.Type TYPE_DATETIME_END = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_END);
    private static final BlackboardAttribute.Type TYPE_DOMAIN = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DOMAIN);

    private static final Comparator<TopAccountResult> TOP_ACCOUNT_RESULT_DATE_COMPARE = (a, b) -> a.getLastAccess().compareTo(b.getLastAccess());
    private static final Comparator<TopWebSearchResult> TOP_WEBSEARCH_RESULT_DATE_COMPARE = (a, b) -> a.getDateAccessed().compareTo(b.getDateAccessed());

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID(),
            ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(),
            ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
            ARTIFACT_TYPE.TSK_CALLLOG.getTypeID(),
            ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID(),
            ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()
    ));

    private static final Set<String> DEVICE_EXCLUDE_LIST = new HashSet<>(Arrays.asList("ROOT_HUB", "ROOT_HUB20"));
    private static final Set<String> DOMAIN_EXCLUDE_LIST = new HashSet<>(Arrays.asList("127.0.0.1", "LOCALHOST"));

    private static final long MS_PER_DAY = 1000 * 60 * 60 * 24;
    private static final long DOMAIN_WINDOW_DAYS = 30;
    private static final long DOMAIN_WINDOW_MS = DOMAIN_WINDOW_DAYS * MS_PER_DAY;

    private final SleuthkitCaseProvider caseProvider;
    private final TextTranslationService translationService;
    private final java.util.logging.Logger logger;

    /**
     * Main constructor.
     */
    public UserActivitySummary() {
        this(SleuthkitCaseProvider.DEFAULT, TextTranslationService.getInstance(),
                org.sleuthkit.autopsy.coreutils.Logger.getLogger(UserActivitySummary.class.getName()));
    }

    /**
     * Main constructor with external dependencies specified. This constructor
     * is designed with unit testing in mind since mocked dependencies can be
     * utilized.
     *
     * @param provider           The object providing the current SleuthkitCase.
     * @param translationService The translation service.
     * @param logger             The logger to use.
     */
    public UserActivitySummary(
            SleuthkitCaseProvider provider,
            TextTranslationService translationService,
            java.util.logging.Logger logger) {

        this.caseProvider = provider;
        this.translationService = translationService;
        this.logger = logger;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return ARTIFACT_UPDATE_TYPE_IDS;
    }

    /**
     * Throws an IllegalArgumentException if count <= 0.
     *
     * @param count The count being checked.
     */
    private void assertValidCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
    }

    /**
     * Gets a list of recent domains based on the datasource.
     *
     * @param dataSource The datasource to query for recent domains.
     * @param count      The max count of items to return.
     *
     * @return The list of items retrieved from the database.
     *
     * @throws InterruptedException
     */
    public List<TopDomainsResult> getRecentDomains(DataSource dataSource, int count) throws TskCoreException, SleuthkitCaseProviderException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        Pair<Long, Map<String, List<Long>>> mostRecentAndGroups = getDomainGroupsAndMostRecent(dataSource);
        // if no recent domains, return accordingly
        if (mostRecentAndGroups.getKey() == null || mostRecentAndGroups.getValue().size() == 0) {
            return Collections.emptyList();
        }

        final long mostRecentMs = mostRecentAndGroups.getLeft();
        Map<String, List<Long>> groups = mostRecentAndGroups.getRight();

        return groups.entrySet().stream()
                .map(entry -> getDomainsResult(entry.getKey(), entry.getValue(), mostRecentMs))
                .filter(result -> result != null)
                // sort by number of visit times in those 30 days (max to min)
                .sorted((a, b) -> -Long.compare(a.getVisitTimes(), b.getVisitTimes()))
                // limit the result number to the parameter provided
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Creates a TopDomainsResult from data or null if no visit date exists
     * within DOMAIN_WINDOW_MS of mostRecentMs.
     *
     * @param domain       The domain.
     * @param visits       The number of visits.
     * @param mostRecentMs The most recent visit of any domain.
     *
     * @return The TopDomainsResult or null if no visits to this domain within
     *         30 days of mostRecentMs.
     */
    private TopDomainsResult getDomainsResult(String domain, List<Long> visits, long mostRecentMs) {
        long visitCount = 0;
        Long thisMostRecentMs = null;

        for (Long visitMs : visits) {
            // make sure that visit is within window of mostRecentMS; otherwise skip it.
            if (visitMs + DOMAIN_WINDOW_MS < mostRecentMs) {
                continue;
            }

            // if visit is within window, increment the count and get most recent
            visitCount++;
            thisMostRecentMs = getMax(thisMostRecentMs, visitMs);
        }

        // if there are no visits within the window, return null
        if (visitCount <= 0 || thisMostRecentMs == null) {
            return null;
        } else {
            // create a top domain result with the domain, count, and most recent visit date
            return new TopDomainsResult(domain, visitCount, new Date(thisMostRecentMs));
        }
    }

    /**
     * Queries TSK_WEB_HISTORY artifacts and returning the latest web history
     * date accessed and a mapping of domains to all of their visits.
     *
     * @param dataSource The datasource.
     *
     * @return A tuple where the first value is the latest web history accessed
     *         date in milliseconds and the second value maps normalized
     *         (lowercase; trimmed) domain names to when those domains were
     *         visited.
     *
     * @throws TskCoreException
     * @throws SleuthkitCaseProviderException
     */
    private Pair<Long, Map<String, List<Long>>> getDomainGroupsAndMostRecent(DataSource dataSource) throws TskCoreException, SleuthkitCaseProviderException {
        List<BlackboardArtifact> artifacts = DataSourceInfoUtilities.getArtifacts(caseProvider.get(), TYPE_WEB_HISTORY,
                dataSource, TYPE_DATETIME_ACCESSED, DataSourceInfoUtilities.SortOrder.DESCENDING, 0);

        Long mostRecentMs = null;
        Map<String, List<Long>> domainVisits = new HashMap<>();

        for (BlackboardArtifact art : artifacts) {
            Long artifactDateSecs = DataSourceInfoUtilities.getLongOrNull(art, TYPE_DATETIME_ACCESSED);
            String domain = DataSourceInfoUtilities.getStringOrNull(art, TYPE_DOMAIN);

            // if there isn't a last access date or domain for this artifact, it can be ignored.
            // Also, ignore the loopback address.
            if (artifactDateSecs == null || StringUtils.isBlank(domain) || DOMAIN_EXCLUDE_LIST.contains(domain.toUpperCase().trim())) {
                continue;
            }

            Long artifactDateMs = artifactDateSecs * 1000;

            // update the most recent visit date overall
            mostRecentMs = getMax(mostRecentMs, artifactDateMs);

            //Normalize the domain to lower case.
            domain = domain.toLowerCase().trim();

            // add this visit date to the list of dates for the domain
            List<Long> domainVisitList = domainVisits.get(domain);
            if (domainVisitList == null) {
                domainVisitList = new ArrayList<>();
                domainVisits.put(domain, domainVisitList);
            }

            domainVisitList.add(artifactDateMs);
        }

        return Pair.of(mostRecentMs, domainVisits);
    }

    /**
     * Returns the maximum value given two longs handling possible null values.
     *
     * @param num1 The first number.
     * @param num2 The second number.
     *
     * @return The maximum non-null number or null if both numbers are null.
     */
    private static Long getMax(Long num1, Long num2) {
        if (num1 == null) {
            return num2;
        } else if (num2 == null) {
            return num1;
        } else {
            return num2 > num1 ? num2 : num1;
        }
    }

    /**
     * Attempts to obtain a web search result record from a blackboard artifact.
     *
     * @param artifact The artifact.
     *
     * @return The TopWebSearchResult or null if the search string or date
     *         accessed cannot be determined.
     */
    private static TopWebSearchResult getWebSearchResult(BlackboardArtifact artifact) {
        String searchString = DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_TEXT);
        Date dateAccessed = DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME_ACCESSED);
        return (StringUtils.isNotBlank(searchString) && dateAccessed != null)
                ? new TopWebSearchResult(searchString, dateAccessed)
                : null;
    }

    /**
     * Retrieves most recent web searches by most recent date grouped by search
     * term.
     *
     * @param dataSource The data source.
     * @param count      The maximum number of records to be shown (must be >
     *                   0).
     *
     * @return The list of most recent web searches where most recent search
     *         appears first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<TopWebSearchResult> getMostRecentWebSearches(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        // get the artifacts
        List<BlackboardArtifact> webSearchArtifacts = caseProvider.get().getBlackboard()
                .getArtifacts(ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID(), dataSource.getId());

        // group by search string (case insensitive)
        Collection<List<TopWebSearchResult>> resultGroups = webSearchArtifacts
                .stream()
                // get items where search string and date is not null
                .map(UserActivitySummary::getWebSearchResult)
                // remove null records
                .filter(result -> result != null)
                // get these messages grouped by search to string
                .collect(Collectors.groupingBy((result) -> result.getSearchString().toUpperCase()))
                .values();

        // get the most recent date for each search term
        List<TopWebSearchResult> results = resultGroups
                .stream()
                // get the most recent access per search type
                .map((list) -> list.stream().max(TOP_WEBSEARCH_RESULT_DATE_COMPARE).get())
                // get most recent searches first
                .sorted(TOP_WEBSEARCH_RESULT_DATE_COMPARE.reversed())
                .limit(count)
                // get as list
                .collect(Collectors.toList());

        // get translation if possible
        if (translationService.hasProvider()) {
            for (TopWebSearchResult result : results) {
                result.setTranslatedResult(getTranslationOrNull(result.getSearchString()));
            }
        }

        return results;
    }

    /**
     * Return the translation of the original text if possible and differs from
     * the original. Otherwise, return null.
     *
     * @param original The original text.
     *
     * @return The translated text or null if no translation can be determined
     *         or exists.
     */
    private String getTranslationOrNull(String original) {
        if (!translationService.hasProvider() || StringUtils.isBlank(original)) {
            return null;
        }

        String translated = null;
        try {
            translated = translationService.translate(original);
        } catch (NoServiceProviderException | TranslationException ex) {
            logger.log(Level.WARNING, String.format("There was an error translating text: '%s'", original), ex);
        }

        // if there is no translation or the translation is the same as the original, return null.
        if (StringUtils.isBlank(translated)
                || translated.toUpperCase().trim().equals(original.toUpperCase().trim())) {

            return null;
        }

        return translated;
    }

    /**
     * Retrieves most recent devices used by most recent date attached.
     *
     * @param dataSource The data source.
     * @param count      The maximum number of records to be shown (must be >
     *                   0).
     *
     * @return The list of most recent devices attached where most recent device
     *         attached appears first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<TopDeviceAttachedResult> getRecentDevices(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        return DataSourceInfoUtilities.getArtifacts(caseProvider.get(), TYPE_DEVICE_ATTACHED,
                dataSource, TYPE_DATETIME, DataSourceInfoUtilities.SortOrder.DESCENDING, 0)
                .stream()
                .map(artifact -> {
                    return new TopDeviceAttachedResult(
                            DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_ID),
                            DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME),
                            DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_MAKE),
                            DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_MODEL)
                    );
                })
                // remove Root Hub identifier
                .filter(result -> {
                    return result.getDeviceModel() == null
                            || !DEVICE_EXCLUDE_LIST.contains(result.getDeviceModel().trim().toUpperCase());
                })
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Obtains a TopAccountResult from a TSK_MESSAGE blackboard artifact.
     *
     * @param artifact The artifact.
     *
     * @return The TopAccountResult or null if the account type or message date
     *         cannot be determined.
     */
    private static TopAccountResult getMessageAccountResult(BlackboardArtifact artifact) {
        String type = DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_MESSAGE_TYPE);
        Date date = DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME);
        return (StringUtils.isNotBlank(type) && date != null)
                ? new TopAccountResult(type, date)
                : null;
    }

    /**
     * Obtains a TopAccountResult from a blackboard artifact. The date is
     * maximum of any found dates for attribute types provided.
     *
     * @param artifact    The artifact.
     * @param messageType The type of message this is.
     * @param dateAttrs   The date attribute types.
     *
     * @return The TopAccountResult or null if the account type or max date are
     *         not provided.
     */
    private static TopAccountResult getAccountResult(BlackboardArtifact artifact, String messageType, BlackboardAttribute.Type... dateAttrs) {
        String type = messageType;

        Date latestDate = null;
        if (dateAttrs != null) {
            latestDate = Stream.of(dateAttrs)
                    .map((attr) -> DataSourceInfoUtilities.getDateOrNull(artifact, attr))
                    .filter((date) -> date != null)
                    .max((a, b) -> a.compareTo(b))
                    .orElse(null);
        }

        return (StringUtils.isNotBlank(type) && latestDate != null)
                ? new TopAccountResult(type, latestDate)
                : null;
    }

    /**
     * Retrieves most recent account used by most recent date for a message
     * sent.
     *
     * @param dataSource The data source.
     * @param count      The maximum number of records to be shown (must be >
     *                   0).
     *
     * @return The list of most recent accounts used where the most recent
     *         account by last message sent occurs first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    @Messages({
        "DataSourceUserActivitySummary_getRecentAccounts_emailMessage=Email Message",
        "DataSourceUserActivitySummary_getRecentAccounts_calllogMessage=Call Log",})
    public List<TopAccountResult> getRecentAccounts(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        Stream<TopAccountResult> messageResults = caseProvider.get().getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(), dataSource.getId())
                .stream()
                .map((art) -> getMessageAccountResult(art));

        Stream<TopAccountResult> emailResults = caseProvider.get().getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(), dataSource.getId())
                .stream()
                .map((art) -> {
                    return getAccountResult(
                            art,
                            Bundle.DataSourceUserActivitySummary_getRecentAccounts_emailMessage(),
                            TYPE_DATETIME_RCVD,
                            TYPE_DATETIME_SENT);
                });

        Stream<TopAccountResult> calllogResults = caseProvider.get().getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_CALLLOG.getTypeID(), dataSource.getId())
                .stream()
                .map((art) -> {
                    return getAccountResult(
                            art,
                            Bundle.DataSourceUserActivitySummary_getRecentAccounts_calllogMessage(),
                            TYPE_DATETIME_START,
                            TYPE_DATETIME_END);
                });

        Stream<TopAccountResult> allResults = Stream.concat(messageResults, Stream.concat(emailResults, calllogResults));

        // get them grouped by account type        
        Collection<List<TopAccountResult>> groupedResults = allResults
                // remove null records
                .filter(result -> result != null)
                // get these messages grouped by account type
                .collect(Collectors.groupingBy(TopAccountResult::getAccountType))
                .values();

        // get account type sorted by most recent date
        return groupedResults
                .stream()
                // get the most recent access per account type
                .map((accountGroup) -> accountGroup.stream().max(TOP_ACCOUNT_RESULT_DATE_COMPARE).get())
                // get most recent accounts accessed
                .sorted(TOP_ACCOUNT_RESULT_DATE_COMPARE.reversed())
                // limit to count
                .limit(count)
                // get as list
                .collect(Collectors.toList());
    }

    /**
     * Object containing information about a web search artifact.
     */
    public static class TopWebSearchResult {

        private final String searchString;
        private final Date dateAccessed;
        private String translatedResult;

        /**
         * Main constructor.
         *
         * @param searchString The search string.
         * @param dateAccessed The latest date searched.
         */
        public TopWebSearchResult(String searchString, Date dateAccessed) {
            this.searchString = searchString;
            this.dateAccessed = dateAccessed;
        }

        /**
         * @return The translated result if one was determined.
         */
        public String getTranslatedResult() {
            return translatedResult;
        }

        /**
         * Sets the translated result for this web search.
         *
         * @param translatedResult The translated result.
         */
        public void setTranslatedResult(String translatedResult) {
            this.translatedResult = translatedResult;
        }

        /**
         * @return The search string.
         */
        public String getSearchString() {
            return searchString;
        }

        /**
         * @return The date for the search.
         */
        public Date getDateAccessed() {
            return dateAccessed;
        }
    }

    /**
     * A record of a device attached.
     */
    public static class TopDeviceAttachedResult {

        private final String deviceId;
        private final Date dateAccessed;
        private final String deviceMake;
        private final String deviceModel;

        /**
         * Main constructor.
         *
         * @param deviceId     The device id.
         * @param dateAccessed The date last attached.
         * @param deviceMake   The device make.
         * @param deviceModel  The device model.
         */
        public TopDeviceAttachedResult(String deviceId, Date dateAccessed, String deviceMake, String deviceModel) {
            this.deviceId = deviceId;
            this.dateAccessed = dateAccessed;
            this.deviceMake = deviceMake;
            this.deviceModel = deviceModel;
        }

        /**
         * @return The device id.
         */
        public String getDeviceId() {
            return deviceId;
        }

        /**
         * @return The date last attached.
         */
        public Date getDateAccessed() {
            return dateAccessed;
        }

        /**
         * @return The device make.
         */
        public String getDeviceMake() {
            return deviceMake;
        }

        /**
         * @return The device model.
         */
        public String getDeviceModel() {
            return deviceModel;
        }
    }

    /**
     * A record of an account and the last time it was used determined by
     * messages.
     */
    public static class TopAccountResult {

        private final String accountType;
        private final Date lastAccess;

        /**
         * Main constructor.
         *
         * @param accountType The account type.
         * @param lastAccess  The date the account was last accessed.
         */
        public TopAccountResult(String accountType, Date lastAccess) {
            this.accountType = accountType;
            this.lastAccess = lastAccess;
        }

        /**
         * @return The account type.
         */
        public String getAccountType() {
            return accountType;
        }

        /**
         * @return The date the account was last accessed.
         */
        public Date getLastAccess() {
            return lastAccess;
        }
    }

    /**
     * Describes a result of a program run on a datasource.
     */
    public static class TopDomainsResult {

        private final String domain;
        private final Long visitTimes;
        private final Date lastVisit;

        /**
         * Describes a top domain result.
         *
         * @param domain     The domain.
         * @param visitTimes The number of times it was visited.
         * @param lastVisit  The date of the last visit.
         */
        public TopDomainsResult(String domain, Long visitTimes, Date lastVisit) {
            this.domain = domain;
            this.visitTimes = visitTimes;
            this.lastVisit = lastVisit;
        }

        /**
         * @return The domain for the result.
         */
        public String getDomain() {
            return domain;
        }

        /**
         * @return The number of times this site is visited.
         */
        public Long getVisitTimes() {
            return visitTimes;
        }

        /**
         * @return The date of the last visit.
         */
        public Date getLastVisit() {
            return lastVisit;
        }
    }
}
