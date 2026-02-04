package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotModel;

/**
 * Utility class for managing CopilotModel instances.
 * <p>
 * This class provides methods for retrieving, synchronizing, and updating CopilotModel instances
 * in the database. It includes methods for downloading datasets, parsing XML, and applying
 * security filters.
 */
public class CopilotModelUtils {
  private static final Logger log = LogManager.getLogger(CopilotModelUtils.class);

  /**
   * Private constructor to prevent instantiation of the utility class.
   * <p>
   * This constructor is intentionally declared private to ensure that the
   * `CopilotModelUtils` class cannot be instantiated. This class is designed
   * to provide static utility methods and does not require instantiation.
   */
  private CopilotModelUtils() {
    // Private constructor to prevent instantiation
  }

  /**
   * Retrieves the default CopilotModel based on the provided provider.
   * <p>
   * This method creates a criteria query to find the default CopilotModel.
   * If the provider is not empty, it adds a restriction to filter by the provider.
   * It also adds a restriction to filter by the default property and orders the results by creation date.
   * The method returns the first result of the query.
   *
   * @param provider
   *     The provider to filter the CopilotModel by.
   * @return The default CopilotModel for the given provider.
   */
  static CopilotModel getDefaultModel(String provider) {
    CopilotModel result = null;
    OBCriteria<CopilotModel> modelCriteria = OBDal.getInstance().createCriteria(CopilotModel.class);
    if (StringUtils.isNotEmpty(provider)) {
      modelCriteria.add(Restrictions.eq(CopilotModel.PROPERTY_PROVIDER, provider));
    }
    modelCriteria.add(Restrictions.or(Restrictions.eq(CopilotModel.PROPERTY_DEFAULT, true),
        Restrictions.eq(CopilotModel.PROPERTY_DEFAULTOVERRIDE, true)));
    modelCriteria.addOrderBy(CopilotModel.PROPERTY_CREATIONDATE, true);

    List<CopilotModel> mdList = modelCriteria.list();
    //search the first with "default override" true
    result = mdList.stream().filter(CopilotModel::isDefaultOverride).findFirst().orElse(null);
    if (result == null) {
      //search the first with "default" true
      result = mdList.stream().filter(CopilotModel::isDefault).findFirst().orElse(null);
    }
    return result;
  }


  /**
   * Logs a debug message if debug logging is enabled.
   * <p>
   * This method checks if the logger is in debug mode and, if so, logs the provided message.
   * It is a utility method to conditionally log debug messages, avoiding unnecessary string
   * concatenation or processing when debug logging is disabled.
   *
   * @param string
   *     The message to be logged if debug logging is enabled.
   */
  private static void logIfDebug(String string) {
    if (log.isDebugEnabled()) {
      log.debug(string);
    }
  }


  /**
   * Retrieves the provider of the given CopilotApp instance.
   * <p>
   * This method checks if the provided CopilotApp instance and its model are not null,
   * and if the model's provider is not empty. If these conditions are met, it returns the provider.
   * Otherwise, it returns "openai" as the default provider.
   * If an exception occurs, it throws an OBException with the message of the exception.
   *
   * @param app
   *     The CopilotApp instance for which the provider is to be retrieved.
   * @return The provider of the CopilotApp instance, or "openai" if the provider is not set.
   * @throws OBException
   *     If an error occurs while retrieving the provider.
   */
  public static String getProvider(CopilotApp app) {
    try {
      if (app != null && app.getModel() != null && StringUtils.isNotEmpty(app.getModel().getProvider())) {
        return app.getModel().getProvider();
      }
      return PROVIDER_OPENAI;
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
      if (app.getModel() != null) {
        return app.getModel().getSearchkey();
      }
      String model = getAppModel(app, getProvider(app));
      CopilotUtils.logIfDebug("Selected model: " + model);
      return model;
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
      String resultModel = null;
      CopilotModel model = app.getModel();
      if (model != null && model.getSearchkey() != null) {
        resultModel = model.getSearchkey();
        log.debug("Model selected in app: {}", resultModel);
        return resultModel;
      }
      model = getDefaultModel(provider);
      if (model == null) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETCOP_NoDefaultModel"), provider));
      }
      resultModel = model.getSearchkey();
      logIfDebug("Model selected by Default: " + resultModel);
      return resultModel;
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }
}
