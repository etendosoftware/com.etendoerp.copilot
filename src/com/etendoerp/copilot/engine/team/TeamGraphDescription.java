/*
 *************************************************************************
 * All portions are Copyright © 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 *************************************************************************
 */
package com.etendoerp.copilot.engine.team;

import java.awt.AWTError;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;

/** Pure description and in-process PNG renderer for the Python LangGraph supervisor topology. */
public final class TeamGraphDescription {
  private static final int MAX_MEMBERS = 30;
  private static final int WIDTH = 900;
  private static final int TOP = 28;
  private static final int NODE_WIDTH = 210;
  private static final int NODE_HEIGHT = 58;
  private static final int MEMBER_GAP = 82;
  private static final int BOTTOM = 35;
  private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
  private static final Font BOLD_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
  private static final Pattern VALID_NODE = Pattern.compile("[A-Za-z0-9_-]{1,63}");
  private final String supervisor;
  private final String supervisorLabel;
  private final List<String> members;
  private final List<String> memberLabels;
  private final boolean hasTaskTool;

  private TeamGraphDescription(String supervisor, String supervisorLabel, List<String> members,
      List<String> memberLabels, boolean hasTaskTool) {
    this.supervisor = supervisor;
    this.supervisorLabel = supervisorLabel;
    this.members = Collections.unmodifiableList(new ArrayList<>(members));
    this.memberLabels = Collections.unmodifiableList(new ArrayList<>(memberLabels));
    this.hasTaskTool = hasTaskTool;
  }

  /** Build from the same request payload that Python's GraphQuestionSchema consumes. */
  public static TeamGraphDescription fromRequest(JSONObject request) throws JSONException {
    if (request == null) throw new IllegalArgumentException("Graph request is required.");
    JSONArray assistants = request.optJSONArray("assistants");
    if (assistants == null || assistants.length() == 0) {
      throw new OBException("Python's graph route requires a team with at least one assistant; no graph image was generated.");
    }
    if (assistants.length() > MAX_MEMBERS) {
      throw new OBException("Team graph images support at most " + MAX_MEMBERS + " members.");
    }
    String teamName = request.optString("name", "Supervisor");
    String supervisor = codify(teamName);
    String supervisorLabel = request.optString("display_name", teamName);
    List<String> members = new ArrayList<>(assistants.length());
    List<String> memberLabels = new ArrayList<>(assistants.length());
    for (int i = 0; i < assistants.length(); i++) {
      JSONObject assistant = assistants.getJSONObject(i);
      if ("openai-assistant".equalsIgnoreCase(assistant.optString("type", ""))) {
        throw new OBException("Python's graph runtime does not support OpenAI Assistant team members.");
      }
      String memberName = assistant.optString("name", "Member " + (i + 1));
      members.add(codify(memberName));
      memberLabels.add(assistant.optString("display_name", memberName));
    }
    boolean taskTool = false;
    JSONArray tools = request.optJSONArray("tools");
    if (tools != null) {
      for (int i = 0; i < tools.length(); i++) {
        JSONObject tool = tools.optJSONObject(i);
        JSONObject function = tool == null ? null : tool.optJSONObject("function");
        if (function != null && "TaskManagementTool".equals(function.optString("name"))) taskTool = true;
      }
    }
    return new TeamGraphDescription(supervisor, supervisorLabel, members, memberLabels, taskTool);
  }

  /** Mermaid equivalent of the create_supervisor compiled graph: start, supervisor, members, end. */
  public String toMermaid() {
    StringBuilder result = new StringBuilder("flowchart TD\n  __start__([\"__start__\"]) --> ")
        .append(quote(supervisor)).append("[\"").append(escape(supervisorLabel)).append("\"]\n");
    result.append("  ").append(quote(supervisor)).append(" --> __end__([\"__end__\"])\n");
    for (int i = 0; i < members.size(); i++) {
      String id = quote(members.get(i));
      result.append("  ").append(quote(supervisor)).append(" --> ").append(id).append("[\"")
          .append(escape(memberLabels.get(i))).append("\"]\n  ").append(id).append(" --> ").append(quote(supervisor)).append('\n');
    }
    if (hasTaskTool) {
      result.append("  ").append(quote(supervisor)).append(" --> tools[\"tools\"]\n  tools --> ")
          .append(quote(supervisor)).append('\n');
    }
    return result.toString();
  }

