package com.etendoerp.copilot.hook;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.RoleInheritance;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.role.inheritance.RoleInheritanceManager;

import com.etendoerp.copilot.data.AgentMemory;
import com.etendoerp.copilot.data.CopilotApp;

/**
 * Hook implementation for retrieving agent memory data to enhance AI prompts with relevant context.
 *
 * <p>This hook is responsible for querying and filtering agent memory records based on the current
 * user's context, including their organization hierarchy, role inheritance, and access permissions.
 * The retrieved memory data is then formatted as additional context for AI prompts to provide
 * more relevant and personalized responses.</p>
 *
 * <p>The filtering logic ensures that only appropriate memory records are included based on:</p>
 * <ul>
 *   <li>Agent ID - must match exactly</li>
 *   <li>User context - includes records for the current user or global records (null user)</li>
 *   <li>Role hierarchy - includes records for current role and inherited roles, or global records (null role)</li>
 *   <li>Organization hierarchy - includes records for current organization and parent organizations, or global records (null org)</li>
 * </ul>
 *
 * @author Etendo Software
 * @since 1.0.0
 */
public class AgentMemoryHook implements OpenAIPromptHook {
  /**
   * Role inheritance manager for traversing role hierarchies
   */
  @Inject
  private RoleInheritanceManager manager;

  /**
   * Logger instance for this class
   */
  private static final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager
      .getLogger();

  /**
   * Executes the agent memory hook to retrieve relevant memory context for AI prompts.
   *
   * <p>This method performs the following operations:</p>
   * <ol>
   *   <li>Retrieves the current user's organizational context and builds a hierarchy of accessible organizations</li>
   *   <li>Determines all roles accessible to the current user through role inheritance</li>
   *   <li>Constructs and executes an HQL query to find relevant agent memory records</li>
   *   <li>Formats the retrieved memory data as contextual information for the AI prompt</li>
   * </ol>
   *
   * <p>The query filters memory records using the following criteria:</p>
   * <ul>
   *   <li><strong>Agent ID:</strong> Must match the provided app's agent ID exactly</li>
   *   <li><strong>User Context:</strong> Includes records assigned to the current user or global records (null user)</li>
   *   <li><strong>Role Context:</strong> Includes records assigned to any of the user's roles (including inherited) or global records (null role)</li>
   *   <li><strong>Organization Context:</strong> Includes records assigned to any organization in the user's hierarchy or global records (null organization)</li>
   * </ul>
   *
   * @param app
   *     The CopilotApp instance containing the agent configuration
   * @return A formatted string containing relevant memory context for the AI prompt, or empty string if no relevant memories found or if an error occurs
   * @throws OBException
   *     if there's an error during the execution process
   */
  @Override
  public String exec(CopilotApp app) throws OBException {
    try {
      // Obtain the organization structure provider
      OBContext obContext = OBContext.getOBContext();
      OrganizationStructureProvider osp = obContext
          .getOrganizationStructureProvider();
      List<Organization> orgList = osp.getParentList(obContext.getCurrentOrganization().getId(),
          true).stream().map(
          orgId -> OBDal.getInstance().get(Organization.class, orgId)).collect(Collectors.toList());
      var orgIds = orgList.stream().map(Organization::getId).collect(Collectors.toSet());
      User user = obContext.getUser();
      Role currentRole = obContext.getRole();

      Set<String> roleIds = new HashSet<>();
      if (currentRole != null) {
        Set<Role> roles = getRolesByInheritance(currentRole);
        roleIds = roles.stream().map(Role::getId).collect(Collectors.toSet());
      }

      // Build the HQL query
      StringBuilder hql = new StringBuilder();
      hql.append("select am from etcop_memory as am where ");

      // Agent filter: must match exactly
      hql.append("am.agent.id = :agentId ");

      // User filter: if userContact is set it must be the current user; if null, include
      hql.append("and (am.userContact.id = :userId or am.userContact is null) ");

      // Role filter: if role is set it must be in my list; if null, include
      hql.append("and (am.role.id in :roleIds or am.role is null) ");

      // Organization filter: if org is set it must be in the parent list; if null, include
      hql.append("and (am.organization.id in :orgIds or am.organization is null)");

      Query<AgentMemory> query = OBDal.getInstance()
          .getSession()
          .createQuery(hql.toString(), AgentMemory.class)
          .setParameter("agentId", app.getId())
          .setParameter("userId", user.getId())
          .setParameterList("roleIds", roleIds)
          .setParameterList("orgIds", orgIds);

      List<AgentMemory> results = query.list();
      if (results.isEmpty()) {
        return "";
      }
      StringBuilder memoryContent = new StringBuilder();
      memoryContent.append("Use the following relevant previous information to answer the user request:\n");
      for (AgentMemory am : results) {
        memoryContent.append(String.format("- %s%n", am.getTextField()));
      }
      return memoryContent.toString();
    } catch (Exception e) {
      log.error("Error executing AgentMemoryHook", e);
      return "";
    }
  }

  /**
   * Retrieves all roles accessible to a given role through the role inheritance hierarchy.
   *
   * <p>This method performs a breadth-first traversal of the role inheritance tree starting
   * from the provided child role. It collects all roles that the child role inherits from,
   * including transitive inheritance relationships.</p>
   *
   * <p>The algorithm works as follows:</p>
   * <ol>
   *   <li>Starts with the provided child role and marks it as visited</li>
   *   <li>For each role in the queue, examines all active role inheritance relationships</li>
   *   <li>Adds parent roles that haven't been visited yet to both the visited set and the processing queue</li>
   *   <li>Continues until all reachable roles have been processed</li>
   * </ol>
   *
   * <p><strong>Note:</strong> Only active role inheritance relationships are considered.
   * Inactive inheritances are ignored to ensure proper access control.</p>
   *
   * @param childRole
   *     The starting role for which to find all inherited roles
   * @return A Set containing the child role and all roles it inherits from (directly or transitively)
   */
  private Set<Role> getRolesByInheritance(Role childRole) {
    if (childRole == null) {
      return new HashSet<>();
    }

    Set<Role> visited = new HashSet<>();
    Queue<Role> queue = new LinkedList<>();

    visited.add(childRole);
    queue.add(childRole);

    while (!queue.isEmpty()) {
      Role currentRole = queue.poll();
      currentRole = OBDal.getInstance().get(Role.class, currentRole.getId());
      OBDal.getInstance().refresh(currentRole);
      for (RoleInheritance ri : currentRole.getADRoleInheritanceList()) {
        if (Boolean.TRUE.equals(ri.isActive())) {
          Role parent = ri.getInheritFrom();

          if (!visited.contains(parent)) {
            visited.add(parent);
            queue.add(ri.getInheritFrom());
          }
        }
      }
    }

    return visited;
  }

  /**
   * Performs a type check to determine if this hook should be executed for the given app.
   *
   * <p>This implementation always returns {@code true}, indicating that the agent memory
   * hook should be executed for all CopilotApp instances regardless of their configuration.
   * This ensures that memory context is always available when processing AI prompts.</p>
   *
   * <p>Subclasses can override this method to implement more specific type checking logic
   * if needed, such as checking for specific agent types or configurations.</p>
   *
   * @param app
   *     The CopilotApp instance to check
   * @return {@code true} always, indicating this hook should be executed for all apps
   */
  @Override
  public boolean typeCheck(CopilotApp app) {
    return true;
  }
}
