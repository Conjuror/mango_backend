package org.mozilla.msrp.platform.mission;

import lombok.extern.log4j.Log4j2;
import org.mozilla.msrp.platform.common.auth.Auth;
import org.mozilla.msrp.platform.common.auth.JwtHelper;
import org.mozilla.msrp.platform.metrics.Metrics;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@Log4j2
@RestController
public class MissionController {

    private static final String PINGS_SEPARATER = ",";

    @Inject
    private MissionService missionService;

    @Inject
    private JwtHelper jwtHelper;

    /**
     * Fetch user requested missions, and aggregate data that is needed by client
     * <p>
     * Request
     * GET /group/{groupId}/missions
     * <p>
     * Response
     * [
     * {
     * "mid": "wsF1OHt3CrtrGbAyT8xo",
     * "title": "...",
     * "description": "...",
     * "endpoint": "/mission_daily/wsF1OHt3CrtrGbAyT8xo"
     * },
     * {
     * "mid": "5quMPsVLh0wims22pstL",
     * "title": "...",
     * "description": "...",
     * "endpoint": "/mission_one_shot/wsF1OHt3CrtrGbAyT8xo"
     * },
     * ]
     *
     * @param groupId id for audience group
     * @return Client-facing mission list
     */
    @RequestMapping(value = "/api/v1/group/{groupId}/missions", method = GET)
    public ResponseEntity<MissionListResponse> getMissionByGroupId(
            @PathVariable("groupId") String groupId,
            @RequestParam("tz") String timezone,
            @RequestAttribute("uid") String uid,
            @RequestAttribute("locale") Locale locale) {

        ZoneId zone = createZone(timezone);
        if (zone == null) {
            return ResponseEntity.badRequest()
                    .body(new MissionListResponse.Error("unsupported timezone"));
        }

        List<MissionListItem> missions = missionService.getMissionsByGroupId(uid, groupId, zone, locale);

        return ResponseEntity.ok(new MissionListResponse.Success(missions));
    }

    /**
     * Create missions
     * <p>
     * Request body
     * {
     * "missions": [
     * {
     * "mid": "wsF1OHt3CrtrGbAyT8xo",
     * "title": "...",
     * "description": "...",
     * "endpoint": "/mission_daily/wsF1OHt3CrtrGbAyT8xo"
     * },
     * {
     * "mid": "5quMPsVLh0wims22pstL",
     * "title": "...",
     * "description": "...",
     * "endpoint": "/mission_one_shot/wsF1OHt3CrtrGbAyT8xo"
     * }
     * ]
     * }
     * <p>
     * Response
     * [
     * {
     * "mid": "wsF1OHt3CrtrGbAyT8xo",
     * "name": "...",
     * "description": "..."
     * },
     * {
     * "mid": "5quMPsVLh0wims22pstL",
     * "name": "...",
     * "description": "..."
     * }
     * ]
     *
     * @param request data needed to create mission
     * @return response with created mission in body
     */
    private ResponseEntity<MissionCreateResponse> createMission(
            @RequestBody MissionCreateRequest request) {

        MissionCreateResult result = missionService.createMissions(request.getMissions());

        if (result instanceof MissionCreateResult.Success) {
            MissionCreateResult.Success successResult = (MissionCreateResult.Success) result;
            return ResponseEntity.ok(new MissionCreateResponse.Success(successResult.getResults()));

        } else {
            MissionCreateResult.Error errorResult = (MissionCreateResult.Error) result;
            return ResponseEntity.badRequest()
                    .body(new MissionCreateResponse.Error(errorResult.getResults()));
        }
    }

    @RequestMapping(value = "/api/v1/admin/missions", method = POST)
    public ResponseEntity createMissionForm(
            @RequestParam String token,
            @RequestParam String missionName,
            @RequestParam String missionType,
            @RequestParam String rewardType,
            @RequestParam int totalDays,
            @RequestParam int joinQuota,
            @RequestParam String titleId,
            @RequestParam String descriptionId,
            @RequestParam String imageUrl,
            @RequestParam String startDate,
            @RequestParam String joinStartDate,
            @RequestParam String joinEndDate,
            @RequestParam String expiredDate,
            @RequestParam String redeemEndDate,
            @RequestParam String pings,
            @RequestParam(value = "messages[]") String[] messages,
            @RequestParam int minVersion) {

        Auth verify = jwtHelper.verify(token);
        if (verify == null || !JwtHelper.ROLE_MSRP_ADMIN.equals(verify.getRole())) {
            return new ResponseEntity("No Permission", HttpStatus.UNAUTHORIZED);
        }
        MissionCreateRequest request = new MissionCreateRequest();
        ArrayList<MissionCreateData> missionList = new ArrayList<>();
        List<String> pingList = null;
        HashMap<String, Object> params = new HashMap<>();
        params.put("totalDays", totalDays);
        params.put("message", Arrays.asList(messages));

        final String[] split = pings.split(PINGS_SEPARATER);
        if (split.length > 0) {
            pingList = Arrays.asList(split);
        } else {
            pingList = new ArrayList<>();
            pingList.add(pings);
        }

        MissionCreateData mCreated = new MissionCreateData(
                missionName,
                titleId,
                descriptionId,
                missionType,
                pingList,
                startDate,
                joinStartDate,
                joinEndDate,
                expiredDate,
                redeemEndDate,
                minVersion,
                params,
                rewardType,
                joinQuota,
                imageUrl
        );
        missionList.add(mCreated);
        request.missions = missionList;
        return createMission(request);
    }

