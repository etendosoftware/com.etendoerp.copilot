package com.etendoerp.copilot.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotRoleApp;

/**
 * Utility for granting shared Copilot agents to a client-admin role.
 *
 * <p>An agent is "shared" (visible to every client-admin role) iff its agent scope is
 * Client-System ({@value #SCOPE_CLIENT_SYSTEM}) or Client ({@value #SCOPE_CLIENT}), it is
 * flagged to sync on startup, and it is active. System-scope agents are excluded.</p>
 *
 * <p>This is invoked lazily from {@code RestServiceUtil.handleAssistants} (the
 * {@code /sws/copilot/assistants} endpoint) so a client that was onboarded after the last
 * Tomcat startup — and therefore never got its grants from
 * {@code CopilotSyncStartup} — receives them the first time it opens Copilot. The role is
 * fully persistent at request time, so there is no transient-entity problem, and the
 * operation is idempotent and short-circuited so the steady-state cost is a single query.</p>
 */
public final class AgentAccessUtils {

  /** Agent scope value "Client" — visible to client-level roles. */
  public static final String SCOPE_CLIENT = "C";
  /** Agent scope value "Client-System" — visible to both system and client-level roles. */
  public static final String SCOPE_CLIENT_SYSTEM = "CS";

  /** System client id — client-admin roles of the System client are never granted here. */
  private static final String SYSTEM_CLIENT_ID = "0";

  private AgentAccessUtils() {
  }

  /**
   * Grants every missing shared agent to {@code role}, if it is an eligible client-admin role.
   * Idempotent: existing grants are left untouched. Short-circuited: when the role already has
   * every shared agent (the steady state) nothing is written and the session is not flushed.
   *
   * @param role the role that just requested its assistants
   * @return the number of new grants created (0 when the role is not eligible or already complete)
   */
  public static int ensureSharedAgentsGranted(Role role) {
    if (!isEligibleRole(role)) {
      return 0;
    }
    List<CopilotApp> shared = getSharedStartupAgents();
    if (shared.isEmpty()) {
      return 0;
    }
    Set<String> grantedAgentIds = getGrantedAgentIds(role);
    int created = 0;
    for (CopilotApp app : shared) {
      if (!grantedAgentIds.contains(app.getId())) {
        createGrant(app, role);
        created++;
      }
    }
    if (created > 0) {
      OBDal.getInstance().flush();
    }
    return created;
  }

  /**
   * A role is eligible for shared-agent grants when it is an active client-admin role that does
   * not belong to the System client.
   *
   * @param role the role to test
   * @return {@code true} when the role should receive the shared agents
   */
  private static boolean isEligibleRole(Role role) {
    return role != null
        && Boolean.TRUE.equals(role.isClientAdmin())
        && Boolean.TRUE.equals(role.isActive())
        && role.getClient() != null
        && !SYSTEM_CLIENT_ID.equals(role.getClient().getId());
  }

  /**
   * Returns every {@link CopilotApp} that must be shared with client-admin roles:
   * agent scope in ({@value #SCOPE_CLIENT_SYSTEM}, {@value #SCOPE_CLIENT}), sync-on-startup, active.
   *
   * @return the list of shared agents (never {@code null})
   */
  public static List<CopilotApp> getSharedStartupAgents() {
    OBCriteria<CopilotApp> crit = OBDal.getInstance().createCriteria(CopilotApp.class);
    crit.add(Restrictions.in(CopilotApp.PROPERTY_AGENTSCOPE,
        Arrays.asList(SCOPE_CLIENT_SYSTEM, SCOPE_CLIENT)));
    crit.add(Restrictions.eq(CopilotApp.PROPERTY_SYNCSTARTUP, Boolean.TRUE));
    crit.add(Restrictions.eq(CopilotApp.PROPERTY_ACTIVE, Boolean.TRUE));
    return crit.list();
  }

  /**
   * Returns the ids of the agents already granted to {@code role}.
   *
   * @param role the role whose grants are read
   * @return the set of granted {@link CopilotApp} ids (never {@code null})
   */
  private static Set<String> getGrantedAgentIds(Role role) {
    OBCriteria<CopilotRoleApp> crit = OBDal.getInstance().createCriteria(CopilotRoleApp.class);
    crit.add(Restrictions.eq(CopilotRoleApp.PROPERTY_ROLE, role));
    Set<String> ids = new HashSet<>();
    for (CopilotRoleApp roleApp : crit.list()) {
      if (roleApp.getCopilotApp() != null) {
        ids.add(roleApp.getCopilotApp().getId());
      }
    }
    return ids;
  }

  /**
   * Creates and saves a {@link CopilotRoleApp} grant linking {@code app} to {@code role},
   * inheriting the role's client and organization.
   *
   * @param app  the agent to grant
   * @param role the role receiving access
   */
  private static void createGrant(CopilotApp app, Role role) {
    CopilotRoleApp roleApp = OBProvider.getInstance().get(CopilotRoleApp.class);
    roleApp.setClient(role.getClient());
    roleApp.setOrganization(role.getOrganization());
    roleApp.setCopilotApp(app);
    roleApp.setRole(role);
    OBDal.getInstance().save(roleApp);
  }
}
