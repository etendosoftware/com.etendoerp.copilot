package com.etendoerp.copilot.util;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppTool;
import com.etendoerp.copilot.data.CopilotTool;

/*
 * This class is used to get the tools for the app.
 */
public class ToolsUtil {

  private ToolsUtil() {
    // Private constructor to prevent instantiation
  }

  /**
   * Get the tools for the app
   *
   * @param app
   * @throws OBException
   * @throws JSONException
   */
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
      if (toolInfo != null && !StringUtils.equals("{}", toolInfo)) {
        result.put(new JSONObject(toolInfo));
      } else {
        JSONObject jsonTool = new JSONObject();
        jsonTool.put("type", "function");
        JSONObject jsonFunc = new JSONObject();
        jsonFunc.put("name", erpTool.getValue());
        jsonTool.put("function", jsonFunc);
        result.put(jsonTool);
      }
    }
    return result;
  }

}
