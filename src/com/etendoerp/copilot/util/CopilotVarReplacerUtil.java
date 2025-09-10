package com.etendoerp.copilot.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;

import com.etendoerp.copilot.data.CopilotApiToken;

/**
 * CopilotVarReplacerUtil is a utility class that provides methods for replacing
 * variables and placeholders in Copilot prompts and strings.
 */
public class CopilotVarReplacerUtil {

  private static final Logger log = LogManager.getLogger(CopilotVarReplacerUtil.class);

  private CopilotVarReplacerUtil() {
    // Private constructor to prevent instantiation
  }

  /**
   * Replaces Copilot prompt variables in the given string.
   * <p>
   * This method replaces placeholders in the provided string with their corresponding
   * values. It uses a default empty {@link JSONObject} for variable mappings.
   * If an error occurs during the replacement process, a {@link RuntimeException} is thrown.
   *
   * @param string
   *     The input string containing placeholders to be replaced.
   * @return A {@link String} with the placeholders replaced by their corresponding values.
   * @throws RuntimeException
   *     If a {@link JSONException} occurs during the replacement process.
   */
  public static String replaceCopilotPromptVariables(String string) {
    try {
      return replaceCopilotPromptVariables(string, new JSONObject(), true);
    } catch (JSONException e) {
      throw new OBException(e);
    }
  }

  /**
   * Replaces system placeholders, context variables, and custom mappings in a string.
   * <p>
   * Replaces {@code @ETENDO_HOST@}, {@code @AD_CLIENT_ID@}, {@code @source.path@}, and other
   * system/context placeholders, plus custom variables from the maps parameter.
   * API tokens are also replaced using the pattern {@code @TOKEN_ALIAS@} based on user/role priority.
   * Optionally escapes and validates curly braces if {@code balanceBrackets} is true.
   *
   * @param string
   *     The input string containing placeholders to replace
   * @param maps
   *     Custom key-value pairs to replace (String/Boolean values only), can be null
   * @param balanceBrackets
   *     If true, escapes curly braces and validates bracket balance
   * @return The string with all placeholders replaced
   * @throws JSONException 
   *     If an error occurs while parsing the JSON object
   * @throws OBException 
   *     If bracket balancing is enabled and brackets are not balanced
   */
  public static String replaceCopilotPromptVariables(String string, JSONObject maps, boolean balanceBrackets) throws JSONException {
    OBContext obContext = OBContext.getOBContext();
    String stringParsed = StringUtils.replace(string, "@ETENDO_HOST@", CopilotUtils.getEtendoHost());
    stringParsed = StringUtils.replace(stringParsed, "@ETENDO_HOST_DOCKER@", CopilotUtils.getEtendoHostDocker());

    if (obContext.getCurrentClient() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@AD_CLIENT_ID@", obContext.getCurrentClient().getId());
      stringParsed = StringUtils.replace(stringParsed, "@CLIENT_NAME@", obContext.getCurrentClient().getName());
    }
    if (obContext.getCurrentOrganization() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@AD_ORG_ID@", obContext.getCurrentOrganization().getId());
      stringParsed = StringUtils.replace(stringParsed, "@ORG_NAME@", obContext.getCurrentOrganization().getName());
    }
    if (obContext.getUser() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@AD_USER_ID@", obContext.getUser().getId());
      stringParsed = StringUtils.replace(stringParsed, "@USERNAME@", obContext.getUser().getUsername());
    }
    if (obContext.getRole() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@AD_ROLE_ID@", obContext.getRole().getId());
      stringParsed = StringUtils.replace(stringParsed, "@ROLE_NAME@", obContext.getRole().getName());
    }
    if (obContext.getWarehouse() != null) {
      stringParsed = StringUtils.replace(stringParsed, "@M_WAREHOUSE_ID@", obContext.getWarehouse().getId());
      stringParsed = StringUtils.replace(stringParsed, "@WAREHOUSE_NAME@", obContext.getWarehouse().getName());
    }
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    stringParsed = StringUtils.replace(stringParsed, "@source.path@", CopilotUtils.getSourcesPath(properties));

    // Replace API tokens with priority: user+role > user > role > null user and role
    Map<String, String> apiTokens = getApiTokensForCurrentContext(obContext);
    for (Map.Entry<String, String> tokenEntry : apiTokens.entrySet()) {
      String tokenPlaceholder = "@" + tokenEntry.getKey().toUpperCase() + "@";
      stringParsed = StringUtils.replace(stringParsed, tokenPlaceholder, tokenEntry.getValue());
    }

    if (maps != null) {
      Map<String, String> replacements = extractReplacementsFromJson(maps);
      StrSubstitutor sub = new StrSubstitutor(replacements);
      stringParsed = sub.replace(stringParsed);
    }

    if (balanceBrackets) {
      stringParsed = stringParsed.replace("{", "{{").replace("}", "}}");

      if (StringUtils.countMatches(stringParsed, "{{") != StringUtils.countMatches(stringParsed, "}}")) {
        throw new OBException(OBMessageUtils.messageBD("ETCOP_BalancedBrackets"));
      }
    }

    return stringParsed;
  }

