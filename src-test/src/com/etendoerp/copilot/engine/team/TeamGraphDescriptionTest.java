/*
 *************************************************************************
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.team;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

public class TeamGraphDescriptionTest {
  @Test
  public void describesStartSupervisorMemberLoopAndEnd() throws Exception {
    JSONObject request = new JSONObject().put("name", "Test Supervisor")
        .put("assistants", new JSONArray().put(new JSONObject().put("name", "Invoice Checker")));

    String mermaid = TeamGraphDescription.fromRequest(request).toMermaid();

    assertTrue(mermaid.contains("__start__([\"__start__\"]) --> `TestSupervisor`"));
    assertTrue(mermaid.contains("`TestSupervisor` --> `InvoiceChecker`"));
    assertTrue(mermaid.contains("`InvoiceChecker` --> `TestSupervisor`"));
    assertTrue(mermaid.contains("[\"Test Supervisor\"]"));
    assertTrue(mermaid.contains("[\"Invoice Checker\"]"));
    assertTrue(mermaid.contains("`TestSupervisor` --> __end__"));
    assertFalse(mermaid.contains("tools["));
  }

  @Test
  public void includesTaskToolNodeOnlyWhenPythonAddsItsSupervisorTool() throws Exception {
    JSONObject request = new JSONObject().put("name", "Supervisor")
        .put("assistants", new JSONArray().put(new JSONObject().put("name", "Member")))
        .put("tools", new JSONArray().put(new JSONObject().put("function", new JSONObject()
            .put("name", "TaskManagementTool"))));

    String mermaid = TeamGraphDescription.fromRequest(request).toMermaid();

    assertTrue(mermaid.contains("`Supervisor` --> tools[\"tools\"]"));
    assertTrue(mermaid.contains("tools --> `Supervisor`"));
  }

  @Test
  public void supportsOneToThirtyMembersWithinDeterministicImageBounds() {
    int oneMember = TeamGraphDescription.imageHeightForMembers(1);
    int thirtyMembers = TeamGraphDescription.imageHeightForMembers(30);

    assertTrue(oneMember > 0);
    assertTrue(thirtyMembers > oneMember);
    assertEquals(oneMember + 29 * 82, thirtyMembers);
    assertThrows(IllegalArgumentException.class, () -> TeamGraphDescription.imageHeightForMembers(0));
    assertThrows(IllegalArgumentException.class, () -> TeamGraphDescription.imageHeightForMembers(31));
  }

  @Test
  public void rejectsNonTeamGraphLikePythonSupervisorConstruction() throws Exception {
    assertThrows(org.openbravo.base.exception.OBException.class,
        () -> TeamGraphDescription.fromRequest(new JSONObject().put("name", "No Members")));
  }
}
