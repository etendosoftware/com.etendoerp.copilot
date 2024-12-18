package com.etendoerp.copilot.process;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;

@RunWith(MockitoJUnitRunner.class)
public class CheckHostsButtonTest {

    @Mock
    private Role mockRole;

    @Mock
    private User mockUser;

    @InjectMocks
    private CheckHostsButton checkHostsButton;

    private MockedStatic<OBContext> mockedOBContext;
    private MockedStatic<OBDal> mockedOBDal;

    @Before
    public void setUp() {
        mockedOBContext = mockStatic(OBContext.class);
        mockedOBDal = mockStatic(OBDal.class);

        OBContext obContext = mock(OBContext.class);
        when(OBContext.getOBContext()).thenReturn(obContext);
        when(obContext.getRole()).thenReturn(mockRole);
        when(obContext.getUser()).thenReturn(mockUser);

        OBDal obDal = mock(OBDal.class);
        when(OBDal.getInstance()).thenReturn(obDal);
        when(obDal.get(Role.class, mockRole.getId())).thenReturn(mockRole);
        when(obDal.get(User.class, mockUser.getId())).thenReturn(mockUser);
    }

    @After
    public void tearDown() {
        mockedOBContext.close();
        mockedOBDal.close();
    }

    @Test
    public void testDoExecute_tokenNull_throwsOBException() {
        // Given
        CheckHostsButton spyCheckHostsButton = spy(checkHostsButton);
        doReturn(null).when(spyCheckHostsButton).getSecurityToken();

        // When & Then
        try {
            spyCheckHostsButton.doExecute(mock(Map.class), "");
            fail("Expected OBException to be thrown");
        } catch (OBException e) {
            assertEquals("Error when generating token.", e.getMessage());
        }
    }

    @Test
    public void testGetSecurityToken_success() throws Exception {
        // Given
        when(mockRole.getId()).thenReturn("roleId");
        when(mockUser.getId()).thenReturn("userId");

        // When
        String token = CheckHostsButton.getSecurityToken();

        // Then
        assertNotNull(token);
    }

    @Test
    public void testCheckEtendoHost_success() throws Exception {
        // Given
        String token = "validToken";
        HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getResponseCode()).thenReturn(200);

        CheckHostsButton spyCheckHostsButton = spy(checkHostsButton);
        doReturn(mockConnection).when(spyCheckHostsButton).createConnection(anyString(), eq(token));

        // When
        JSONObject result = spyCheckHostsButton.checkEtendoHost(token);

        // Then
        assertTrue(result.getBoolean(CheckHostsButton.ETENDO_HOST));
    }

    @Test
    public void testCreateConnection_configuresCorrectly() throws IOException {
        // Given
        String urlString = "http://example.com";
        String token = "validToken";

        // When
        HttpURLConnection connection = checkHostsButton.createConnection(urlString, token);

        // Then
        assertEquals("POST", connection.getRequestMethod());
        assertEquals("Bearer " + token, connection.getRequestProperty("Authorization"));
        assertEquals(CheckHostsButton.CONTENT_TYPE, connection.getRequestProperty("Content-Type"));
    }
}
