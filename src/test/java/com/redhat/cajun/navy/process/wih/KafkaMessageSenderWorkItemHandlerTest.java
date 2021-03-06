package com.redhat.cajun.navy.process.wih;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cajun.navy.process.entity.OutboxEvent;
import com.redhat.cajun.navy.process.entity.OutboxEventEmitter;
import com.redhat.cajun.navy.process.message.model.CreateMissionCommand;
import com.redhat.cajun.navy.process.message.model.IncidentAssignmentEvent;
import com.redhat.cajun.navy.process.message.model.Message;
import com.redhat.cajun.navy.process.message.model.Responder;
import com.redhat.cajun.navy.process.message.model.UpdateIncidentCommand;
import com.redhat.cajun.navy.process.message.model.UpdateResponderCommand;
import com.redhat.cajun.navy.rules.model.Incident;
import com.redhat.cajun.navy.rules.model.Mission;
import com.redhat.cajun.navy.rules.model.Status;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.concurrent.SettableListenableFuture;


public class KafkaMessageSenderWorkItemHandlerTest {

    @Mock
    private KafkaTemplate<String, Message<?>> kafkaTemplate;

    @Mock
    private WorkItem workItem;

    @Mock
    private WorkItemManager workItemManager;

    @Mock
    private OutboxEventEmitter outboxEventEmitter;

    @Captor
    private ArgumentCaptor<Message<?>> messageCaptor;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    private KafkaMessageSenderWorkItemHandler wih;

    @Before
    public void setup() {
        initMocks(this);
        wih = new KafkaMessageSenderWorkItemHandler();
        setField(wih, null, kafkaTemplate, KafkaTemplate.class);
        setField(wih, "createMissionCommandDestination", "topic-mission-command", String.class);
        setField(wih, "updateResponderCommandDestination", "topic-responder-command", String.class);
        setField(wih, "updateIncidentCommandDestination", "topic-incident-command", String.class);
        setField(wih, "incidentAssignmentEventDestination", "topic-incident-event", String.class);
        setField(wih, null, outboxEventEmitter, OutboxEventEmitter.class);
        wih.init();
    }

    @Test
    public void testExecuteWorkItem() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("MessageType", "testPayloadType");
        when(workItem.getParameters()).thenReturn(parameters);
        when(workItem.getId()).thenReturn(1L);

