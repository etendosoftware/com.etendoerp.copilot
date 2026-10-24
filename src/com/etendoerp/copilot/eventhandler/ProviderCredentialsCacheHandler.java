package com.etendoerp.copilot.eventhandler;

import javax.enterprise.event.Observes;

import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

import com.etendoerp.copilot.config.ProviderCredentials;
import com.etendoerp.copilot.data.CopilotApiKey;
import com.etendoerp.copilot.data.CopilotModel;

/** Invalidates resolved credentials after provider keys or model links change. */
public class ProviderCredentialsCacheHandler extends EntityPersistenceEventObserver {
  private static final Entity[] ENTITIES = {
      ModelProvider.getInstance().getEntity(CopilotApiKey.class),
      ModelProvider.getInstance().getEntity(CopilotModel.class)
  };

  @Override
  protected Entity[] getObservedEntities() {
    return ENTITIES;
  }

  public void onNew(@Observes EntityNewEvent event) {
    invalidate(event);
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    invalidate(event);
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    invalidate(event);
  }

  private void invalidate(org.openbravo.client.kernel.event.EntityPersistenceEvent event) {
    if (isValidEvent(event)) {
      ProviderCredentials.invalidateCache();
    }
  }
}
