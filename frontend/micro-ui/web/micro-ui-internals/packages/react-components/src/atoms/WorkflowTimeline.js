import React, { Fragment,useState,useEffect } from 'react'
import { Loader } from "./Loader"
import CardSectionHeader from './CardSectionHeader';
import { CheckPoint, ConnectingCheckPoints } from './ConnectingCheckPoints';
import BreakLine from './BreakLine';
import { useTranslation } from "react-i18next";
import TLCaption from './TLCaption';

function OpenImage(imageSource, index, thumbnailsToShow) {
    window.open(thumbnailsToShow?.fullImage?.[0], "_blank");
}

const WorkflowTimeline = ({ businessService, tenantId,applicationNo, timelineStatusPrefix="ESTIMATE_" ,statusAttribute="status", ...props}) => {
    const [additionalComment,setAdditionalComment] = useState(false)
    //for testing from url these 2 lines of code are kept here
    // const { estimateNumber } = Digit.Hooks.useQueryParams();
    // applicationNo = applicationNo? applicationNo : estimateNumber 
    const { t } = useTranslation();
    const getTimelineCaptions = (checkpoint) => {
        
        const caption = {
            date: `${Digit.DateUtils?.ConvertTimestampToDate(checkpoint.auditDetails.lastModifiedEpoch)} ${Digit.DateUtils?.ConvertEpochToTimeInHours(
                checkpoint.auditDetails.lastModifiedEpoch
            )} ${Digit.DateUtils?.getDayfromTimeStamp(checkpoint.auditDetails.lastModifiedEpoch)}`,
            name: checkpoint?.assignes?.[0]?.name,
            mobileNumber: checkpoint?.assignes?.[0]?.mobileNumber,
            wfComment: checkpoint?.comment ? [checkpoint?.comment] :[],
            additionalComment: additionalComment && checkpoint?.performedAction === "APPROVE",
            thumbnailsToShow: checkpoint?.thumbnailsToShow,
        };

        return <TLCaption data={caption} OpenImage={OpenImage} />;
        
    };

    let workflowDetails = Digit.Hooks.useWorkflowDetailsWorks(
        {
            tenantId: tenantId,
            id: applicationNo,
            moduleCode: businessService,
            config: {
                enabled: true,
                cacheTime: 0
            }
        }
    );

    useEffect(() => {
        if (workflowDetails?.data?.applicationBusinessService === "muster-roll-approval" && workflowDetails?.data?.actionState?.applicationStatus === "APPROVED") {
            setAdditionalComment(true)
        }
    }, [workflowDetails])
    

    
    return (
        <Fragment>
            {workflowDetails?.isLoading && <Loader />}
            { workflowDetails?.data?.timeline?.length > 0 && (
                <React.Fragment>
                    {workflowDetails?.breakLineRequired === undefined ? <BreakLine /> : workflowDetails?.breakLineRequired ? <BreakLine /> : null}
                    {!workflowDetails?.isLoading && (
                        <Fragment>
                            <CardSectionHeader style={{ marginBottom: "16px", marginTop: "32px" }}>
                                {t("WORKS_WORKFLOW_TIMELINE")}
                            </CardSectionHeader>
                            {workflowDetails?.data?.timeline && workflowDetails?.data?.timeline?.length === 1 ? (
                                  <ConnectingCheckPoints>
                                  <React.Fragment>
                                  <CheckPoint
                                    keyValue={0}
                                    isCompleted={true}
                                    //info={checkpoint.comment}
                                    label={t(`${timelineStatusPrefix}${workflowDetails?.data?.timeline[0]?.[statusAttribute]}`)}
                                    customChild={null}
                                    />
                                  </React.Fragment>
                                    <React.Fragment key={1}>
                                        <CheckPoint
                                            isCompleted={false}
                                            label={t(`${timelineStatusPrefix}${workflowDetails?.data?.timeline[0]?.["status"]}`)}
                                            customChild={getTimelineCaptions(workflowDetails?.data?.timeline[0])}
                                        />
                                    </React.Fragment>         
                              </ConnectingCheckPoints> 
                            ) : (
                                <ConnectingCheckPoints>
                                    {workflowDetails?.data?.timeline &&
                                        workflowDetails?.data?.timeline.map((checkpoint, index, arr) => {
                                            return (
                                                <React.Fragment key={index}>
                                                    <CheckPoint
                                                        keyValue={index}
                                                        isCompleted={index === 0}
                                                        //info={checkpoint.comment}
                                                        label={t(
                                                            `${timelineStatusPrefix}${checkpoint?.performedAction === "EDIT" ? `${checkpoint?.performedAction}_ACTION` : checkpoint?.[index!=0?"status":statusAttribute]
                                                            }`
                                                        )}
                                                        customChild={getTimelineCaptions(checkpoint)}
                                                    />
                                                    
                                                </React.Fragment>
                                            );
                                        })}
                                </ConnectingCheckPoints>
                            )}
                        </Fragment>
                    )}
                </React.Fragment>
            )}
        </Fragment>
    )
}

export default WorkflowTimeline