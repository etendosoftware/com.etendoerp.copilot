package com.etendoerp.copilot.util;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import java.util.List;

public class ToolsUtil {
  private ToolsUtil() {
  }

  public static JSONArray getToolSet(CopilotApp app) throws OBException, JSONException {
    // we will read from /copilot the tools if we can
    JSONArray result = new JSONArray();
    OBCriteria<CopilotAppTool> appToolCrit = OBDal.getInstance()
        .createCriteria(CopilotAppTool.class);
    appToolCrit.add(Restrictions.eq(CopilotAppTool.PROPERTY_COPILOTAPP, app));
    List<CopilotAppTool> appToolsList = appToolCrit.list();
    if (appToolsList.isEmpty()) {
      return result;
    }
    //make petition to /copilot
    for (CopilotAppTool appTool : appToolsList) {
      CopilotTool erpTool = appTool.getCopilotTool();
      String toolInfo = erpTool.getJsonStructure();
      if (toolInfo != null) {

        result.put(new JSONObject(toolInfo));
      }
    }

    return result;
  }

}
