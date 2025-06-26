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

import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.datasource.DataSourceUtils;
import org.openbravo.service.json.JsonConstants;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotFile;

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
      String fileName = getFileName(appSource);
      String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
      String hql = appSource.getFile().getHql();
      hql = hql.replaceAll("\\r\\n|\\r|\\n", " ");

      String result = getHQLResult(hql, "e", extension);

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
  public static String getFileName(CopilotAppSource appSource) {
    // If the file object within the appSource is not null and it has a non-empty filename, return the filename.
    String full_name = null; //default name
    String nameWithoutExtension;
    String extension;
    CopilotFile file = appSource.getFile();
    if (file != null && StringUtils.isNotEmpty(file.getFilename())) {
      full_name = file.getFilename();
    }
    // If the file object within the appSource has a non-empty name, sanitize it and return.
    // The sanitization process involves replacing spaces with underscores and removing characters that are not allowed in file names.
    if (StringUtils.isEmpty(full_name) && StringUtils.isNotEmpty(file.getName())) {
      full_name = file.getName();
    }
    if (full_name == null) {
      full_name = "result" + System.currentTimeMillis() + ".csv";
    }
    if (full_name.contains(".")) {
      nameWithoutExtension = full_name.substring(0, full_name.lastIndexOf("."));
      extension = full_name.substring(full_name.lastIndexOf("."));
    } else {
      nameWithoutExtension = full_name;
      extension = ".csv";
    }

    // Remove characters that are not allowed in file names and spaces
    nameWithoutExtension = nameWithoutExtension.replace(" ", "_");

    nameWithoutExtension = nameWithoutExtension.replaceAll("[^a-zA-Z0-9-_]", "");
    full_name = nameWithoutExtension + extension;


    // If the file object within the appSource does not have a name or filename, generate a default name using the current timestamp.
    return full_name;
  }

  private File createAttachment(String fileName, String result) throws IOException {
    Path tempDirectory = Files.createTempDirectory("temporary_queries");
    Path tempFile = tempDirectory.resolve(fileName);
    try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile())) {
      fileOutputStream.write(result.getBytes());
    } catch (FileNotFoundException e) {
      throw new OBException(e);
    }
    return new File(tempFile.toString());
  }

  private String getHQLResult(String hql, String entityAlias, String extension) {
    boolean isCsv = StringUtils.equalsIgnoreCase(extension, "csv");
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
    /// Determine the appropriate separator for appending HQL filters
    String separator = StringUtils.containsIgnoreCase(hql, WHERE) ? AND : WHERE;
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
    var resultList = qry.getResultList();
    var headersArray = qry.getReturnAliases();
    if (isCsv && headersArray != null) {
      results.add(String.join(", ", headersArray));
    }
    for (Object resultObject : resultList) {
      if (!resultObject.getClass().isArray()) {
        results.add(printObject(resultObject));
        continue;
      }
      final Object[] values = (Object[]) resultObject;
      var listColumnValues = Arrays.stream(values)
          .map(this::printObject).collect(Collectors.toList());
      if (!isCsv) {
        addAliasesForColumns(listColumnValues, headersArray);
      }

      results.add(String.join(isCsv ? ", " : "\n", listColumnValues));
    }
    return String.join(isCsv ? "\n" : "\n\n", results);
  }

  /**
   * Adds aliases to column values based on the provided headers array.
   * <p>
   * This method iterates through the list of column values and prepends each value
   * with its corresponding alias from the headers array. If a header is not available
   * or is empty, a default alias ("?") is used.
   *
   * @param listColumnValues
   *     A {@link List} of {@link String} representing the column values to be updated.
   * @param headersArray
   *     An array of {@link String} containing the aliases for the columns.
   */
  private static void addAliasesForColumns(List<String> listColumnValues, String[] headersArray) {
    for (int i = 0; i < listColumnValues.size(); i++) {
      listColumnValues.set(i, (headersArray.length > i && StringUtils.isNotEmpty(
          headersArray[i]) ? headersArray[i] : "?") + ": " + listColumnValues.get(i));
    }
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
