package com.etendoerp.copilot.process;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.ETCOPSchedule;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.CopilotConstants;
import com.etendoerp.copilot.util.OpenAIUtils;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.ui.ProcessRequest;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.test.base.OBBaseTest;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ProcessScheduleAppsTest extends OBBaseTest {

    @Mock
    private ProcessBundle processBundle;
    @Mock
    private ProcessLogger logger;
    @Mock
    private ProcessRequest processRequest;
    @Mock
    private ETCOPSchedule schedule;
    @Mock
    private CopilotApp copilotApp;
    @Mock
    private CopilotAppSource copilotAppSource;
    @Mock
    private Role role;
    
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OpenAIUtils> mockedOpenAIUtils;
    private MockedStatic<RestServiceUtil> mockedRestServiceUtil;

    private ProcessScheduleApps processScheduleApps;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        processScheduleApps = new ProcessScheduleApps();
        when(processBundle.getLogger()).thenReturn(logger);
        mockedOBDal = mockStatic(OBDal.class);
        mockedOpenAIUtils = mockStatic(OpenAIUtils.class);
        mockedRestServiceUtil = mockStatic(RestServiceUtil.class);
    }

    @After
    public void tearDown() {
        mockedOBDal.close();
        mockedOpenAIUtils.close();
        mockedRestServiceUtil.close();
    }

    @Test
    public void testDoExecute() throws Exception {
        // Given
        when(OBDal.getInstance().get(ProcessRequest.class, processBundle.getProcessRequestId())).thenReturn(processRequest);
        OBCriteria<ETCOPSchedule> criteria = mock(OBCriteria.class);
        when(OBDal.getInstance().createCriteria(ETCOPSchedule.class)).thenReturn(criteria);
        when(criteria.add(Restrictions.eq(ETCOPSchedule.PROPERTY_PROCESSREQUEST, processRequest))).thenReturn(criteria);
        when(criteria.list()).thenReturn(List.of(schedule));

        // When
        processScheduleApps.doExecute(processBundle);

        // Then
        verify(logger).log("Refreshing 1 schedules\n");
        verify(logger).log("Processing 1 schedules\n");
    }

    @Test
    public void testRefreshScheduleFiles() throws Exception {
        // Given
        when(schedule.getCopilotApp()).thenReturn(copilotApp);
        when(copilotApp.getETCOPAppSourceList()).thenReturn(List.of(copilotAppSource));
        when(CopilotConstants.isAttachBehaviour(copilotAppSource)).thenReturn(true);
        when(copilotAppSource.getFile().getName()).thenReturn("testFile");
        mockedOpenAIUtils.when(OpenAIUtils::getOpenaiApiKey).thenReturn("apiKey");

        // When
        processScheduleApps.refreshScheduleFiles(List.of(schedule));

        // Then
        verify(logger).log("- Syncing source testFile\n");
        mockedOpenAIUtils.verify(() -> OpenAIUtils.syncAppSource(copilotAppSource, "apiKey"));
    }

    @Test
    public void testProcessSchedules() throws Exception {
        // Given
        when(schedule.getCopilotApp()).thenReturn(copilotApp);
        when(OBContext.getOBContext().getRole()).thenReturn(role);
        when(role.getName()).thenReturn("TestRole");
        when(copilotApp.getName()).thenReturn("TestApp");
        when(copilotApp.getETCOPAppSourceList()).thenReturn(List.of(copilotAppSource));
        when(CopilotConstants.isAttachBehaviour(copilotAppSource)).thenReturn(true);
        when(copilotAppSource.getOpenaiIdFile()).thenReturn("fileId");
        when(schedule.getPrompt()).thenReturn("Test prompt");
        JSONObject response = new JSONObject();
        response.put("response", "Test response");
        mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(false, null, copilotApp, schedule.getConversation(), schedule.getPrompt(), List.of("fileId"))).thenReturn(response);

        // When
        processScheduleApps.processSchedules(List.of(schedule));

        // Then
        verify(logger).log("-> Send question to copilot:\n---\n Test prompt\n---\n");
        verify(logger).log("<- Copilot response:\n---\nTest response\n---\n");
    }

    @Test
    public void testCheckRoleAccessApp() {
        // Given
        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
        OBCriteria<CopilotRoleApp> criteria = mock(OBCriteria.class);
        when(OBDal.getInstance().createCriteria(CopilotRoleApp.class)).thenReturn(criteria);
        when(criteria.add(Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, role))).thenReturn(criteria);
        when(criteria.add(Restrictions.eq(CopilotRoleApp.PROPERTY_COPILOTAPP, copilotApp))).thenReturn(criteria);
        when(criteria.setMaxResults(1)).thenReturn(criteria);
        when(criteria.uniqueResult()).thenReturn(mock(CopilotRoleApp.class));

        // When
        boolean hasAccess = processScheduleApps.checkRoleAccessApp(role, copilotApp);

        // Then
        assertTrue(hasAccess);
    }
}
