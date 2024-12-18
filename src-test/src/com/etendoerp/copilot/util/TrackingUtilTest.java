import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.copilot.data.Conversation;
import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.Message;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class TrackingUtilTest {

    @Mock
    private OBDal obDal;

    @Mock
    private OBContext obContext;

    @Mock
    private Conversation mockConversation;

    @Mock
    private Message mockMessage;

    @Mock
    private CopilotApp mockApp;

    @InjectMocks
    private TrackingUtil trackingUtil;

    private MockedStatic<OBDal> obDalStaticMock;
    private MockedStatic<OBContext> obContextStaticMock;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        obDalStaticMock = mockStatic(OBDal.class);
        obContextStaticMock = mockStatic(OBContext.class);
        obDalStaticMock.when(OBDal::getInstance).thenReturn(obDal);
        obContextStaticMock.when(OBContext::getOBContext).thenReturn(obContext);
    }

    @After
    public void tearDown() {
        obDalStaticMock.close();
        obContextStaticMock.close();
    }

    @Test
    public void testGetConversation_createsNewConversationIfNotExists() {
        when(obDal.createQuery(eq(Conversation.class), anyString())).thenReturn(mock(Query.class));
        when(obDal.getInstance().createQuery(Conversation.class, "as c where c.externalID = :conversationId").setNamedParameter("conversationId", "123").setMaxResult(1).uniqueResult()).thenReturn(null);
        when(obDal.getInstance().save(any(Conversation.class))).thenReturn(null);

        Conversation conversation = trackingUtil.getInstance().getConversation("123", mockApp);

        assertNotNull(conversation);
        verify(obDal, times(1)).save(any(Conversation.class));
    }

    @Test
    public void testCreateMessage_createsMessageAndAssociatesToConversation() {
        when(obDal.createQuery(eq(Conversation.class), anyString())).thenReturn(mock(Query.class));
        when(obDal.getInstance().createQuery(Conversation.class, "as c where c.externalID = :conversationId").setNamedParameter("conversationId", "123").setMaxResult(1).uniqueResult()).thenReturn(mockConversation);
        when(obDal.createCriteria(Message.class)).thenReturn(mock(OBCriteria.class));

        trackingUtil.getInstance().createMessage("123", "user", "Hello", mockApp);

        verify(obDal, times(1)).save(any(Message.class));
    }

    @Test
    public void testTrackQuestion_registersUserQuestion() {
        trackingUtil.trackQuestion("123", "Hello", mockApp);
        verify(obDal, times(1)).save(any(Message.class));
    }

    @Test
    public void testTrackResponse_registersAssistantResponse() {
        trackingUtil.trackResponse("123", "Response", mockApp);
        verify(obDal, times(1)).save(any(Message.class));
    }

    @Test
    public void testTrackResponse_registersErrorResponse() {
        trackingUtil.trackResponse("123", "Error", mockApp, true);
        verify(obDal, times(1)).save(any(Message.class));
    }

    @Test
    public void testGetHistory_returnsMessageHistory() throws JSONException {
        when(obDal.createQuery(eq(Message.class), anyString())).thenReturn(mock(Query.class));
        when(obDal.getInstance().createQuery(Message.class, "as m where m.etcopConversation.externalID = :conversationId order by m.creationDate asc").setNamedParameter("conversationId", "123").list()).thenReturn(Collections.singletonList(mockMessage));
        when(mockMessage.getRole()).thenReturn("user");
        when(mockMessage.getMessage()).thenReturn("Hello");

        JSONArray history = TrackingUtil.getHistory("123");

        assertEquals(1, history.length());
        JSONObject message = history.getJSONObject(0);
        assertEquals("user", message.getString("role"));
        assertEquals("Hello", message.getString("content"));
    }
}
