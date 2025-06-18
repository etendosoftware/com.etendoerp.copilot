package com.etendoerp.copilot.util;

import static com.etendoerp.copilot.util.CopilotConstants.PROVIDER_OPENAI;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Element;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.xml.XMLUtil;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

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
   * Synchronizes the models by downloading the dataset file and updating the models in the database.
   * <p>
   * This method retrieves the URL for the models dataset from the properties, downloads the file,
   * and calls the `upsertModels` method to update the models in the database.
   *
   * @throws OBException
   *     If an error occurs during the synchronization process.
   */
  public static void syncModels() throws OBException {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String url = properties.getProperty("COPILOT_MODELS_DATASET_URL", CopilotUtils.DEFAULT_MODELS_DATASET_URL).replace(
        "<BRANCH>", properties.getProperty("COPILOT_MODELS_DATASET_BRANCH", "master"));
    upsertModels(downloadFile(url));
  }

  /**
   * Updates the models in the database using the provided dataset file.
   * <p>
   * This method reads the dataset file, parses the XML to extract model elements,
   * and calls the `upsertModel` method for each model element to update the database.
   *
   * @param datasetFile
   *     The dataset file containing the models to be updated.
   * @throws OBException
   *     If an error occurs while updating the models.
   */
  private static void upsertModels(File datasetFile) {
    try (FileInputStream fis = new FileInputStream(datasetFile)) {
      OBContext.setAdminMode(false);
      List<Element> elementList = XMLUtil.getInstance().getRootElement(fis).elements("ETCOP_Openai_Model");
      for (Element modelElem : elementList) {
        logIfDebug(modelElem.toString());
        upsertModel(modelElem);
      }
      OBDal.getInstance().flush();
    } catch (IOException e) {
      throw new OBException(e);
    } finally {
      OBContext.restorePreviousMode();
    }
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
   * Inserts or updates a model in the database based on the provided XML element.
   * <p>
   * This method checks if the model already exists in the database. If it does, it updates the model.
   * If it does not, it creates a new model and sets its properties based on the XML element.
   *
   * @param modelElem
   *     The XML element containing the model data.
   */
  private static void upsertModel(Element modelElem) {
    CopilotModel model = OBDal.getInstance().get(CopilotModel.class, modelElem.elementText("id"));
    if (model == null) {
      model = OBProvider.getInstance().get(CopilotModel.class);
      model.setId(modelElem.elementText("id"));
      model.setNewOBObject(true);
      model.setCreatedBy(OBDal.getInstance().get(User.class, "0"));
      model.setUpdatedBy(OBDal.getInstance().get(User.class, "0"));
      model.setCreationDate(new Date());
      model.setUpdated(new Date());
      model.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
      model.setClient(OBDal.getInstance().get(Client.class, "0"));
    }
    model.setActive(Boolean.parseBoolean(modelElem.elementText("active")));
    model.setSearchkey(modelElem.elementText("searchkey"));
    model.setName(modelElem.elementText("name"));
    model.setProvider(modelElem.elementText("provider"));
    model.setEtendoMaintained(true);
    model.setMaxTokens(StringUtils.isNotEmpty(modelElem.elementText("maxTokens")) ? Long.parseLong(
        modelElem.elementText("maxTokens")) : null);
    model.setDefault(Boolean.parseBoolean(modelElem.elementText("default")));
    OBDal.getInstance().save(model);
    disableReplicated(model.getId(), model.getSearchkey());
  }

  /**
   * Disables replicated models with the same search key.
   * <p>
   * This method finds all CopilotModel instances with the same search key but different ID,
   * and sets their active status to false.
   *
   * @param id
   *     The ID of the model to exclude from the search.
   * @param searchkey
   *     The search key to match for disabling replicated models.
   */
  private static void disableReplicated(String id, String searchkey) {
    OBCriteria<CopilotModel> modelCriteria = OBDal.getInstance().createCriteria(CopilotModel.class);
    modelCriteria.add(Restrictions.ne(CopilotModel.PROPERTY_ID, id));
    modelCriteria.add(Restrictions.eq(CopilotModel.PROPERTY_SEARCHKEY, searchkey));
    List<CopilotModel> models = modelCriteria.list();
    for (CopilotModel modelrep : models) {
      modelrep.setActive(false);
      OBDal.getInstance().save(modelrep);
    }
  }

  /**
   * Downloads a file from the specified URL.
   * <p>
   * This method creates a temporary file and downloads the content from the provided URL into the file.
   *
   * @param fileUrl
   *     The URL of the file to be downloaded.
   * @return The downloaded file.
   * @throws OBException
   *     If an error occurs while downloading the file.
   */
  private static File downloadFile(String fileUrl) {
    try {
      File tempFile = File.createTempFile("download", null);
      try (InputStream in = new URL(fileUrl).openStream()) {
        Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      return tempFile;
    } catch (IOException e) {
      throw new OBException(e);
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
      log.debug("Model selected by Default: {}", resultModel);
      return resultModel;
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
  }
}
