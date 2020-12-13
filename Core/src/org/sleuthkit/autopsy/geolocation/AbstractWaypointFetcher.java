/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.geolocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javafx.util.Pair;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationParseResult;
import org.sleuthkit.autopsy.geolocation.datamodel.Track;
import org.sleuthkit.autopsy.geolocation.datamodel.Waypoint;
import org.sleuthkit.autopsy.geolocation.datamodel.WaypointBuilder;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * The business logic for filtering waypoints.
 */
abstract class AbstractWaypointFetcher implements WaypointBuilder.WaypointFilterQueryCallBack {

    private static final Logger logger = Logger.getLogger(AbstractWaypointFetcher.class.getName());

    private final GeoFilterPanel.GeoFilter filters;

    /**
     * Constructs the Waypoint Runner
     *
     * @param filters
     */
    AbstractWaypointFetcher(GeoFilterPanel.GeoFilter filters) {
        this.filters = filters;
    }

    /**
     * Gets the waypoints based in the current GeoFilter.
     *
     * This function kicks off a process that will send with
     * handleFilteredWaypointSet being called. Subclasses must implement
     * handleFitleredWayoiintSet to get the final results.
     *
     * @throws GeoLocationDataException
     */
    void getWaypoints() throws GeoLocationDataException {
        Case currentCase = Case.getCurrentCase();
        WaypointBuilder.getAllWaypoints(currentCase.getSleuthkitCase(),
                filters.getDataSources(),
                filters.getArtifactTypes(),
                filters.showAllWaypoints(),
                filters.getMostRecentNumDays(),
                filters.showWaypointsWithoutTimeStamp(),
                this);

    }


    /**
     * Called after all of the MapWaypoints are created from all of the
     * TSK_GPS_XXX objects.
     * 
     * @param mapWaypoints List of filtered MapWaypoints.
     * @param tracks The tracks that were successfully parsed.
     * @param wasEntirelySuccessful True if no errors occurred while processing.
     */
    abstract void handleFilteredWaypointSet(Set<MapWaypoint> mapWaypoints, List<Set<MapWaypoint>> tracks, 
        boolean wasEntirelySuccessful);

    @Override
    public void process(GeoLocationParseResult<Waypoint> waypointResults) {
        GeoLocationParseResult<Track> trackResults = null;
        if (filters.getArtifactTypes().contains(ARTIFACT_TYPE.TSK_GPS_TRACK)) {
            try {
                trackResults = Track.getTracks(Case.getCurrentCase().getSleuthkitCase(), filters.getDataSources());
            } catch (GeoLocationDataException ex) {
                logger.log(Level.WARNING, "Exception thrown while retrieving list of Tracks", ex);
            }
        }
                
        Pair<List<Waypoint>, List<List<Waypoint>>> waypointsAndTracks = createWaypointList(
            waypointResults.getItems(),
            (trackResults == null) ? new ArrayList<Track>() : trackResults.getItems());

        
        final Set<MapWaypoint> pointSet = MapWaypoint.getWaypoints(waypointsAndTracks.getKey());
        final List<Set<MapWaypoint>> trackSets = new ArrayList<>();
        for (List<Waypoint> t : waypointsAndTracks.getValue()) {
            trackSets.add(MapWaypoint.getWaypoints(t));
        }

        handleFilteredWaypointSet(
            pointSet, trackSets, 
            (trackResults == null || trackResults.isSuccessfullyParsed()) && waypointResults.isSuccessfullyParsed());
    }

    /**
     * Returns a complete list of waypoints including the tracks. Takes into
     * account the current filters and includes waypoints as approprate.
     *
     * @param waypoints List of waypoints
     * @param tracks    List of tracks
     *
     * @return A list of waypoints including the tracks based on the current
     *         filters.
     */
    private Pair<List<Waypoint>, List<List<Waypoint>>> createWaypointList(List<Waypoint> waypoints, List<Track> tracks) {
        final List<Waypoint> completeList = new ArrayList<>();
        List<List<Waypoint>> filteredTracks = new ArrayList<>();

        if (tracks != null) {
            Long timeRangeEnd;
            Long timeRangeStart;
            if (!filters.showAllWaypoints()) {
                // Figure out what the most recent time is given the filtered
                // waypoints and the tracks.
                timeRangeEnd = getMostRecent(waypoints, tracks);
                timeRangeStart = timeRangeEnd - (86400 * filters.getMostRecentNumDays());

                completeList.addAll(getWaypointsInRange(timeRangeStart, timeRangeEnd, waypoints));
                
                filteredTracks = getTracksInRange(timeRangeStart, timeRangeEnd, tracks);
                for (List<Waypoint> filteredTrack : filteredTracks) {
                    completeList.addAll(filteredTrack);
                }
            } else {
                completeList.addAll(waypoints);
                for (Track track : tracks) {
                    completeList.addAll(track.getPath());
                    filteredTracks.add(track.getPath());
                }
            }
        } else {
            completeList.addAll(waypoints);
        }
        return new Pair<>(completeList, filteredTracks);
    }

