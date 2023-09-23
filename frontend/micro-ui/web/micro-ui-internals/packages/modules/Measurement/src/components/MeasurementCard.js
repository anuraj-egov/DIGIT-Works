import { EmployeeModuleCard, CaseIcon, MuktaHomeIcon } from "@egovernments/digit-ui-react-components";
import React, { useState } from "react";


import { useTranslation } from "react-i18next";

const MeasurementCard = () => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();


  const propsForModuleCard = {
    Icon: <CaseIcon />,
    moduleName: t("WORKS_MEASUREMENT"),
    kpis: [
      // {
      //   //Pass Count Value from Inbox API here
      //   count: "-",
      //   label: t("MEASUREMENT_INBOX"),
      //   link: `/${window?.contextPath}/employee/measurement/inbox`,
      // }
    ],
    links: [
      // {
      //   label: t("ACTION_TEST_CREATE_MEASUREMENT"),
      //   link: `/${window?.contextPath}/employee/measurement/create-measurement`,
      //   roles: ["EMPLOYEE"],
      // }

      {
        label: t("MB_CREATE"),
        link: `/${window?.contextPath}/employee/measurement/create`,
      },
      {
        label: t("MB_INBOX"),
        link: `/${window?.contextPath}/employee/measurement/inbox`,
      },
      {
        label: t("ACTION_TEST_CREATE_MEASUREMENT"),
        link: `/${window?.contextPath}/employee/measurement/create?tenantId=pg.citya&workOrderNumber=WO/2023-24/000784`,
        roles: ["EMPLOYEE"],
      },
      {
        label: t("MB_SEARCH"),
        link: `/${window?.contextPath}/employee/measurement/searchplain`,
      },
      {
        label: t("MB_UPDATE"),
        link: `/${window?.contextPath}/employee/measurement/update?tenantId=pg.citya&workOrderNumber=WO/2023-24/000784&mbNumber=MB/2023-24/000507`,
      }
    ],
  };
  return <EmployeeModuleCard {...propsForModuleCard} />;
};

export default MeasurementCard;