    /**
     * Create missions
     * <p>
     * Request body
     * {
     * "missions": [
     * {
     * "endpoint": "/mission_daily/ooKv67x8MFzsIr44PfBz"
     * },
     * {
     * "endpoint": "/mission_one_shot/VjTcDnJBHEqlzVjkj8zb"
     * }
     * ]
     * }
     * <p>
     * Response
     * [
     * {
     * "endpoint": "/mission_daily/ooKv67x8MFzsIr44PfBz"
     * },
     * {
     * "endpoint": "/mission_one_shot/VjTcDnJBHEqlzVjkj8zb"
     * }
     * ]
     *
     * @param groupId group id
     * @param request contains missions to be grouped
     * @return response with created mission in body
     */
    @RequestMapping(value = "/api/v1/group/{groupId}/missions")
    public ResponseEntity<List<MissionReferenceDoc>> groupMissions(@PathVariable("groupId") String groupId,
                                                                   @RequestBody MissionGroupRequest request) {
        List<MissionReferenceDoc> body = missionService.groupMissions(groupId, request.getMissions());
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    /**
     * Join mission
     * <p>
     * Response
     * {
     * "mid": "",
     * "status": "join"
     * }
     *
     * @param missionType mission type, which is also the name of the mission collection
     * @param mid         mission id
     * @return response indicating mission id and it's new join status
     */
    @RequestMapping(value = "/api/v1/missions/{missionType}/{mid}", method = POST)
    public ResponseEntity<MissionJoinResponse> joinMission(
            @PathVariable("missionType") String missionType,
            @PathVariable("mid") String mid,
            @RequestParam("tz") String timezone,
            @RequestAttribute("uid") String uid,
            @RequestAttribute("locale") Locale locale) {

        ZoneId zone = createZone(timezone);
        if (zone == null) {
            return ResponseEntity
                    .badRequest()
                    .body(new MissionJoinResponse.Error(
                            "unsupported timezone",
                            JoinFailedReason.Unknown.getCode()
                    ));
        }

        MissionJoinResult result = missionService.joinMission(uid, missionType, mid, zone, locale);
        if (result instanceof MissionJoinResult.Success) {
            Metrics.event(Metrics.EVENT_MISSION_JOINED, "mid:" + mid);
            MissionJoinResult.Success successResult = (MissionJoinResult.Success) result;
            return ResponseEntity.ok(new MissionJoinResponse.Success(successResult));

        } else {
            MissionJoinResult.Error errorResult = (MissionJoinResult.Error) result;
            return ResponseEntity.status(errorResult.getCode())
                    .body(new MissionJoinResponse.Error(errorResult.getMessage(), errorResult.getReason().getCode()));
        }
    }

    @RequestMapping(value = "/api/v1/missions/{missionType}/{mid}", method = DELETE)
    public ResponseEntity<MissionQuitResponse> quitMission(
            @PathVariable("missionType") String missionType,
            @PathVariable("mid") String mid,
            @RequestAttribute("uid") String uid) {
        return missionService.quitMission(uid, missionType, mid).toEntityResponse();
    }

    /**
     * Check-in missions that are interest in the give ping
     * <p>
     * Response (daily mission as example) // same as /api/v1/group/{groupId}/missions
     * <p>
     * Invalid timezone
     * {
     * "error": "unsupported timezone"
     * }
     *
     * @param ping     ping used in Firebase Analytics
     * @param timezone user's timezone in
     */
    @RequestMapping(value = "/api/v1/ping/{ping}", method = PUT)
    public ResponseEntity<MissionCheckInResponse> checkInMissionsByPing(
            @PathVariable("ping") String ping,
            @RequestParam("tz") String timezone,
            @RequestAttribute("uid") String uid,
            @RequestAttribute("locale") Locale locale) {

        ZoneId zone = createZone(timezone);
        if (zone == null) {
            return ResponseEntity.badRequest()
                    .body(new MissionCheckInResponse.Error("unsupported timezone"));
        }

        log.info("ping={}, timezone={}", ping, zone);

        List<MissionListItem> results = missionService.checkInMissions(uid, ping, zone, locale);

        return ResponseEntity.ok(new MissionCheckInResponse.Success(results));
    }

    private ZoneId createZone(String timezone) {
        try {
            return ZoneId.of(timezone);

        } catch (DateTimeException e) {
            log.info("unsupported timezone=" + timezone);
            return null;
        }
    }
}