  /** Render a deterministic, self-contained PNG and return its base64 representation. */
  public String toPngBase64() {
    if (System.getProperty("java.awt.headless") == null) System.setProperty("java.awt.headless", "true");
    int height = imageHeightForMembers(members.size()) + (hasTaskTool ? MEMBER_GAP : 0);
    try {
      BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, height);
        g.setStroke(new BasicStroke(2f));
        g.setFont(FONT);
        int cx = WIDTH / 2;
        int supervisorY = TOP + 115;
        int startY = TOP + NODE_HEIGHT / 2;
        drawNode(g, cx, startY, "__start__", NodeKind.ROUND);
        arrow(g, cx, startY + NODE_HEIGHT / 2, cx, supervisorY - NODE_HEIGHT / 2);
        drawNode(g, cx, supervisorY, supervisorLabel, NodeKind.SUPERVISOR);
        int cursor = supervisorY + 100;
        int memberIndex = 0;
        for (int i = 0; i < members.size(); i++) {
          int y = cursor + i * MEMBER_GAP;
          int x = WIDTH / 5;
          arrow(g, cx - NODE_WIDTH / 2, supervisorY + NODE_HEIGHT / 2, x + NODE_WIDTH / 2, y - NODE_HEIGHT / 2);
          drawNode(g, x, y, memberLabels.get(i), NodeKind.MEMBER);
          int railX = 28;
          arrow(g, x - NODE_WIDTH / 2, y, railX, y);
          g.setColor(Color.DARK_GRAY);
          g.drawLine(railX, y, railX, supervisorY);
          arrow(g, railX, supervisorY, cx - NODE_WIDTH / 2, supervisorY);
          memberIndex++;
        }
        if (hasTaskTool) {
          int y = cursor + memberIndex * MEMBER_GAP;
          int x = WIDTH / 5;
          arrow(g, cx - NODE_WIDTH / 2, supervisorY + NODE_HEIGHT / 2, x + NODE_WIDTH / 2, y - NODE_HEIGHT / 2);
          drawNode(g, x, y, "tools", NodeKind.MEMBER);
          int railX = 28;
          arrow(g, x - NODE_WIDTH / 2, y, railX, y);
          g.setColor(Color.DARK_GRAY);
          g.drawLine(railX, y, railX, supervisorY);
          arrow(g, railX, supervisorY, cx - NODE_WIDTH / 2, supervisorY);
          memberIndex++;
        }
        int endY = cursor + memberIndex * MEMBER_GAP;
        arrow(g, cx, supervisorY + NODE_HEIGHT / 2, cx, endY - NODE_HEIGHT / 2);
        drawNode(g, cx, endY, "__end__", NodeKind.ROUND);
      } finally {
        g.dispose();
      }
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      if (!ImageIO.write(image, "png", bytes)) throw new IOException("No PNG writer is installed.");
      return Base64.getEncoder().encodeToString(bytes.toByteArray());
    } catch (HeadlessException | AWTError e) {
      throw new OBException("Unable to render the team graph image: Java2D is unavailable in this headless environment. "
          + "Ensure the JRE has AWT logical-font support (fontconfig may be required).", e);
    } catch (InternalError e) {
      throw new OBException("Unable to render the team graph image: Java2D could not load AWT logical fonts. "
          + "Install/configure fontconfig in the JRE environment.", e);
    } catch (NullPointerException e) {
      throw new OBException("Unable to render the team graph image: Java2D could not initialize AWT logical fonts. "
          + "Verify that fontconfig is installed and configured in the JRE environment.", e);
    } catch (LinkageError e) {
      throw new OBException("Unable to render the team graph image: Java2D could not load AWT logical fonts. "
          + "Verify that fontconfig is installed and configured in the JRE environment.", e);
    } catch (IOException | RuntimeException e) {
      if (e instanceof OBException) throw (OBException) e;
      throw new OBException("Unable to encode the team graph image as PNG.", e);
    }
  }

  /** Height of the deterministic diagram for the given team size; useful for bounds checks. */
  public static int imageHeightForMembers(int memberCount) {
    if (memberCount < 1 || memberCount > MAX_MEMBERS) throw new IllegalArgumentException("Member count must be 1..30.");
    return TOP + 215 + memberCount * MEMBER_GAP + NODE_HEIGHT / 2 + BOTTOM;
  }

  private static void drawNode(Graphics2D g, int cx, int cy, String label, NodeKind kind) {
    g.setFont(kind == NodeKind.SUPERVISOR ? BOLD_FONT : FONT);
    FontMetrics metrics = g.getFontMetrics();
    List<String> lines = wrap(label, metrics, NODE_WIDTH - 24);
    int x = cx - NODE_WIDTH / 2;
    int y = cy - NODE_HEIGHT / 2;
    g.setColor(kind == NodeKind.SUPERVISOR ? new Color(255, 226, 153) : Color.WHITE);
    if (kind == NodeKind.ROUND) g.fillRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 28, 28);
    else g.fillRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 12, 12);
    g.setColor(kind == NodeKind.SUPERVISOR ? new Color(167, 105, 0) : new Color(65, 75, 90));
    g.setStroke(new BasicStroke(kind == NodeKind.SUPERVISOR ? 3f : 2f));
    if (kind == NodeKind.ROUND) g.drawRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 28, 28);
    else g.drawRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 12, 12);
    g.setColor(Color.BLACK);
    int total = lines.size() * metrics.getHeight();
    int baseline = cy - total / 2 + metrics.getAscent();
    for (String line : lines) {
      g.drawString(line, cx - metrics.stringWidth(line) / 2, baseline);
      baseline += metrics.getHeight();
    }
  }

  private static List<String> wrap(String value, FontMetrics metrics, int maxWidth) {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    int lineWidth = 0;
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      int characterWidth = metrics.charWidth(character);
      if (line.length() > 0 && lineWidth + characterWidth > maxWidth) {
        lines.add(line.toString());
        line.setLength(0);
        lineWidth = 0;
      }
      line.append(character);
      lineWidth += characterWidth;
    }
    if (line.length() > 0) lines.add(line.toString());
    return lines.isEmpty() ? List.of("") : lines;
  }

  private static void arrow(Graphics2D g, int x1, int y1, int x2, int y2) {
    g.setColor(new Color(70, 78, 90));
    g.drawLine(x1, y1, x2, y2);
    double angle = Math.atan2(y2 - y1, x2 - x1);
    int size = 9;
    Path2D arrow = new Path2D.Double();
    arrow.moveTo(x2, y2);
    arrow.lineTo(x2 - size * Math.cos(angle - Math.PI / 6), y2 - size * Math.sin(angle - Math.PI / 6));
    arrow.lineTo(x2 - size * Math.cos(angle + Math.PI / 6), y2 - size * Math.sin(angle + Math.PI / 6));
    arrow.closePath();
    g.fill(arrow);
  }

  private static String codify(String value) {
    String result = StringUtils.defaultIfBlank(value, "Member").replaceAll("\\s+", "").replaceAll("[^A-Za-z0-9_-]", "");
    if (result.length() > 63) result = result.substring(0, 63);
    if (!VALID_NODE.matcher(result).matches()) throw new OBException("A team graph node has no valid name.");
    return result;
  }

  private static String quote(String value) {
    return "`" + value.replace("`", "") + "`";
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private enum NodeKind { ROUND, MEMBER, SUPERVISOR }
}