  /**
   * Retrieves API tokens for the current context with priority: user+role > user > role > null user and role.
   * <p>
   * This method searches for API tokens based on the current user and role from the OBContext.
   * It implements a priority system where tokens with both user and role specified have the highest priority,
   * followed by user-only tokens, then role-only tokens, and finally tokens with null user and role.
   * Each alias can have only one token returned, with higher priority tokens taking precedence.
   *
   * @param obContext The current OBContext containing user and role information
   * @return A map where keys are token aliases and values are the corresponding tokens
   */
  private static Map<String, String> getApiTokensForCurrentContext(OBContext obContext) {
    Map<String, String> tokenMap = new HashMap<>();
    
    try {
      User currentUser = obContext.getUser();
      Role currentRole = obContext.getRole();
      
      // Create criteria to get all active tokens for the current client
      OBCriteria<CopilotApiToken> criteria = OBDal.getInstance().createCriteria(CopilotApiToken.class);
      criteria.add(Restrictions.eq(CopilotApiToken.PROPERTY_ACTIVE, true));
      
      if (obContext.getCurrentClient() != null) {
        criteria.add(Restrictions.eq(CopilotApiToken.PROPERTY_CLIENT + ".id", obContext.getCurrentClient().getId()));
      }
      
      List<CopilotApiToken> allTokens = criteria.list();
      
      // Group tokens by alias for priority processing
      Map<String, List<CopilotApiToken>> tokensByAlias = allTokens.stream()
          .filter(token -> StringUtils.isNotEmpty(token.getAlias()))
          .collect(Collectors.groupingBy(CopilotApiToken::getAlias));
      
      // Process each alias group with priority
      for (Map.Entry<String, List<CopilotApiToken>> entry : tokensByAlias.entrySet()) {
        String alias = entry.getKey();
        List<CopilotApiToken> tokensForAlias = entry.getValue();
        
        CopilotApiToken selectedToken = selectTokenByPriority(tokensForAlias, currentUser, currentRole);
        if (selectedToken != null && StringUtils.isNotEmpty(selectedToken.getToken())) {
          tokenMap.put(alias, selectedToken.getToken());
        }
      }
      
    } catch (Exception e) {
      log.error("Error retrieving API tokens for current context", e);
    }
    
    return tokenMap;
  }

  /**
   * Selects the token with the highest priority from a list of tokens for the same alias.
   * Priority order: user+role > user > role > null user and role
   *
   * @param tokens List of tokens with the same alias
   * @param currentUser Current user from context
   * @param currentRole Current role from context
   * @return The token with the highest priority, or null if none found
   */
  private static CopilotApiToken selectTokenByPriority(List<CopilotApiToken> tokens, User currentUser, Role currentRole) {
    CopilotApiToken userRoleToken = null;
    CopilotApiToken userToken = null;
    CopilotApiToken roleToken = null;
    CopilotApiToken nullToken = null;
    
    for (CopilotApiToken token : tokens) {
      User tokenUser = token.getUserContact();
      Role tokenRole = token.getRole();
      
      // Priority 1: user+role match
      if (currentUser != null && currentRole != null && 
          tokenUser != null && tokenRole != null &&
          currentUser.getId().equals(tokenUser.getId()) && 
          currentRole.getId().equals(tokenRole.getId())) {
        userRoleToken = token;
      }
      // Priority 2: user match only (role is null)
      else if (currentUser != null && 
               tokenUser != null && tokenRole == null &&
               currentUser.getId().equals(tokenUser.getId())) {
        userToken = token;
      }
      // Priority 3: role match only (user is null)
      else if (currentRole != null && 
               tokenUser == null && tokenRole != null &&
               currentRole.getId().equals(tokenRole.getId())) {
        roleToken = token;
      }
      // Priority 4: both user and role are null
      else if (tokenUser == null && tokenRole == null) {
        nullToken = token;
      }
    }
    
    // Return token based on priority
    if (userRoleToken != null) return userRoleToken;
    if (userToken != null) return userToken;
    if (roleToken != null) return roleToken;
    return nullToken;
  }

  /**
   * Extracts replacements from a JSONObject for use with StrSubstitutor.
   * <p>
   * This method iterates through the keys of the provided JSONObject and extracts
   * key-value pairs where the value is either a String or Boolean. The extracted
   * pairs are converted to String representations and returned as a Map.
   *
   * @param maps The JSONObject containing the key-value mappings
   * @return A Map of String keys to String values for replacement operations
   * @throws JSONException If an error occurs while accessing the JSON data
   */
  private static Map<String, String> extractReplacementsFromJson(JSONObject maps) throws JSONException {
    Map<String, String> replacements = new HashMap<>();
    Iterator<String> keys = maps.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = maps.get(key);
      if (value instanceof String || value instanceof Boolean) {
        replacements.put(key, value.toString());
      }
    }
    return replacements;
  }
}
