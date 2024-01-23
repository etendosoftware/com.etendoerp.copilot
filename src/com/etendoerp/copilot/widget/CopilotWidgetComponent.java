package com.etendoerp.copilot.widget;

import org.openbravo.client.kernel.BaseTemplateComponent;
import org.openbravo.dal.core.OBContext;

public class CopilotWidgetComponent extends BaseTemplateComponent {

  public String getName() {
    return OBContext.getOBContext().getUser().getName();
  }

}