        wih.addPayloadBuilder("testPayloadType", "testMessageType", "topic-test", TestMessageEvent::build);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(Message.class))).thenReturn(new SettableListenableFuture<>());

        wih.executeWorkItem(workItem, workItemManager);
        verify(workItemManager).completeWorkItem(eq(1L), anyMap());
        verify(kafkaTemplate).send(eq("topic-test"), eq("testKey"), any(Message.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateMissionCommandMessageType() {

        Mission mission = new Mission();
        mission.setIncidentId("incident123");
        mission.setIncidentLat(new BigDecimal("30.12345"));
        mission.setIncidentLong(new BigDecimal("-70.98765"));
        mission.setResponderId("responder123");
        mission.setResponderStartLat(new BigDecimal("40.12345"));
        mission.setResponderStartLong(new BigDecimal("-80.98765"));
        mission.setDestinationLat(new BigDecimal("50.12345"));
        mission.setDestinationLong(new BigDecimal("-90.98765"));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("MessageType", "CreateMission");
        parameters.put("Payload", mission);
        when(workItem.getParameters()).thenReturn(parameters);
        when(workItem.getId()).thenReturn(1L);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(Message.class))).thenReturn(new SettableListenableFuture<>());

        wih.executeWorkItem(workItem, workItemManager);
        verify(workItemManager).completeWorkItem(eq(1L), anyMap());
        verify(kafkaTemplate).send(eq("topic-mission-command"), eq("incident123"), messageCaptor.capture());

        Message<CreateMissionCommand> message = (Message<CreateMissionCommand>) messageCaptor.getValue();
        assertThat(message.getMessageType(), equalTo("CreateMissionCommand"));
        assertThat(message.getInvokingService(), equalTo("IncidentProcessService"));
        assertThat(message.getBody(), notNullValue());
        CreateMissionCommand cmd = message.getBody();
        assertThat(cmd.getIncidentId(), equalTo("incident123"));
        assertThat(cmd.getIncidentLat(), equalTo("30.12345"));
        assertThat(cmd.getIncidentLong(), equalTo("-70.98765"));
        assertThat(cmd.getResponderId(), equalTo("responder123"));
        assertThat(cmd.getResponderStartLat(), equalTo("40.12345"));
        assertThat(cmd.getResponderStartLong(), equalTo("-80.98765"));
        assertThat(cmd.getDestinationLat(), equalTo("50.12345"));
        assertThat(cmd.getDestinationLong(), equalTo("-90.98765"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSetResponderUnavailableMessageType() throws Exception {
        Mission mission = new Mission();
        mission.setIncidentId("incident123");
        mission.setIncidentLat(new BigDecimal("30.12345"));
        mission.setIncidentLong(new BigDecimal("-70.98765"));
        mission.setResponderId("responder123");
        mission.setResponderStartLat(new BigDecimal("40.12345"));
        mission.setResponderStartLong(new BigDecimal("-80.98765"));
        mission.setDestinationLat(new BigDecimal("50.12345"));
        mission.setDestinationLong(new BigDecimal("-90.98765"));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("MessageType", "SetResponderUnavailable");
        parameters.put("Payload", mission);
        when(workItem.getParameters()).thenReturn(parameters);
        when(workItem.getId()).thenReturn(1L);

        wih.executeWorkItem(workItem, workItemManager);
        verify(workItemManager).completeWorkItem(eq(1L), anyMap());
        verify(outboxEventEmitter).emitEvent(outboxEventCaptor.capture());

        OutboxEvent outboxEvent = outboxEventCaptor.getValue();
        assertThat(outboxEvent.getAggregateType(), equalTo("responder-command"));
        assertThat(outboxEvent.getAggregateId(), equalTo("responder123"));
        assertThat(outboxEvent.getType(), equalTo("UpdateResponderCommand"));
        String messageAsJson = outboxEvent.getPayload();
        Message<UpdateResponderCommand> message = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(messageAsJson, new TypeReference<Message<UpdateResponderCommand>>() {});
        assertThat(message.getMessageType(), equalTo("UpdateResponderCommand"));
        assertThat(message.getInvokingService(), equalTo("IncidentProcessService"));
        assertThat(message.getHeaderValue("incidentId"), equalTo("incident123"));
        assertThat(message.getBody(), notNullValue());
        UpdateResponderCommand command = message.getBody();
        assertThat(command, notNullValue());
        Responder responder = command.getResponder();
        assertThat(responder, notNullValue());
        assertThat(responder.getId(), equalTo("responder123"));
        assertThat(responder.isAvailable(), equalTo(false));
        assertThat(responder.getName(), nullValue());
        assertThat(responder.getPhoneNumber(), nullValue());
        assertThat(responder.getLatitude(), nullValue());
        assertThat(responder.getLongitude(), nullValue());
        assertThat(responder.getBoatCapacity(), nullValue());
        assertThat(responder.isMedicalKit(), nullValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateIncidentMessageTypeStatusAssigned() {
        Incident incident = new Incident();
        incident.setId("incident123");
        incident.setStatus("Assigned");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("MessageType", "UpdateIncident");
        parameters.put("Payload", incident);
        when(workItem.getParameters()).thenReturn(parameters);
        when(workItem.getId()).thenReturn(1L);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(Message.class))).thenReturn(new SettableListenableFuture<>());

        wih.executeWorkItem(workItem, workItemManager);
        verify(workItemManager).completeWorkItem(eq(1L), anyMap());
        verify(kafkaTemplate).send(eq("topic-incident-command"), eq("incident123"), messageCaptor.capture());

        Message<UpdateIncidentCommand> message = (Message<UpdateIncidentCommand>) messageCaptor.getValue();
        assertThat(message.getMessageType(), equalTo("UpdateIncidentCommand"));
        assertThat(message.getInvokingService(), equalTo("IncidentProcessService"));
        assertThat(message.getBody(), notNullValue());
        UpdateIncidentCommand command = message.getBody();
        assertThat(command, notNullValue());
        com.redhat.cajun.navy.process.message.model.Incident toUpdate = command.getIncident();
        assertThat(toUpdate, notNullValue());
        assertThat(toUpdate.getId(), equalTo("incident123"));
        assertThat(toUpdate.getStatus(), equalTo("ASSIGNED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateIncidentMessageTypeStatusPickedUp() {
        Incident incident = new Incident();
        incident.setId("incident123");
        incident.setStatus("PickedUp");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("MessageType", "UpdateIncident");
        parameters.put("Payload", incident);
        when(workItem.getParameters()).thenReturn(parameters);
        when(workItem.getId()).thenReturn(1L);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(Message.class))).thenReturn(new SettableListenableFuture<>());

        wih.executeWorkItem(workItem, workItemManager);
        verify(workItemManager).completeWorkItem(eq(1L), anyMap());
        verify(kafkaTemplate).send(eq("topic-incident-command"), eq("incident123"), messageCaptor.capture());

        Message<UpdateIncidentCommand> message = (Message<UpdateIncidentCommand>) messageCaptor.getValue();
        assertThat(message.getMessageType(), equalTo("UpdateIncidentCommand"));
        assertThat(message.getInvokingService(), equalTo("IncidentProcessService"));
        assertThat(message.getBody(), notNullValue());
        UpdateIncidentCommand command = message.getBody();
        assertThat(command, notNullValue());
        com.redhat.cajun.navy.process.message.model.Incident toUpdate = command.getIncident();
        assertThat(toUpdate, notNullValue());
        assertThat(toUpdate.getId(), equalTo("incident123"));
        assertThat(toUpdate.getStatus(), equalTo("PICKEDUP"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateIncidentMessageTypeStatusDelivered() {
        Incident incident = new Incident();
        incident.setId("incident123");
        incident.setStatus("Delivered");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("MessageType", "UpdateIncident");
        parameters.put("Payload", incident);
        when(workItem.getParameters()).thenReturn(parameters);
        when(workItem.getId()).thenReturn(1L);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(Message.class))).thenReturn(new SettableListenableFuture<>());

        wih.executeWorkItem(workItem, workItemManager);
        verify(workItemManager).completeWorkItem(eq(1L), anyMap());
        verify(kafkaTemplate).send(eq("topic-incident-command"), eq("incident123"), messageCaptor.capture());

        Message<UpdateIncidentCommand> message = (Message<UpdateIncidentCommand>) messageCaptor.getValue();
        assertThat(message.getMessageType(), equalTo("UpdateIncidentCommand"));
        assertThat(message.getInvokingService(), equalTo("IncidentProcessService"));
        assertThat(message.getBody(), notNullValue());
        UpdateIncidentCommand command = message.getBody();
        assertThat(command, notNullValue());
        com.redhat.cajun.navy.process.message.model.Incident toUpdate = command.getIncident();
        assertThat(toUpdate, notNullValue());
        assertThat(toUpdate.getId(), equalTo("incident123"));
        assertThat(toUpdate.getStatus(), equalTo("RESCUED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIncidentAssignmentEventMessageTypeStatusAssigned() {
        Mission mission = new Mission();
        mission.setIncidentId("incident123");
        mission.setIncidentLat(new BigDecimal("30.12345"));
        mission.setIncidentLong(new BigDecimal("-70.98765"));
        mission.setResponderId("responder123");
        mission.setResponderStartLat(new BigDecimal("40.12345"));
        mission.setResponderStartLong(new BigDecimal("-80.98765"));
        mission.setDestinationLat(new BigDecimal("50.12345"));
        mission.setDestinationLong(new BigDecimal("-90.98765"));
        mission.setStatus(Status.ASSIGNED);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("MessageType", "IncidentAssignment");
        parameters.put("Payload", mission);
        when(workItem.getParameters()).thenReturn(parameters);
        when(workItem.getId()).thenReturn(1L);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(Message.class))).thenReturn(new SettableListenableFuture<>());

        wih.executeWorkItem(workItem, workItemManager);
        verify(workItemManager).completeWorkItem(eq(1L), anyMap());
        verify(kafkaTemplate).send(eq("topic-incident-event"), eq("incident123"), messageCaptor.capture());

        Message<IncidentAssignmentEvent> message = (Message<IncidentAssignmentEvent>) messageCaptor.getValue();
        assertThat(message.getMessageType(), equalTo("IncidentAssignmentEvent"));
        assertThat(message.getInvokingService(), equalTo("IncidentProcessService"));
        assertThat(message.getBody(), notNullValue());
        IncidentAssignmentEvent event = message.getBody();
        assertThat(event, notNullValue());
        assertThat(event.getIncidentId(), equalTo("incident123"));
        assertThat(event.getAssignment(), equalTo(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIncidentAssignmentEventMessageTypeStatusUnassigned() {
        Mission mission = new Mission();
        mission.setIncidentId("incident123");
        mission.setIncidentLat(new BigDecimal("30.12345"));
        mission.setIncidentLong(new BigDecimal("-70.98765"));
        mission.setResponderId("responder123");
        mission.setResponderStartLat(new BigDecimal("40.12345"));
        mission.setResponderStartLong(new BigDecimal("-80.98765"));
        mission.setDestinationLat(new BigDecimal("50.12345"));
        mission.setDestinationLong(new BigDecimal("-90.98765"));
        mission.setStatus(Status.UNASSIGNED);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("MessageType", "IncidentAssignment");
        parameters.put("Payload", mission);
        when(workItem.getParameters()).thenReturn(parameters);
        when(workItem.getId()).thenReturn(1L);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(Message.class))).thenReturn(new SettableListenableFuture<>());

        wih.executeWorkItem(workItem, workItemManager);
        verify(workItemManager).completeWorkItem(eq(1L), anyMap());
        verify(kafkaTemplate).send(eq("topic-incident-event"), eq("incident123"), messageCaptor.capture());

        Message<IncidentAssignmentEvent> message = (Message<IncidentAssignmentEvent>) messageCaptor.getValue();
        assertThat(message.getMessageType(), equalTo("IncidentAssignmentEvent"));
        assertThat(message.getInvokingService(), equalTo("IncidentProcessService"));
        assertThat(message.getBody(), notNullValue());
        IncidentAssignmentEvent event = message.getBody();
        assertThat(event, notNullValue());
        assertThat(event.getIncidentId(), equalTo("incident123"));
        assertThat(event.getAssignment(), equalTo(false));
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class TestMessageEvent {

        static Pair<String, Message<?>> build(String messageType, Map<String, Object> parameters) {
            TestMessageEvent event = new TestMessageEvent();
            Message<TestMessageEvent> message = new Message.Builder<>(messageType, "testservice", event).build();
            return new ImmutablePair<>("testKey", message);
        }

    }

}
