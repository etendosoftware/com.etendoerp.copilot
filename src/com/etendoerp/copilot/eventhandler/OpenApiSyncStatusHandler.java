/*************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF  ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and  limitations under the License.
 * All portions are Copyright (C) 2021-2025 Futit Services S.L.
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 ************************************************************************/
package com.etendoerp.copilot.eventhandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.event.Observes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;
import com.etendoerp.copilot.util.CopilotAppInfoUtils;
import com.etendoerp.copilot.util.CopilotUtils;
import com.etendoerp.openapi.data.OpenApiFlow;
import com.etendoerp.openapi.data.OpenApiFlowPoint;
import com.etendoerp.openapi.data.OpenAPIRequest;

/**
 * Handles synchronization status updates for agents when OpenAPI Flows,
 * OpenAPI Requests, or FlowPoints are created, updated, or deleted.
 * <p>
 * The relationship chain is:
 * OpenApiFlow -> CopilotFile (via openAPIFlow FK) -> CopilotAppSource -> CopilotApp (Agent)
 * OpenAPIRequest -> OpenApiFlowPoint -> OpenApiFlow -> same chain
 * OpenApiFlowPoint -> OpenApiFlow -> same chain
 */
public class OpenApiSyncStatusHandler extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(OpenApiFlow.class),
      ModelProvider.getInstance().getEntity(OpenAPIRequest.class),
      ModelProvider.getInstance().getEntity(OpenApiFlowPoint.class)
  };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    markAffectedAgents(event.getTargetInstance(), "updated");
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    markAffectedAgents(event.getTargetInstance(), "saved");
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    markAffectedAgents(event.getTargetInstance(), "deleted");
  }

  /**
   * Given the modified entity instance, resolves the affected OpenApiFlow(s)
   * and marks all agents linked through CopilotFile -> CopilotAppSource as pending sync.
   */
  private void markAffectedAgents(Object targetInstance, String action) {
    Set<OpenApiFlow> affectedFlows = resolveAffectedFlows(targetInstance);
    for (OpenApiFlow flow : affectedFlows) {
      markAgentsByFlow(flow, action);
    }
  }

  /**
   * Resolves the set of OpenApiFlow records affected by the changed entity.
   */
  private Set<OpenApiFlow> resolveAffectedFlows(Object targetInstance) {
    Set<OpenApiFlow> flows = new HashSet<>();

    if (targetInstance == null) {
      return flows;
    }

    if (targetInstance instanceof OpenApiFlow) {
      flows.add((OpenApiFlow) targetInstance);

    } else if (targetInstance instanceof OpenApiFlowPoint) {
      OpenApiFlowPoint flowPoint = (OpenApiFlowPoint) targetInstance;
      OpenApiFlow flow = flowPoint.getEtapiOpenapiFlow();
      if (flow != null) {
        flows.add(flow);
      }

    } else if (targetInstance instanceof OpenAPIRequest) {
      OpenAPIRequest request = (OpenAPIRequest) targetInstance;
      OBCriteria<OpenApiFlowPoint> fpCriteria = OBDal.getInstance()
          .createCriteria(OpenApiFlowPoint.class);
      fpCriteria.add(
          Restrictions.eq(OpenApiFlowPoint.PROPERTY_ETAPIOPENAPIREQ, request));
      for (OpenApiFlowPoint fp : fpCriteria.list()) {
        OpenApiFlow flow = fp.getEtapiOpenapiFlow();
        if (flow != null) {
          flows.add(flow);
        }
      }
    }

    return flows;
  }

  /**
   * Given an OpenApiFlow, finds all CopilotFile records that reference it,
   * then all CopilotAppSource records that reference those files,
   * and marks each parent CopilotApp as pending synchronization.
   */
  private void markAgentsByFlow(OpenApiFlow flow, String action) {
    OBCriteria<CopilotFile> fileCriteria = OBDal.getInstance()
        .createCriteria(CopilotFile.class);
    fileCriteria.add(Restrictions.eq(CopilotFile.PROPERTY_OPENAPIFLOW, flow));

    List<CopilotFile> files = fileCriteria.list();
    for (CopilotFile file : files) {
      OBCriteria<CopilotAppSource> sourceCriteria = OBDal.getInstance()
          .createCriteria(CopilotAppSource.class);
      sourceCriteria.add(Restrictions.eq(CopilotAppSource.PROPERTY_FILE, file));

      for (CopilotAppSource appSource : sourceCriteria.list()) {
        CopilotApp agent = appSource.getEtcopApp();
        CopilotAppInfoUtils.markAsPendingSynchronization(agent);
        CopilotUtils.logIfDebug(
            "OpenAPI entity " + action + ": sync status of agent '"
                + agent.getName() + "' changed to PS");
      }
    }
  }
}