    /**
     * Return a list of waypoints that fall into the given time range.
     *
     * @param timeRangeStart start timestamp of range (seconds from java epoch)
     * @param timeRangeEnd   start timestamp of range (seconds from java epoch)
     * @param waypoints      List of waypoints to filter.
     *
     * @return A list of waypoints that fall into the time range.
     */
    private List<Waypoint> getWaypointsInRange(Long timeRangeStart, Long timeRangeEnd, List<Waypoint> waypoints) {
        List<Waypoint> completeList = new ArrayList<>();
        // Add all of the waypoints that fix into the time range.
        if (waypoints != null) {
            for (Waypoint point : waypoints) {
                Long time = point.getTimestamp();
                if ((time == null && filters.showWaypointsWithoutTimeStamp())
                        || (time != null && (time >= timeRangeStart && time <= timeRangeEnd))) {

                    completeList.add(point);
                }
            }
        }
        return completeList;
    }

    /**
     * Return a list of lists of waypoints from the given tracks that fall into 
     * the given time range. The track start time will used for determining if
     * the whole track falls into the range.
     *
     * @param timeRangeStart start timestamp of range (seconds from java epoch)
     * @param timeRangeEnd   start timestamp of range (seconds from java epoch)
     * @param tracks         Track list.
     *
     * @return A list of lists of waypoints corresponding to belong to tracks
     *         that exist within the time range.
     */
    private List<List<Waypoint>> getTracksInRange(Long timeRangeStart, Long timeRangeEnd, List<Track> tracks) {
        List<List<Waypoint>> ret = new ArrayList<>();
        if (tracks != null) {
            for (Track track : tracks) {
                Long trackTime = track.getStartTime();

                if ((trackTime == null && filters.showWaypointsWithoutTimeStamp())
                        || (trackTime != null && (trackTime >= timeRangeStart && trackTime <= timeRangeEnd))) {
                    ret.add(track.getPath());
                }
            }
        }
        return ret;
    }

    /**
     * Find the latest time stamp in the given list of waypoints.
     *
     * @param points List of Waypoints, required.
     *
     * @return The latest time stamp (seconds from java epoch)
     */
    private Long findMostRecentTimestamp(List<Waypoint> points) {

        Long mostRecent = null;

        for (Waypoint point : points) {
            if (mostRecent == null) {
                mostRecent = point.getTimestamp();
            } else {
                mostRecent = Math.max(mostRecent, point.getTimestamp());
            }
        }

        return mostRecent;
    }

    /**
     * Find the latest time stamp in the given list of tracks.
     *
     * @param tracks List of Waypoints, required.
     *
     * @return The latest time stamp (seconds from java epoch)
     */
    private Long findMostRecentTracks(List<Track> tracks) {
        Long mostRecent = null;

        for (Track track : tracks) {
            if (mostRecent == null) {
                mostRecent = track.getStartTime();
            } else if (track.getStartTime() != null) {
                mostRecent = Math.max(mostRecent, track.getStartTime());
            }
        }

        return mostRecent;
    }

    /**
     * Returns the "most recent" timestamp amount the list of waypoints and
     * track points.
     *
     * @param points List of Waypoints
     * @param tracks List of Tracks
     *
     * @return Latest time stamp (seconds from java epoch)
     */
    private Long getMostRecent(List<Waypoint> points, List<Track> tracks) {
        Long waypointMostRecent = findMostRecentTimestamp(points);
        Long trackMostRecent = findMostRecentTracks(tracks);

        if (waypointMostRecent != null && trackMostRecent != null) {
            return Math.max(waypointMostRecent, trackMostRecent);
        } else if (waypointMostRecent == null && trackMostRecent != null) {
            return trackMostRecent;
        } else if (waypointMostRecent != null && trackMostRecent == null) {
            return waypointMostRecent;
        }

        return -1L;
    }
}
