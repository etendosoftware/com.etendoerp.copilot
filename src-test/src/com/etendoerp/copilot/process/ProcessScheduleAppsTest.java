package com.etendoerp.copilot.process;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.ui.ProcessRequest;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.data.CopilotRoleApp;
import com.etendoerp.copilot.data.ETCOPSchedule;
import com.etendoerp.copilot.rest.RestServiceUtil;
import com.etendoerp.copilot.util.OpenAIUtils;

public class ProcessScheduleAppsTest extends WeldBaseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private ProcessBundle processBundle;
    
    @Mock
    private ProcessLogger processLogger;
    
    @Mock
    private OBDal obDal;
    
    @Mock
    private OBCriteria<ETCOPSchedule> scheduleCriteria;
    
    @Mock
    private OBCriteria<CopilotRoleApp> roleAppCriteria;
    
    @Mock
    private ProcessRequest processRequest;
    
    @Mock
    private ETCOPSchedule schedule;
    
    @Mock
    private CopilotApp copilotApp;
    
    @Mock
    private Role role;
    
    @Mock
    private CopilotRoleApp roleApp;
    
    @Mock
    private CopilotAppSource appSource;

    private ProcessScheduleApps processScheduleApps;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBContext> mockedOBContext;
    private MockedStatic<OpenAIUtils> mockedOpenAIUtils;
    private MockedStatic<RestServiceUtil> mockedRestServiceUtil;
    private AutoCloseable mocks;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        processScheduleApps = new ProcessScheduleApps();
        
        // Mock static classes
        mockedOBDal = mockStatic(OBDal.class);
        mockedOBContext = mockStatic(OBContext.class);
        mockedOpenAIUtils = mockStatic(OpenAIUtils.class);
        mockedRestServiceUtil = mockStatic(RestServiceUtil.class);
        
        // Setup OBDal
        mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
        
        // Setup ProcessBundle
        when(processBundle.getLogger()).thenReturn(processLogger);
        when(processBundle.getProcessRequestId()).thenReturn("testProcessRequestId");
        
        // Setup OBContext
        OBContext mockContext = mock(OBContext.class);
        mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
        when(mockContext.getRole()).thenReturn(role);
        
        // Setup Criteria
        when(obDal.createCriteria(ETCOPSchedule.class)).thenReturn(scheduleCriteria);
        when(obDal.createCriteria(CopilotRoleApp.class)).thenReturn(roleAppCriteria);
        when(scheduleCriteria.add(any(Criterion.class))).thenReturn(scheduleCriteria);
        when(roleAppCriteria.add(any(Criterion.class))).thenReturn(roleAppCriteria);
        
        // Setup ProcessRequest
        when(obDal.get(ProcessRequest.class, "testProcessRequestId")).thenReturn(processRequest);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
        if (mockedOpenAIUtils != null) {
            mockedOpenAIUtils.close();
        }
        if (mockedRestServiceUtil != null) {
            mockedRestServiceUtil.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void testDoExecute_Success() throws Exception {
        // Given
        List<ETCOPSchedule> schedules = Collections.singletonList(schedule);
        when(scheduleCriteria.list()).thenReturn(schedules);
        when(schedule.getCopilotApp()).thenReturn(copilotApp);
        when(roleAppCriteria.setMaxResults(1)).thenReturn(roleAppCriteria);
        when(roleAppCriteria.uniqueResult()).thenReturn(roleApp);
        when(schedule.getConversation()).thenReturn("testConversation");
        when(schedule.getPrompt()).thenReturn("testPrompt");

        List<CopilotAppSource> sources = Collections.singletonList(appSource);
        when(copilotApp.getETCOPAppSourceList()).thenReturn(sources);

        // Mock handleQuestion response
        JSONObject response = new JSONObject();
        response.put("app_id", "testAppId");
        response.put("response", "Test response");
        response.put("conversation_id", "testConversationId");
        response.put("timestamp", new Timestamp(System.currentTimeMillis()).toString());

        mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(
                anyBoolean(),
                isNull(),
                any(CopilotApp.class),
                anyString(),
                anyString(),
                anyList()
        )).thenReturn(response);

        // When
        processScheduleApps.doExecute(processBundle);

        // Then
        verify(processLogger, times(4)).log(anyString());
        mockedRestServiceUtil.verify(
                () -> RestServiceUtil.handleQuestion(
                        anyBoolean(),
                        isNull(),
                        any(CopilotApp.class),
                        anyString(),
                        anyString(),
                        anyList()
                ),
                times(1)
        );
    }

    @Test
    public void testDoExecute_NoAccess() throws Exception {
        // Given
        List<ETCOPSchedule> schedules = Collections.singletonList(schedule);
        when(scheduleCriteria.list()).thenReturn(schedules);
        when(schedule.getCopilotApp()).thenReturn(copilotApp);
        when(roleAppCriteria.setMaxResults(1)).thenReturn(roleAppCriteria);
        when(roleAppCriteria.uniqueResult()).thenReturn(null);
        
        mockedOpenAIUtils.when(() -> OpenAIUtils.getOpenaiApiKey()).thenReturn("test-api-key");

        // When
        processScheduleApps.doExecute(processBundle);

        // Then
        verify(processLogger, times(3)).log(anyString());
        mockedRestServiceUtil.verify(
            () -> RestServiceUtil.handleQuestion(anyBoolean(), any(), any(), any(), anyString(), anyList()),
            times(0)
        );
    }

    @Test
    public void testDoExecute_ConnectionError() throws Exception {
        // Given
        expectedException.expect(OBException.class);
        
        List<ETCOPSchedule> schedules = Collections.singletonList(schedule);
        when(scheduleCriteria.list()).thenReturn(schedules);
        when(schedule.getCopilotApp()).thenReturn(copilotApp);
        when(roleAppCriteria.setMaxResults(1)).thenReturn(roleAppCriteria);
        when(roleAppCriteria.uniqueResult()).thenReturn(roleApp);
        
        List<CopilotAppSource> sources = new ArrayList<>();
        when(copilotApp.getETCOPAppSourceList()).thenReturn(sources);
        
        mockedOpenAIUtils.when(() -> OpenAIUtils.getOpenaiApiKey()).thenReturn("test-api-key");
        
        mockedRestServiceUtil.when(() -> RestServiceUtil.handleQuestion(
            anyBoolean(), any(), any(), any(), anyString(), anyList()
        )).thenThrow(new java.net.ConnectException("Connection refused"));

        // When
        processScheduleApps.doExecute(processBundle);

        // Then
        verify(processLogger, times(4)).log(anyString());
    }
}