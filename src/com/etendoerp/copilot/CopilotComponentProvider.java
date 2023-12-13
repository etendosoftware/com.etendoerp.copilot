package com.etendoerp.copilot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.client.kernel.BaseComponentProvider;
import org.openbravo.client.kernel.Component;
import org.openbravo.client.kernel.ComponentProvider;

@ApplicationScoped
@ComponentProvider.Qualifier(CopilotComponentProvider.CopilotProvider)
public class CopilotComponentProvider extends BaseComponentProvider {
  public static final String CopilotProvider = "ETCOP_Provider";

  @Override
  public Component getComponent(String componentId, Map<String, Object> parameters) {
    return null;
  }

  @Override
  public List<ComponentResource> getGlobalComponentResources() {
    final List<ComponentResource> globalResources = new ArrayList<ComponentResource>();
    globalResources.add(createStyleSheetResource("web/styles/style.css", false));
    globalResources.add(createStaticResource("web/js/copilot.js", false));
    
    return globalResources;
  }

}