package com.etendoerp.copilot.hook;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.datasource.DataSourceUtils;
import org.openbravo.service.json.JsonConstants;

import com.etendoerp.copilot.data.CopilotAppSource;

public class ProcessHQLAppSource {
  private static final ProcessHQLAppSource INSTANCE = new ProcessHQLAppSource();
  private static final String AND = " AND ";
  private static final String WHERE = " WHERE ";
  public static final String CLIENT_ID = "clientId";
  public static final String ORGANIZATIONS = "organizations";

  public static ProcessHQLAppSource getInstance() {
    return INSTANCE;
  }

  public File generate(CopilotAppSource appSource) throws OBException {
    try {
      String result = getHQLResult(appSource.getFile().getHql(), "e");

      String fileName = getFileName(appSource);
      return createAttachment(fileName, result);
    } catch (IOException e) {
      throw new OBException(
          String.format(String.format(OBMessageUtils.messageBD("ETCOP_HQLGenErr"), e)));

    } finally {
      OBDal.getInstance().flush();
    }
  }

  /**
   * This method is used to generate a file name for a given CopilotAppSource object.
   *
   * @param appSource
   *     The CopilotAppSource object for which the file name is to be generated.
   * @return A string representing the file name.
   */
  private String getFileName(CopilotAppSource appSource) {
    // If the file object within the appSource is not null and it has a non-empty filename, return the filename.
    if (appSource.getFile() != null && StringUtils.isNotEmpty(appSource.getFile().getFilename())) {
      return appSource.getFile().getFilename();
    }
    // If the file object within the appSource has a non-empty name, sanitize it and return.
    // The sanitization process involves replacing spaces with underscores and removing characters that are not allowed in file names.
    if (StringUtils.isNotEmpty(appSource.getFile().getName())) {
      var name = appSource.getFile().getName();
      // Remove characters that are not allowed in file names and spaces
      name = name.replace(" ", "_");
      name = name.replaceAll("[^a-zA-Z0-9-_]", "");
      return name;
    }
    // If the file object within the appSource does not have a name or filename, generate a default name using the current timestamp.
    return "result" + System.currentTimeMillis();
  }

  private File createAttachment(String fileName, String result) throws IOException {
    Path tempDirectory = Files.createTempDirectory("temporary_queries");
    Path tempFile = tempDirectory.resolve(fileName + ".csv");
    try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile())) {
      fileOutputStream.write(result.getBytes());
    } catch (FileNotFoundException e) {
      throw new OBException(e);
    }
    return new File(tempFile.toString());
  }

  private String getHQLResult(String hql, String entityAlias) {
    final org.hibernate.Session session = OBDal.getInstance().getSession();
    Map<String, String> parameters = new HashMap<>();
    String additionalFilter = entityAlias + ".client.id in ('0', :clientId)";
    // client filter
    parameters.put(CLIENT_ID, OBContext.getOBContext().getCurrentClient().getId());

    // organization filter
    final String orgs = DataSourceUtils.getOrgs(parameters.get(JsonConstants.ORG_PARAMETER));
    if (StringUtils.isNotEmpty(orgs)) {
      additionalFilter += AND + entityAlias + ".organization.id in ( :organizations )";
      parameters.put(ORGANIZATIONS, orgs);
    }
    // adds the hql filters in the proper place at the end of the query
    String separator = null;
    if (StringUtils.containsIgnoreCase(hql, WHERE)) {
      // if there is already a where clause, append with 'AND'
      separator = AND;
    } else {
      // otherwise, append with 'where'
      separator = WHERE;
    }
    hql = hql + separator + additionalFilter;
    var qry = session.createQuery(hql);
    Set<String> namedParameters = qry.getParameterMetadata().getNamedParameterNames();
    if (namedParameters.contains(CLIENT_ID)) {
      qry.setParameter(CLIENT_ID, parameters.get(CLIENT_ID));
      parameters.remove(CLIENT_ID);
    }
    if (namedParameters.contains(ORGANIZATIONS)) {
      qry.setParameterList(ORGANIZATIONS,
          parameters.get(ORGANIZATIONS).replaceAll("'", "").split(","));
      parameters.remove(ORGANIZATIONS);
    }
    parameters.forEach(qry::setParameter);
    List<String> results = new ArrayList<>();
    for (Object resultObject : qry.list()) {
      if (resultObject.getClass().isArray()) {
        final Object[] values = (Object[]) resultObject;
        results.add(Arrays.stream(values).map(this::printObject).collect(Collectors.joining(", ")));
      } else {
        results.add(printObject(resultObject));
      }
    }
    return String.join("\n", results);
  }

  private String printObject(Object value) {
    if (value == null) {
      return "NULL";
    }
    if (value instanceof BaseOBObject) {
      return printBaseOBObject((BaseOBObject) value);
    }
    return value.toString();
  }

  private String printBaseOBObject(BaseOBObject bob) {
    final boolean derivedReadable = OBContext.getOBContext()
        .getEntityAccessChecker()
        .isDerivedReadable(bob.getEntity());
    if (derivedReadable) {
      return bob.toString();
    }
    String properties = bob.getEntity()
        .getProperties()
        .stream()
        .filter(p -> !p.isOneToMany() && bob.get(p.getName()) != null)
        .map(p -> {
          Object value = bob.get(p.getName());
          if (value instanceof BaseOBObject) {
            final BaseOBObject bobValue = (BaseOBObject) value;
            value = getEntityLink(bobValue,
                bobValue.getId() + " (" + bobValue.getIdentifier() + ")");
          } else if (p.isId()) {
            value = getEntityLink(bob, (String) value);
          }
          return p.getName() + ": " + value;
        })
        .collect(Collectors.joining(", "));
    return "[entity: " + bob.getEntityName() + ", " + properties + "]";
  }

  private String getEntityLink(BaseOBObject bob, String title) {
    String contextName = OBPropertiesProvider.getInstance()
        .getOpenbravoProperties()
        .getProperty("context.name");
    return "/" + contextName + "/" + bob.getEntityName() + "/" + bob.getId() + "/" + title;
  }

}
