package org.egov.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.kafka.Producer;
import org.egov.repository.MusterRollRepository;
import org.egov.tracer.model.CustomException;
import org.egov.util.MdmsUtil;
import org.egov.util.MusterRollServiceUtil;
import org.egov.validator.MusterRollValidator;
import org.egov.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MusterRollService {

    @Autowired
    private MusterRollValidator musterRollValidator;

    @Autowired
    private EnrichmentService enrichmentService;

    @Autowired
    private CalculationService calculationService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private Producer producer;

    @Autowired
    private MusterRollServiceConfiguration serviceConfiguration;

    @Autowired
    private MusterRollRepository musterRollRepository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MdmsUtil mdmsUtils;

    @Autowired
    private MusterRollServiceUtil musterRollServiceUtil;

    @Autowired
    private MusterRollServiceConfiguration config;

    @Autowired
    private RestTemplate restTemplate;


    /**
     * Calculates the per day attendance , attendance aggregate from startDate to endDate
     * and provides it as an estimate.
     * Note: This will NOT create muster roll and NOT store the details
     *
     * @param musterRollRequest
     * @return
     */
    public MusterRollRequest estimateMusterRoll(MusterRollRequest musterRollRequest) {
        log.info("MusterRollService::estimateMusterRoll");

        musterRollValidator.validateEstimateMusterRoll(musterRollRequest);
        enrichmentService.enrichMusterRollOnEstimate(musterRollRequest);
        calculationService.createAttendance(musterRollRequest,false);
        return musterRollRequest;
    }


    /**
     * Calculates the per day attendance , attendance aggregate from startDate to endDate for all the
     * individuals of the provided attendance register.
     * Creates muster roll and stores the details.
     *
     * @param musterRollRequest
     * @return
     */
    public MusterRollRequest createMusterRoll(MusterRollRequest musterRollRequest) {
        log.info("MusterRollService::createMusterRoll");

        checkMusterRollExists(musterRollRequest.getMusterRoll());
        musterRollValidator.validateCreateMusterRoll(musterRollRequest);
        enrichmentService.enrichMusterRollOnCreate(musterRollRequest);
        calculationService.createAttendance(musterRollRequest,true);
        workflowService.updateWorkflowStatus(musterRollRequest);

        producer.push(serviceConfiguration.getSaveMusterRollTopic(), musterRollRequest);
        return musterRollRequest;
    }

    /**
     * Search muster roll based on the given search criteria - tenantId, musterId, musterRollNumber, startDate, endDate, status
     * and musterRollStatus
     *
     * @param requestInfoWrapper
     * @param searchCriteria
     * @return
     */
    public List<MusterRoll> searchMusterRolls(RequestInfoWrapper requestInfoWrapper, MusterRollSearchCriteria searchCriteria) {
        log.info("MusterRollService::searchMusterRolls");

        musterRollValidator.validateSearchMuster(requestInfoWrapper,searchCriteria);
        enrichmentService.enrichSearchRequest(searchCriteria);

        List<Role> roles = requestInfoWrapper.getRequestInfo().getUserInfo().getRoles();
        boolean isFilterRequired = roles.stream()
                .anyMatch(role -> role.getCode().equalsIgnoreCase("ORG_ADMIN") || role.getCode().equalsIgnoreCase("ORG_STAFF"));
        //Fetch the attendance registers that belong to the user and then fetch the musters that belongs to the user
        List<String> registerIds = new ArrayList<>();
        if (isFilterRequired) {
            registerIds = fetchAttendanceRegistersOfUser(requestInfoWrapper.getRequestInfo(),searchCriteria);
        }

        List<MusterRoll> musterRollList = musterRollRepository.getMusterRoll(searchCriteria,registerIds);
        List<MusterRoll> filteredMusterRollList = musterRollList;

        //apply the limit and offset
        if (filteredMusterRollList != null && !musterRollServiceUtil.isTenantBasedSearch(searchCriteria)) {
            filteredMusterRollList = applyLimitAndOffset(searchCriteria,filteredMusterRollList);
        }
        return filteredMusterRollList;
    }

    /**
     * Updates the totalAttendance, skill details (if modified) and re-calculates the attendance (if 'computeAttendance' is true)
     *
     * @param musterRollRequest
     * @return
     */
    public MusterRollRequest updateMusterRoll(MusterRollRequest musterRollRequest) {
        log.info("MusterRollService::updateMusterRoll");

        MusterRoll existingMusterRoll = fetchExistingMusterRoll(musterRollRequest.getMusterRoll());
        log.info("MusterRollService::updateMusterRoll::update request for musterRollNumber::"+existingMusterRoll.getMusterRollNumber());

        boolean isComputeAttendance = isComputeAttendance(musterRollRequest.getMusterRoll());
        musterRollValidator.validateUpdateMusterRoll(musterRollRequest,existingMusterRoll,isComputeAttendance);

        //fetch MDMS data for muster - skill level
        String rootTenantId = existingMusterRoll.getTenantId().split("\\.")[0];
        Object mdmsData = mdmsUtils.mDMSCallMuster(musterRollRequest, rootTenantId);

        enrichmentService.enrichMusterRollOnUpdate(musterRollRequest,existingMusterRoll,mdmsData);
        //If 'computeAttendance' flag is true, re-calculate the attendance from attendanceLogs and update
        if (isComputeAttendance) {
            calculationService.updateAttendance(musterRollRequest,mdmsData);
        }
        workflowService.updateWorkflowStatus(musterRollRequest);

        producer.push(serviceConfiguration.getUpdateMusterRollTopic(), musterRollRequest);
        return musterRollRequest;
    }

    /**
     * Check if the muster roll already exists for the same registerId, startDate and endDate to avoid duplicate muster creation
     * @param musterRoll
     */
    private void checkMusterRollExists(MusterRoll musterRoll) {
        MusterRollSearchCriteria searchCriteria = MusterRollSearchCriteria.builder().tenantId(musterRoll.getTenantId())
                .registerId(musterRoll.getRegisterId()).fromDate(musterRoll.getStartDate())
                .toDate(musterRoll.getEndDate()).build();
        List<MusterRoll> musterRolls = musterRollRepository.getMusterRoll(searchCriteria,null);
        if (!CollectionUtils.isEmpty(musterRolls)) {
            StringBuffer exceptionMessage = new StringBuffer();
            exceptionMessage.append("Muster roll already exists for the register - ");
            exceptionMessage.append(musterRoll.getRegisterId());
            exceptionMessage.append(" with startDate - ");
            exceptionMessage.append(musterRoll.getStartDate());
            exceptionMessage.append(" and endDate - ");
            exceptionMessage.append(musterRoll.getEndDate());
            throw new CustomException("DUPLICATE_MUSTER_ROLL",exceptionMessage.toString());
        }
    }

    /**
     * Fetch the existing muster roll from DB else throw error
     * @param musterRoll
     * @return
     */
    private MusterRoll fetchExistingMusterRoll(MusterRoll musterRoll) {
        List<String> ids = new ArrayList<>();
        ids.add(musterRoll.getId());
        MusterRollSearchCriteria searchCriteria = MusterRollSearchCriteria.builder().ids(ids).tenantId(musterRoll.getTenantId()).build();
        List<MusterRoll> musterRolls = musterRollRepository.getMusterRoll(searchCriteria,null);
        if (CollectionUtils.isEmpty(musterRolls)) {
            throw new CustomException("NO_MATCH_FOUND","Invalid Muster roll id - "+musterRoll.getId());
        }
        MusterRoll existingMusterRoll = musterRolls.get(0);
        return existingMusterRoll;
    }

    /**
     * Check if the 'computeAttendance' flag is true
     * @param musterRoll
     * @return
     */
    private boolean isComputeAttendance (MusterRoll musterRoll) {
       if (musterRoll.getAdditionalDetails() != null) {
           try {
               JsonNode node = mapper.readTree(mapper.writeValueAsString(musterRoll.getAdditionalDetails()));
               if (node.findValue("computeAttendance") != null && StringUtils.isNotBlank(node.findValue("computeAttendance").textValue())) {
                   String value = node.findValue("computeAttendance").textValue();
                   return BooleanUtils.toBoolean(value);
               }
           } catch (IOException e) {
               log.info("MusterRollService::isComputeAttendance::Failed to parse additionalDetail object from request"+e);
               throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object from request on update");
           }
       }
       return false;
    }

    /**
     * Fetch the registerIds that the user belongs to ( if role is org_staff or org_admin)
     * @param requestInfo
     * @return
     */
    private List<String>  fetchAttendanceRegistersOfUser(RequestInfo requestInfo, MusterRollSearchCriteria searchCriteria) {
        String id = requestInfo.getUserInfo().getUuid();

        StringBuilder uri = new StringBuilder();
        uri.append(config.getAttendanceLogHost()).append(config.getAttendanceRegisterEndpoint());
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri.toString())
                .queryParam("tenantId",searchCriteria.getTenantId())
                .queryParam("status", Status.ACTIVE);
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();

        AttendanceRegisterResponse attendanceRegisterResponse = null;
        log.info("MusterRollService::fetchAttendanceRegistersOfUser::call attendance register search with tenantId::"+searchCriteria.getTenantId()
                +"::for user::"+id);

        try {
            attendanceRegisterResponse  = restTemplate.postForObject(uriBuilder.toUriString(),requestInfoWrapper,AttendanceRegisterResponse.class);
        }  catch (HttpClientErrorException | HttpServerErrorException httpClientOrServerExc) {
            log.error("MusterRollService::fetchAttendanceRegistersOfUser::Error thrown from attendance register service::"+httpClientOrServerExc.getStatusCode());
            throw new CustomException("ATTENDANCE_REGISTER_SERVICE_EXCEPTION","Error thrown from attendance register service::"+httpClientOrServerExc.getStatusCode());
        }

        if (attendanceRegisterResponse == null || CollectionUtils.isEmpty(attendanceRegisterResponse.getAttendanceRegister())) {
            throw new CustomException("NO_DATA_FOUND","No Attendance registers found for the user. So no muster created for the register");
        }

        List<AttendanceRegister> attendanceRegisters = attendanceRegisterResponse.getAttendanceRegister();
        List<String> registerIds = attendanceRegisters.stream()
                .map(register -> register.getId())
                .collect(Collectors.toList());

        return  registerIds;
    }

    /**
     * Applies the limit and offset
     * @param searchCriteria
     * @param musterRollList
     * @return
     */
    private List<MusterRoll> applyLimitAndOffset(MusterRollSearchCriteria searchCriteria, List<MusterRoll> musterRollList) {
        List<MusterRoll> musterRolls = musterRollList.stream()
                        .skip(searchCriteria.getOffset())  // offset
                        .limit(searchCriteria.getLimit()) // limit
                        .collect(Collectors.toList());
        return musterRolls;
    }
}
