package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
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
    * This method creates a criteria query to find the default CopilotModel. If the {@code provider}
    * parameter is not empty the search is restricted to models for that provider; when {@code provider}
    * is {@code null} no provider filter is applied. Before querying the database this method calls
    * {@link #readOverrideDefaultModel()} — when a system preference override is configured it will be
    * returned immediately (the preference acts as a global override).
    * <p>
    * The criteria then filters by {@code default = true} and orders results by creation date
    * returning the first model found.
    *
    * @param provider The provider to filter the CopilotModel by, or {@code null} to match any provider.
    * @return The default {@link CopilotModel} for the given provider, or {@code null} if none found.
   */
  static CopilotModel getDefaultModel(String provider) {
    CopilotModel result = null;
    result = readOverrideDefaultModel();
    if (result != null) {
      return result;
    }
    OBCriteria<CopilotModel> modelCriteria = OBDal.getInstance().createCriteria(CopilotModel.class);
    if (StringUtils.isNotEmpty(provider)) {
      modelCriteria.add(Restrictions.eq(CopilotModel.PROPERTY_PROVIDER, provider));
    }
    modelCriteria.add(Restrictions.eq(CopilotModel.PROPERTY_DEFAULT, true));
    modelCriteria.addOrderBy(CopilotModel.PROPERTY_CREATIONDATE, true);

    List<CopilotModel> mdList = modelCriteria.list();
    //search the first with "default" true
    result = mdList.stream().filter(CopilotModel::isDefault).findFirst().orElse(null);
    return result;
  }

  /**
    * Reads the preference "ETCOP_DefaultModelOverride" and returns the corresponding
    * {@link CopilotModel} when the preference value is present and well-formed.
    * <p>
    * The preference value must use the format {@code provider/modelId}. When present and valid the
    * method looks up the model by name and provider and returns it — this allows administrators to
    * globally override the default model without changing database records. If the preference is
    * missing, malformed, or the referenced model is not found, the method returns {@code null}
    * (and logs an error if the model key was not found).
    *
    * @return the {@link CopilotModel} specified in the preference, or {@code null} if not applicable
    *     or not found
   */
  private static CopilotModel readOverrideDefaultModel() {
    //if propertie not found or exception return null
    try {
      String overrideModelStr = Preferences.getPreferenceValue(
          "ETCOP_DefaultModelOverride",
          true,
          OBContext.getOBContext().getCurrentClient(),
          OBContext.getOBContext().getCurrentOrganization(),
          OBContext.getOBContext().getUser(),
          OBContext.getOBContext().getRole(),
          null
      );
      // the text has the format provider/modelId
      if (StringUtils.isEmpty(overrideModelStr)) {
        return null;
      }
      String[] parts = overrideModelStr.split("/");
      if (parts.length != 2) {
        return null;
      }
      String prefProvider = parts[0];
      String modelKey = parts[1];

      OBCriteria<CopilotModel> modelCriteria = OBDal.getInstance().createCriteria(CopilotModel.class);
      modelCriteria.add(Restrictions.eq(CopilotModel.PROPERTY_NAME, modelKey));
      modelCriteria.add(Restrictions.eq(CopilotModel.PROPERTY_PROVIDER, prefProvider));
      modelCriteria.setMaxResults(1);
      CopilotModel model = (CopilotModel) modelCriteria.uniqueResult();
      if (model != null) {
        logIfDebug("Overriding default model with model from preference: " + model.getSearchkey());
        return model;
      }
      log.error("No CopilotModel found with id: " + modelKey + " from preference ETCOP_DefaultModelOverride");
    } catch (Exception e) {
      log.debug("Not found override default model preference or error reading it:", e);
    }
    return null;
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
    * Retrieves the provider of the given {@link CopilotApp} instance.
    * <p>
    * If the {@code app} or its model is {@code null} or the model's provider is empty, this method
    * returns the default provider constant {@link com.etendoerp.copilot.util.CopilotConstants#PROVIDER_OPENAI}.
    * Otherwise the model's provider value is returned. Exceptions are wrapped in an {@link OBException}.
    *
    * @param app The {@link CopilotApp} instance for which the provider is to be retrieved.
    * @return The provider of the CopilotApp instance, or the default provider when not set.
    * @throws OBException If an error occurs while retrieving the provider.
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
        logIfDebug(String.format("Model selected in app: %s", resultModel));
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

  /**
   * Returns a {@link ModelProviderResult} containing the model search key and provider for the
   * given {@link CopilotApp} agent.
   * <p>
   * The method first validates the {@code agent} parameter. If the agent has an associated
   * {@link CopilotModel}, its search key and provider are returned. Otherwise the configured
   * default model (retrieved via {@link #getDefaultModel(String)} with a {@code null} provider)
   * is used. If no default model is available, an {@link OBException} is thrown.
   * <p>
   * Important details:
   * - The default model resolution invoked here will first consult the global preference
   *   "ETCOP_DefaultModelOverride" (format: "provider/modelId"). If that preference references a
   *   valid model, it will be returned as the default (i.e., the preference acts as a global override).
   * - If the agent's model exists but its {@code provider} property is {@code null} or empty, this
   *   method will return the provider value as-is (possibly {@code null}). The helper
   *   {@link #getProvider(CopilotApp)} which supplies a fallback of {@code "openai"} is NOT used
   *   by this method — callers that require a non-null provider should call {@link #getProvider}
   *   or perform their own fallback logic.
   *
   * @param agent the {@link CopilotApp} to inspect; must not be {@code null}
   * @return a {@link ModelProviderResult} with the model search key and provider
   * @throws OBException if {@code agent} is {@code null} or if no default model is found
   */
  public static ModelProviderResult getModelProviderResult(CopilotApp agent) {
    if (agent == null) {
      throw new OBException("CopilotApp agent is null");
    }
    CopilotModel model = agent.getModel();
    if (model != null) {
      return new ModelProviderResult(model.getSearchkey(), model.getProvider());
    }
    model = getDefaultModel(null);
    if (model != null) {
      return new ModelProviderResult(model.getSearchkey(), model.getProvider());
    }
    throw new OBException("No default CopilotModel found");

  }

  /**
   * Simple DTO holding a selected model search key and its provider.
   */
  public static class ModelProviderResult {
    /** The model search key (identifier). */
    public final String modelStr;
    /** The provider identifier for the model. */
    public final String providerStr;

    /**
     * Constructs a new result instance.
     *
     * @param modelStr the model search key
     * @param providerStr the provider identifier
     */
    public ModelProviderResult(String modelStr, String providerStr) {
      this.modelStr = modelStr;
      this.providerStr = providerStr;
    }
  }
}
