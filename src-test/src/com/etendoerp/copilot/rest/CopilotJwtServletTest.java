/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.rest;

import com.etendoerp.copilot.util.OpenAIUtils;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.test.base.TestConstants;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

/**
 * Unit tests for the {@link CopilotJwtServlet} class.
 */
public class CopilotJwtServletTest extends WeldBaseTest {

    private CopilotJwtServlet servlet;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private RestService mockRestService;

    /**
     * Sets up the necessary mocks before each test.
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = spy(new CopilotJwtServlet());
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockRestService = mock(RestService.class);

        // Inject the mock RestService instance into CopilotJwtServlet
        setPrivateField(CopilotJwtServlet.class, "instance", servlet, mockRestService);

        // Set OBContext
        OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN,
                TestConstants.Clients.FB_GRP, TestConstants.Orgs.ESP_NORTE);
    }

    /**
     * Tests the doGet method of the {@link CopilotJwtServlet} class with a valid token.
     */
    @Test
    public void testDoGetWithValidToken() throws IOException {
        when(mockRequest.getHeader("Authorization")).thenReturn("Bearer " + OpenAIUtils.getOpenaiApiKey());
        doNothing().when(servlet).doGet(mockRequest, mockResponse);
        servlet.doGet(mockRequest, mockResponse);
    }

    /**
     * Tests the doGet method of the {@link CopilotJwtServlet} class with an invalid token.
     */
    @Test
    public void testDoGetWithInvalidToken() throws IOException {
        when(mockRequest.getHeader("Authorization")).thenReturn("Bearer invalid_token");
        servlet.doGet(mockRequest, mockResponse);
    }

    /**
     * Tests the {@link CopilotJwtServlet#doPost(HttpServletRequest, HttpServletResponse)} method with a valid token.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Test
    public void testDoPostWithValidToken() throws IOException {
        when(mockRequest.getHeader("Authorization")).thenReturn("Bearer " + OpenAIUtils.getOpenaiApiKey());
        doNothing().when(mockRestService).doPost(mockRequest, mockResponse);
        servlet.doPost(mockRequest, mockResponse);
    }

    /**
     * Tests the {@link CopilotJwtServlet#doPost(HttpServletRequest, HttpServletResponse)} method with an invalid token.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Test
    public void testDoPostWithInvalidToken() throws IOException {
        when(mockRequest.getHeader("Authorization")).thenReturn("Bearer invalid_token");
        servlet.doPost(mockRequest, mockResponse);
    }

    /**
     * Utility method to set private fields using reflection.
     */
    private void setPrivateField(Class<?> clazz, String fieldName, Object target, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
