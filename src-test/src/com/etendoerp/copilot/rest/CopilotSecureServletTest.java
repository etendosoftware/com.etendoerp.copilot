package com.etendoerp.copilot.rest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CopilotSecureServletTest {

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @Mock
    private RestService mockRestService;

    @InjectMocks
    private CopilotSecureServlet copilotSecureServlet;

    @Before
    public void setUp() {
        // Set up the RestService mock to be used by the servlet
        CopilotSecureServlet.setInstance(mockRestService);
    }

    @Test
    public void testDoGet_callsRestServiceDoGet() throws IOException {
        // When
        copilotSecureServlet.doGet(mockRequest, mockResponse);

        // Then
        verify(mockRestService, times(1)).doGet(mockRequest, mockResponse);
    }

    @Test
    public void testDoPost_callsRestServiceDoPost() throws IOException {
        // When
        copilotSecureServlet.doPost(mockRequest, mockResponse);

        // Then
        verify(mockRestService, times(1)).doPost(mockRequest, mockResponse);
    }
}
