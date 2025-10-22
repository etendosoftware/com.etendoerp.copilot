package com.etendoerp.copilot.rest;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

class RestServiceUtilTestLang {


  private static final String LIT_LANG1 = "lang1";




  @Test
  void testGetJSONLabelsModuleLanguageEquals() throws Exception {
    // Setup OBContext with language
    OBContext mockCtx = Mockito.mock(OBContext.class);
    org.openbravo.model.ad.system.Language mockLang = Mockito.mock(org.openbravo.model.ad.system.Language.class);
    Mockito.when(mockLang.getId()).thenReturn(LIT_LANG1);
    Mockito.when(mockCtx.getLanguage()).thenReturn(mockLang);

    // Module whose language equals current language
    org.openbravo.model.ad.module.Module module = Mockito.mock(org.openbravo.model.ad.module.Module.class);
    org.openbravo.model.ad.system.Language modLang = Mockito.mock(org.openbravo.model.ad.system.Language.class);
    Mockito.when(modLang.getId()).thenReturn(LIT_LANG1);
    Mockito.when(module.getLanguage()).thenReturn(modLang);

    // OBDal and criteria for Message
    OBDal dal = Mockito.mock(OBDal.class);
    org.openbravo.dal.service.OBCriteria<org.openbravo.model.ad.ui.Message> crit = Mockito
        .mock(org.openbravo.dal.service.OBCriteria.class);
    Mockito.when(dal.get(org.openbravo.model.ad.module.Module.class, RestServiceUtil.COPILOT_MODULE_ID))
        .thenReturn(module);
    Mockito.when(dal.createCriteria(org.openbravo.model.ad.ui.Message.class)).thenReturn(crit);
    Mockito.when(crit.add(org.mockito.ArgumentMatchers.any())).thenReturn(crit);

    org.openbravo.model.ad.ui.Message msg = Mockito.mock(org.openbravo.model.ad.ui.Message.class);
    Mockito.when(msg.getIdentifier()).thenReturn("id1");
    Mockito.when(msg.getMessageText()).thenReturn("text1");
    Mockito.when(crit.list()).thenReturn(List.of(msg));

    try (org.mockito.MockedStatic<OBContext> mockOB = Mockito.mockStatic(OBContext.class);
         org.mockito.MockedStatic<OBDal> mockDal = Mockito.mockStatic(OBDal.class)) {
      mockOB.when(() -> OBContext.setAdminMode(false)).thenAnswer(i -> null);
      mockOB.when(OBContext::getOBContext).thenReturn(mockCtx);
      mockOB.when(OBContext::restorePreviousMode).thenAnswer(i -> null);

      mockDal.when(OBDal::getInstance).thenReturn(dal);

      JSONObject json = RestServiceUtil.getJSONLabels();
      Assertions.assertNotNull(json);
      Assertions.assertEquals("text1", json.getString("id1"));
    }
  }

  @Test
  void testGetJSONLabelsMessageTrlBranch() throws Exception {
    // Setup OBContext with language different from module language
    OBContext mockCtx = Mockito.mock(OBContext.class);
    org.openbravo.model.ad.system.Language mockLang = Mockito.mock(org.openbravo.model.ad.system.Language.class);
    Mockito.when(mockLang.getId()).thenReturn("lang2");
    Mockito.when(mockCtx.getLanguage()).thenReturn(mockLang);

    // Module whose language is different
    org.openbravo.model.ad.module.Module module = Mockito.mock(org.openbravo.model.ad.module.Module.class);
    org.openbravo.model.ad.system.Language modLang = Mockito.mock(org.openbravo.model.ad.system.Language.class);
    Mockito.when(modLang.getId()).thenReturn(LIT_LANG1);
    Mockito.when(module.getLanguage()).thenReturn(modLang);

    // OBDal and criteria for MessageTrl
    OBDal dal = Mockito.mock(OBDal.class);
    org.openbravo.dal.service.OBCriteria<org.openbravo.model.ad.ui.MessageTrl> crit = Mockito
        .mock(org.openbravo.dal.service.OBCriteria.class);
    Mockito.when(dal.get(org.openbravo.model.ad.module.Module.class, RestServiceUtil.COPILOT_MODULE_ID))
        .thenReturn(module);
    Mockito.when(dal.createCriteria(org.openbravo.model.ad.ui.MessageTrl.class)).thenReturn(crit);
    Mockito.when(crit.add(org.mockito.ArgumentMatchers.any())).thenReturn(crit);
    // createAlias returns the criteria; mock it to return the same crit
    Mockito.when(crit.createAlias(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString())).thenReturn(crit);

    org.openbravo.model.ad.ui.MessageTrl msgTrl = Mockito.mock(org.openbravo.model.ad.ui.MessageTrl.class);
    org.openbravo.model.ad.ui.Message linkedMsg = Mockito.mock(org.openbravo.model.ad.ui.Message.class);
    Mockito.when(linkedMsg.getIdentifier()).thenReturn("id2");
    Mockito.when(msgTrl.getMessage()).thenReturn(linkedMsg);
    Mockito.when(msgTrl.getMessageText()).thenReturn("text2");
    Mockito.when(crit.list()).thenReturn(List.of(msgTrl));

    try (org.mockito.MockedStatic<OBContext> mockOB = Mockito.mockStatic(OBContext.class);
         org.mockito.MockedStatic<OBDal> mockDal = Mockito.mockStatic(OBDal.class)) {
      mockOB.when(() -> OBContext.setAdminMode(false)).thenAnswer(i -> null);
      mockOB.when(OBContext::getOBContext).thenReturn(mockCtx);
      mockOB.when(OBContext::restorePreviousMode).thenAnswer(i -> null);

      mockDal.when(OBDal::getInstance).thenReturn(dal);

      JSONObject json = RestServiceUtil.getJSONLabels();
      Assertions.assertNotNull(json);
      Assertions.assertEquals("text2", json.getString("id2"));
    }
  }
}
