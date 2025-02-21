package com.etendoerp.copilot.eventhandler;

import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;

public class AutoRegistrationAssistantHandler extends BaseActionHandler {

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject result = new JSONObject();
    try {
      JSONObject jsonData = new JSONObject(content);
      String appId = jsonData.getString("appId");

      CopilotApp currentApp = OBDal.getInstance().get(CopilotApp.class, appId);

      OBContext currentOBContext = OBContext.getOBContext();
      Role currentRole = currentOBContext.getRole();

      OBCriteria<CopilotRoleApp> crit = OBDal.getInstance().createCriteria(CopilotRoleApp.class);
      crit.add(Restrictions.eq(CopilotRoleApp.PROPERTY_COPILOTAPP, currentApp));
      crit.add(Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, currentRole));
      crit.setMaxResults(1);
      if (crit.uniqueResult() != null) {
        return result;
      }

      CopilotRoleApp newRoleApp = OBProvider.getInstance().get(CopilotRoleApp.class);
      newRoleApp.setCopilotApp(currentApp);
      newRoleApp.setRole(currentRole);

      OBDal.getInstance().save(newRoleApp);
      OBDal.getInstance().flush();

    } catch (Exception e) {
      try {
        result.put("success", false);
        result.put("message", "Error: " + e.getMessage());
      } catch (Exception ignore) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_RoleNotAdded"));
      }
    }
    return result;
  }
}
