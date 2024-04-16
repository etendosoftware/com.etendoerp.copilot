package com.etendoerp.copilot.hook;

import com.etendoerp.copilot.data.CopilotFile;
import org.apache.commons.lang.StringUtils;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.ad.datamodel.Table;

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
import java.util.stream.Collectors;

import static com.etendoerp.copilot.hook.RemoteFileHook.COPILOT_FILE_TAB_ID;

public class HQLFileHook implements CopilotFileHook {

  private static final String COPILOT_FILE_AD_TABLE_ID = "6B246B1B3A6F4DE8AFC208E07DB29CE2";
  public static final String FILE_TYPE_HQL = "HQL";

  @Override
  public void exec(CopilotFile hookObject) throws OBException {
    try {
      String result = getHQLResult(hookObject.getHql());
      String fileName = hookObject.getFilename();
      // store the result in a temporary file
      AttachImplementationManager aim = WeldUtils.getInstanceFromStaticBeanManager(
          AttachImplementationManager.class);
      removeAttachment(aim, hookObject);
      File file = createAttachment(fileName, result);
      aim.upload(new HashMap<>(), COPILOT_FILE_TAB_ID, hookObject.getId(),
          hookObject.getOrganization().getId(), file);
    } catch (IOException e) {
      throw new OBException(e);
    } finally {
      OBDal.getInstance().flush();
    }
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

  public static Attachment getAttachment(CopilotFile targetInstance) {
    OBCriteria<Attachment> attchCriteria = OBDal.getInstance().createCriteria(Attachment.class);
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, targetInstance.getId()));
    attchCriteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE,
        OBDal.getInstance().get(Table.class, COPILOT_FILE_AD_TABLE_ID)));
    attchCriteria.add(Restrictions.ne(Attachment.PROPERTY_ID, targetInstance.getId()));
    return (Attachment) attchCriteria.setMaxResults(1).uniqueResult();
  }

  private void removeAttachment(AttachImplementationManager aim, CopilotFile hookObject) {
    Attachment attachment = getAttachment(hookObject);
    if (attachment != null) {
      aim.delete(attachment);
    }
  }

  private String getHQLResult(String hql) {
    final org.hibernate.Session session = OBDal.getInstance().getSession();
    var qry = session.createQuery(hql);
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

  @Override
  public boolean typeCheck(String type) {
    return StringUtils.equals(type, FILE_TYPE_HQL);
  }
}
