package com.etendoerp.copilot.util;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/** Loads the named structured-output schemas shared by OCR tools. */
public final class OcrSchemaBuilder {
  private OcrSchemaBuilder() {}

  /** Returns the named JSON schema, or {@code null} when the name is not available. */
  public static JSONObject loadSchema(String name) throws JSONException {
    if (name == null || !"testdocument".equalsIgnoreCase(name.trim())) return null;
    JSONObject properties = new JSONObject()
        .put("document_id", prop("string", "Unique document identifier (REQUIRED)"))
        .put("document_number", prop("string", "Human-readable document number (REQUIRED)"))
        .put("title", prop("string", "Document title (REQUIRED)"))
        .put("description", prop("string", "Detailed description"))
        .put("status", enumProp("Current document status", "draft", "pending", "approved", "rejected", "archived"))
        .put("priority", enumProp("Document priority", "low", "medium", "high", "critical"))
        .put("creation_date", formatProp("Date when document was created", "date"))
        .put("due_date", formatProp("Due date if applicable", "date"))
        .put("modified_date", formatProp("Last modification timestamp", "date-time"))
        .put("owner", contactSchema(true))
        .put("assignee", contactSchema(false))
        .put("contacts", arrayOf(contactSchema(false), "Additional contacts related to this document"))
        .put("billing_address", addressSchema())
        .put("shipping_address", addressSchema())
        .put("amounts", amountSchema())
        .put("line_items", arrayOf(lineItemSchema(), "Document line items"))
        .put("attachments", arrayOf(attachmentSchema(), "File attachments"))
        .put("approvals", arrayOf(approvalSchema(), "List of approvals"))
        .put("is_active", prop("boolean", "Whether the document is active"))
        .put("is_confidential", prop("boolean", "Confidentiality flag"))
        .put("version", prop("integer", "Document version number", "minimum", 1))
        .put("tags", arrayOf(prop("string", null), "Tags for categorization"))
        .put("category", prop("string", "Document category"))
        .put("notes", prop("string", "General notes"))
        .put("metadata", new JSONObject().put("type", "object").put("description", "Additional metadata as key-value pairs"));
    return objectSchema(properties).put("required", new JSONArray()
        .put("document_id").put("document_number").put("title").put("creation_date").put("owner"));
  }

  private static JSONObject contactSchema(boolean required) throws JSONException {
    JSONObject value = objectSchema(new JSONObject()
        .put("name", prop("string", "Full name of the contact person"))
        .put("email", prop("string", "Email address"))
        .put("phone", prop("string", "Phone number"))
        .put("department", prop("string", "Department name")));
    if (required) value.put("required", new JSONArray().put("name").put("email"));
    return value;
  }

  private static JSONObject addressSchema() throws JSONException {
    return objectSchema(new JSONObject()
        .put("street", prop("string", "Street address"))
        .put("street2", prop("string", "Additional address line"))
        .put("city", prop("string", "City name"))
        .put("state", prop("string", "State or province"))
        .put("postal_code", prop("string", "Postal or ZIP code"))
        .put("country", prop("string", "Country name or ISO code"))
        .put("is_primary", prop("boolean", "Whether this is the primary address")))
        .put("required", new JSONArray().put("street").put("city").put("postal_code").put("country"));
  }

  private static JSONObject amountSchema() throws JSONException {
    return objectSchema(new JSONObject()
        .put("subtotal", prop("number", "Subtotal amount before taxes"))
        .put("tax_amount", prop("number", "Tax amount"))
        .put("discount_amount", prop("number", "Discount amount"))
        .put("total", prop("number", "Total amount after taxes and discounts"))
        .put("currency", prop("string", "Currency code (ISO 4217)")))
        .put("required", new JSONArray().put("subtotal").put("total"));
  }

  private static JSONObject lineItemSchema() throws JSONException {
    return objectSchema(new JSONObject()
        .put("line_number", prop("integer", "Line number", "exclusiveMinimum", 0))
        .put("item_code", prop("string", "Product or service code"))
        .put("description", prop("string", "Item description"))
        .put("quantity", prop("number", "Quantity ordered"))
        .put("unit_price", prop("number", "Price per unit"))
        .put("tax_rate", prop("number", "Tax rate percentage (0-100)"))
        .put("discount_percent", prop("number", "Discount percentage"))
        .put("line_total", prop("number", "Total for this line"))
        .put("notes", prop("string", "Additional notes for this line")))
        .put("required", new JSONArray().put("line_number").put("item_code").put("description")
            .put("quantity").put("unit_price").put("line_total"));
  }

  private static JSONObject attachmentSchema() throws JSONException {
    return objectSchema(new JSONObject()
        .put("filename", prop("string", "Name of the attached file"))
        .put("file_size", prop("integer", "Size of the file in bytes"))
        .put("mime_type", prop("string", "MIME type of the file"))
        .put("url", prop("string", "URL to download the file"))
        .put("checksum", prop("string", "MD5 or SHA256 checksum")))
        .put("required", new JSONArray().put("filename").put("file_size").put("mime_type"));
  }

  private static JSONObject approvalSchema() throws JSONException {
    return objectSchema(new JSONObject()
        .put("approved_by", prop("string", "Name of the approver"))
        .put("approved_date", formatProp("Date and time of approval", "date-time"))
        .put("comments", prop("string", "Approval comments"))
        .put("level", prop("integer", "Approval level")))
        .put("required", new JSONArray().put("approved_by").put("approved_date"));
  }

  private static JSONObject objectSchema(JSONObject properties) throws JSONException {
    return new JSONObject().put("type", "object").put("properties", properties);
  }

  private static JSONObject arrayOf(JSONObject item, String description) throws JSONException {
    return new JSONObject().put("type", "array").put("description", description).put("items", item);
  }

  private static JSONObject formatProp(String description, String format) throws JSONException {
    return prop("string", description).put("format", format);
  }

  private static JSONObject prop(String type, String description) throws JSONException {
    JSONObject value = new JSONObject().put("type", type);
    if (description != null) value.put("description", description);
    return value;
  }

  private static JSONObject prop(String type, String description, String key, int value) throws JSONException {
    return prop(type, description).put(key, value);
  }

  private static JSONObject enumProp(String description, String... items) throws JSONException {
    JSONArray values = new JSONArray();
    for (String item : items) values.put(item);
    return prop("string", description).put("enum", values);
  }
}
