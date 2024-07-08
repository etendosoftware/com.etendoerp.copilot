package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_GEMINI;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_GEMINI_VALUE;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI;
import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI_VALUE;

import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.purchase.ws.CopilotWSServlet;

public class CopilotUtils {


  public static final HashMap<String, String> PROVIDER_MAP_CODE_NAME = buildProviderCodeMap();
  public static final HashMap<String, String> PROVIDER_MAP_CODE_DEFAULT_PROP = buildProviderCodeDefaulMap();

  private static HashMap<String, String> buildProviderCodeMap() {
    HashMap<String, String> map = new HashMap<>();
    map.put(PROVIDER_OPENAI_VALUE, PROVIDER_OPENAI);
    map.put(PROVIDER_GEMINI_VALUE, PROVIDER_GEMINI);
    return map;
  }

  private static HashMap<String, String> buildProviderCodeDefaulMap() {
    HashMap<String, String> map = new HashMap<>();
    map.put(PROVIDER_OPENAI, "ETCOP_DefaultModelOpenAI");
    map.put(PROVIDER_GEMINI, "ETCOP_DefaultModelGemini");
    return map;
  }


  /**
   * This method is used to get the provider of a given CopilotApp instance.
   * It first checks if the CopilotApp instance and its provider are not null. If they are not null, it returns the provider of the CopilotApp instance.
   * If the CopilotApp instance or its provider is null, it retrieves the default provider from the system preferences.
   * The default provider is retrieved using the preference key "ETCOP_DefaultProvider".
   * The method uses the OBContext to get the current client, organization, user, and role for retrieving the preference value.
   * The provider code is then retrieved from the PROVIDER_MAP_CODE_NAME map using the provider code as the key.
   * If an exception occurs while executing any of the above steps, it throws an OBException with the message of the exception.
   *
   * @param app
   *     The CopilotApp instance for which the provider is to be retrieved.
   * @return The provider of the CopilotApp instance, or the default provider if the CopilotApp instance or its provider is null.
   * @throws OBException
   *     If an error occurs while retrieving the provider.
   */
  public static String getProvider(CopilotApp app) {
    try {
      String provCode = null;
      if (app != null && StringUtils.isNotEmpty(app.getProvider())) {
        provCode = app.getProvider();
      } else {
        OBContext context = OBContext.getOBContext();
        provCode = Preferences.getPreferenceValue("ETCOP_DefaultProvider", true, context.getCurrentClient(),
            context.getCurrentOrganization(), context.getUser(), context.getRole(), null);
      }

      return PROVIDER_MAP_CODE_NAME.get(provCode);
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }

  /**
   * This method is used to get the model of a given CopilotApp instance.
   * It calls the overloaded getAppModel method with the CopilotApp instance and its provider as arguments.
   * The provider of the CopilotApp instance is retrieved using the getProvider method.
   * If an exception occurs while getting the model or the provider, it throws an OBException with the message of the exception.
   *
   * @param app
   *     The CopilotApp instance for which the model is to be retrieved.
   * @return The model of the CopilotApp instance.
   * @throws OBException
   *     If an error occurs while retrieving the model or the provider.
   */
  public static String getAppModel(CopilotApp app) {
    try {
      return getAppModel(app, getProvider(app));
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }


  /**
   * This method is used to get the model of a given CopilotApp instance and a provider.
   * It first checks if the model and its search key of the CopilotApp instance are not null. If they are not null, it returns the search key of the model.
   * If the model or its search key is null, it retrieves the provider of the CopilotApp instance if the provider argument is null.
   * The provider of the CopilotApp instance is retrieved using the getProvider method.
   * It then checks if the provider is in the PROVIDER_MAP_CODE_DEFAULT_PROP map, and sets the preference accordingly.
   * If the provider is not in the map, it throws an OBException with a formatted message.
   * The preference value is then retrieved using the Preferences.getPreferenceValue method with the preference, the current client, organization, user, and role.
   * If an exception occurs while executing any of the above steps, it throws an OBException with the message of the exception.
   *
   * @param app
   *     The CopilotApp instance for which the model is to be retrieved.
   * @param provider
   *     The provider for which the model is to be retrieved.
   * @return The model of the CopilotApp instance, or the preference value if the model or its search key is null.
   * @throws OBException
   *     If an error occurs while retrieving the model, the provider, or the preference value.
   */
  public static String getAppModel(CopilotApp app, String provider) {
    try {
      String current_provider = provider;
      if (app.getModel() != null && app.getModel().getSearchkey() != null) {
        return app.getModel().getSearchkey();
      }
      // if the provider is not indicated we will read the provider of the app ( or the default if not set)
      OBContext context = OBContext.getOBContext();
      if (current_provider == null) {
        current_provider = getProvider(app);
      }
      String preference;
      if (!PROVIDER_MAP_CODE_DEFAULT_PROP.containsKey(current_provider)) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_MissingModel"),
            app.getName()));
      }
      preference = PROVIDER_MAP_CODE_DEFAULT_PROP.get(current_provider);

      return Preferences.getPreferenceValue(preference, true, context.getCurrentClient(),
          context.getCurrentOrganization(), context.getUser(), context.getRole(), null);

    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }
}
