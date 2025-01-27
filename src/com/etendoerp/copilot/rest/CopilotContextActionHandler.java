package com.etendoerp.copilot.rest;

import java.math.BigDecimal;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.order.Order;

/**
 * This ActionHandler sums the grand total amounts of orders
 * specified in the incoming JSON payload and returns the result.
 */
public class CopilotContextActionHandler extends BaseActionHandler {

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String data) {
    try {
      final JSONObject jsonData = new JSONObject(data);
      final JSONArray orderIds = jsonData.optJSONArray("orders");

      if (orderIds == null) {
        throw new OBException("Missing 'orders' array in the request JSON.");
      }

      BigDecimal total = BigDecimal.ZERO;

      for (int i = 0; i < orderIds.length(); i++) {
        final String orderId = orderIds.getString(i);

        final Order order = OBDal.getInstance().get(Order.class, orderId);
        if (order == null) {
          continue;
        }

        total = total.add(order.getGrandTotalAmount());
      }

      final JSONObject resultJson = new JSONObject();
      resultJson.put("total", total);
      return resultJson;

    } catch (Exception e) {
      throw new OBException(e);
    }
  }
}